package com.firstgit.api.service;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for processing ZIP file deployments to GitHub.
 * 
 * Uses the authenticated user's OAuth2 token (stored in-memory via OAuth2TokenStore)
 * to create repos and push code via the GitHub API + git CLI.
 */
@Service
public class ZipProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ZipProcessingService.class);

    private static final Set<String> REMOVE_DIRS = Set.of(
        "node_modules", "target", "build", "dist", ".gradle", ".idea",
        ".vscode", ".cache", "venv", ".venv", "__pycache__", ".pytest_cache",
        ".parcel-cache", "out", "coverage", ".next", ".nuxt"
    );

    private static final Set<String> REMOVE_EXT = Set.of(
        ".class", ".jar", ".war", ".exe", ".dll", ".pdb", ".iso", ".mp4",
        ".mov", ".psd", ".log", ".tmp", ".zip", ".tar", ".gz", ".7z",
        ".o", ".a", ".lib", ".obj", ".pyc"
    );

    private static final long ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES =
            getEnvLong("ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES", 300L * 1024 * 1024);
    private static final long ZIP_MAX_ENTRY_UNCOMPRESSED_BYTES =
            getEnvLong("ZIP_MAX_ENTRY_UNCOMPRESSED_BYTES", 50L * 1024 * 1024);
    private static final int ZIP_MAX_ENTRIES = (int) getEnvLong("ZIP_MAX_ENTRIES", 10_000);

    /**
     * Main deployment method.
     * Accepts `isPrivate` parameter and respects it for repo creation.
     * 
     * @param file       The uploaded ZIP file
     * @param repoName   The desired GitHub repository name
     * @param isPrivate  Whether the repository should be private
     * @param githubToken The OAuth2 access token for the authenticated user
     * @return The URL of the created/pushed repository
     * @throws IOException If any I/O or GitHub API error occurs
     */
    public String processAndDeploy(MultipartFile file, String repoName, boolean isPrivate, String githubToken) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
        String owner = github.getMyself().getLogin();

        // 1. Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("firstgit-deploy-");
        File tempZip = tempDir.resolve(
            file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.zip"
        ).toFile();
        file.transferTo(tempZip);

        try {
            // 2. Extract ZIP contents
            extractZip(tempZip.toPath(), tempDir);

            // 3. Clean extracted files (remove node_modules, build artifacts, etc.)
            List<String> preservePatterns = loadPreserveManifest(tempDir);
            cleanExtracted(tempDir, preservePatterns);

            // 4. Find or create the GitHub repository
            GHRepository repository = findOrCreateRepo(github, owner, repoName, isPrivate);

            // 5. Push via git CLI
            try {
                pushWithGitCLI(tempDir, github, githubToken, owner, repoName);
            } catch (Exception e) {
                log.warn("git CLI push failed, falling back to API upload: {}", e.getMessage());
                fallbackApiUpload(tempZip.toPath(), tempDir, repository);
            }

            return repository.getHtmlUrl().toString();
        } finally {
            // 6. Cleanup temp directory
            try {
                deleteRecursively(tempDir);
            } catch (Exception e) {
                log.warn("Failed to cleanup temp directory: {}", e.getMessage());
            }
        }
    }

    /**
     * Fetch the user's existing GitHub repositories.
     */
    public List<String> fetchUserRepositories(String githubToken) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
        return github.getMyself().listRepositories().toList().stream()
                .map(GHRepository::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private void extractZip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            java.util.zip.ZipEntry entry;
            long totalUncompressed = 0L;
            int entries = 0;
            while ((entry = zis.getNextEntry()) != null) {
                String rawName = entry.getName();
                if (rawName == null) continue;

                String entryName = rawName.replace('\\', '/');
                while (entryName.startsWith("/")) {
                    entryName = entryName.substring(1);
                }

                if (entry.isDirectory() || entryName.endsWith("/")) continue;
                if (++entries > ZIP_MAX_ENTRIES) {
                    throw new IOException("ZIP contains too many entries");
                }
                if (entryName.length() > 500) {
                    throw new IOException("ZIP entry name too long");
                }

                Path outPath = targetDir.resolve(entryName).normalize();
                // ZIP-slip protection
                if (!outPath.startsWith(targetDir)) {
                    log.warn("Skipping ZIP-slip entry: {}", entryName);
                    continue;
                }

                try {
                    Files.createDirectories(outPath.getParent());
                    long entryUncompressed = 0L;
                    try (OutputStream out = new BufferedOutputStream(
                            Files.newOutputStream(outPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zis.read(buffer)) > 0) {
                            entryUncompressed += read;
                            totalUncompressed += read;
                            if (entryUncompressed > ZIP_MAX_ENTRY_UNCOMPRESSED_BYTES
                                    || totalUncompressed > ZIP_MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw new IOException("ZIP uncompressed size exceeds limits");
                            }
                            out.write(buffer, 0, read);
                        }
                    } catch (IOException e) {
                        try { Files.deleteIfExists(outPath); } catch (Exception ignored) {}
                        throw e;
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract entry: {} - {}", entryName, e.getMessage());
                }
            }
        }
    }

    private List<String> loadPreserveManifest(Path tempDir) {
        Path manifest = tempDir.resolve(".firstgit-preserve");
        if (!Files.exists(manifest)) return List.of();

        try {
            List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8);
            return lines.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .limit(200)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private void cleanExtracted(Path dir, List<String> preservePatterns) {
        if (!Files.exists(dir)) return;

        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    String rel = dir.relativize(p).toString().replace('\\', '/');
                    if (rel.isEmpty()) return;
                    if (matchesAny(rel, preservePatterns)) return;

                    if (Files.isDirectory(p)) {
                        String name = p.getFileName().toString();
                        if (REMOVE_DIRS.contains(name) || ".git".equals(name)) {
                            deleteRecursively(p);
                        }
                    } else if (Files.isRegularFile(p)) {
                        String name = p.getFileName().toString();
                        if (".DS_Store".equals(name) || name.endsWith(".iml") || name.endsWith("~")) {
                            Files.deleteIfExists(p);
                            return;
                        }
                        String lower = name.toLowerCase();
                        for (String ext : REMOVE_EXT) {
                            if (lower.endsWith(ext)) {
                                Files.deleteIfExists(p);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            });
        } catch (Exception ignored) {
            // Directory may not exist
        }
    }

    private boolean matchesAny(String rel, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        for (String pat : patterns) {
            if (pat.isBlank()) continue;
            if (rel.equals(pat)) return true;
            try {
                if (pat.contains("*") || pat.contains("?")) {
                    PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + pat);
                    if (m.matches(Paths.get(rel))) return true;
                } else if (pat.startsWith("*.") && rel.toLowerCase().endsWith(pat.substring(1).toLowerCase())) {
                    return true;
                } else if (pat.endsWith("/**")) {
                    String prefix = pat.substring(0, pat.length() - 3);
                    if (rel.startsWith(prefix)) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
        }
    }

    private GHRepository findOrCreateRepo(GitHub github, String owner, String repoName, boolean isPrivate) throws IOException {
        try {
            return github.getRepository(owner + "/" + repoName);
        } catch (GHFileNotFoundException e) {
            log.info("Creating new repository: {}/{} (private={})", owner, repoName, isPrivate);
            return github.createRepository(repoName)
                    .description("Deployed via FirstGit")
                    .private_(isPrivate)
                    .create();
        }
    }

    private void pushWithGitCLI(Path workDir, GitHub github, String githubToken, String owner, String repoName) throws Exception {
        File wd = workDir.toFile();
        String authorName = github.getMyself().getLogin();
        String authorEmail = github.getMyself().getEmail();
        if (authorEmail == null || authorEmail.isBlank()) {
            authorEmail = authorName + "@users.noreply.github.com";
        }

        // Initialize git repo
        exec(wd, "git", "init");
        exec(wd, "git", "config", "user.name", authorName);
        exec(wd, "git", "config", "user.email", authorEmail);

        // Add and commit
        exec(wd, "git", "add", ".");
        exec(wd, "git", "commit", "-m", "Deploy via FirstGit", "--author", authorName + " <" + authorEmail + ">");

        // Try SSH key-based push first, fall back to HTTPS
        boolean sshSuccess = trySshPush(wd, githubToken, owner, repoName);
        if (!sshSuccess) {
            log.info("SSH push not available, using HTTPS fallback");
            httpsPush(wd, githubToken, owner, repoName);
        }
    }

    private boolean trySshPush(File wd, String githubToken, String owner, String repoName) {
        try {
            // Generate ephemeral SSH key
            Path keyDir = Files.createTempDirectory("firstgit-key-");
            Path privateKey = keyDir.resolve("id_ed25519");
            Path publicKey = keyDir.resolve("id_ed25519.pub");

            try {
                exec(wd, "ssh-keygen", "-t", "ed25519", "-f", privateKey.toString(), "-N", "", "-q");
            } catch (Exception e) {
                log.debug("ssh-keygen not available: {}", e.getMessage());
                deleteRecursively(keyDir);
                return false;
            }

            if (!Files.exists(privateKey) || !Files.exists(publicKey)) {
                deleteRecursively(keyDir);
                return false;
            }

            // Register deploy key via GitHub API
            String pub = Files.readString(publicKey);
            String sshRemote = "git@github.com:" + owner + "/" + repoName + ".git";

            HttpClient http = HttpClient.newHttpClient();
            String url = "https://api.github.com/repos/" + owner + "/" + repoName + "/keys";
            String title = "firstgit-deploy-" + System.currentTimeMillis();
            String bodyJson = "{\"title\":\"" + title + "\",\"key\":\"" 
                + pub.replace("\n", "\\n").replace("\"", "\\\"") 
                + "\",\"read_only\":false}";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "token " + githubToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int keyId = -1;
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(resp.body());
                if (m.find()) keyId = Integer.parseInt(m.group(1));
            }

            // Add remote and push with SSH
            exec(wd, "git", "remote", "add", "origin", sshRemote);
            exec(wd, "git", "branch", "-M", "main");

            ProcessBuilder pbPush = new ProcessBuilder("git", "push", "-u", "origin", "main");
            pbPush.directory(wd);
            pbPush.environment().put("GIT_SSH_COMMAND",
                "ssh -i \"" + privateKey.toString() + "\" -o IdentitiesOnly=yes -o StrictHostKeyChecking=no");
            pbPush.redirectErrorStream(true);

            Process ppush = pbPush.start();
            String pushOutput = new String(ppush.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int rc = ppush.waitFor();

            // Cleanup deploy key
            if (keyId != -1) {
                String delUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/keys/" + keyId;
                HttpRequest delReq = HttpRequest.newBuilder()
                        .uri(URI.create(delUrl))
                        .header("Authorization", "token " + githubToken)
                        .header("Accept", "application/vnd.github+json")
                        .DELETE().build();
                try { http.send(delReq, HttpResponse.BodyHandlers.discarding()); } catch (Exception ignored) {}
            }

            // Cleanup key files
            deleteRecursively(keyDir);

            if (rc == 0) return true;

            log.warn("SSH push failed (rc={}): {}", rc, pushOutput);
            return false;
        } catch (Exception e) {
            log.warn("SSH push failed: {}", e.getMessage());
            return false;
        }
    }

    private void httpsPush(File wd, String githubToken, String owner, String repoName) throws Exception {
        String remoteUrl = "https://x-access-token:" + githubToken + "@github.com/" + owner + "/" + repoName + ".git";
        exec(wd, "git", "remote", "add", "origin", remoteUrl);
        exec(wd, "git", "branch", "-M", "main");
        exec(wd, "git", "push", "-u", "origin", "main");
    }

    private void fallbackApiUpload(Path zipPath, Path tempDir, GHRepository repository) {
        try {
            // Upload individual files via GitHub Content API
            try (var stream = Files.walk(tempDir)) {
                stream.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        String relPath = tempDir.relativize(p).toString().replace('\\', '/');
                        byte[] bytes = Files.readAllBytes(p);

                        // Detect binary files
                        boolean binary = false;
                        int scan = Math.min(bytes.length, 1024);
                        for (int i = 0; i < scan; i++) {
                            if (bytes[i] == 0) { binary = true; break; }
                        }

                        if (!binary) {
                            String content = new String(bytes, StandardCharsets.UTF_8);
                            try {
                                repository.createContent()
                                    .path(relPath)
                                    .content(content)
                                    .message("Add " + relPath)
                                    .commit();
                            } catch (Exception ex) {
                                try {
                                    org.kohsuke.github.GHContent existing = repository.getFileContent(relPath);
                                    existing.update(content, "Update " + relPath);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}
                });
            }

            // Also upload the ZIP as a release asset
            try {
                String tag = "deployed-" + System.currentTimeMillis();
                var release = repository.createRelease(tag)
                        .name("Deployment " + tag)
                        .body("Deployed via FirstGit")
                        .create();
                release.uploadAsset(zipPath.toFile(), "application/zip");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.warn("API upload fallback failed: {}", e.getMessage());
        }
    }

    private void exec(File workDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int rc = p.waitFor();
        if (rc != 0) {
            throw new RuntimeException("Command failed: " + sanitizeCommand(command) + "\n" + output);
        }
    }

    private String sanitizeCommand(String... command) {
        if (command == null) return "";
        return Arrays.stream(command).map(this::sanitizeArg).collect(Collectors.joining(" "));
    }

    private String sanitizeArg(String arg) {
        if (arg == null) return "null";
        int idx = arg.indexOf("https://x-access-token:");
        if (idx >= 0) {
            int at = arg.indexOf("@", idx);
            if (at > 0) {
                return "https://x-access-token:***" + arg.substring(at);
            }
            return "https://x-access-token:***";
        }
        return arg;
    }

    private static long getEnvLong(String key, long defaultValue) {
        String raw = System.getenv(key);
        if (raw == null || raw.isBlank()) return defaultValue;
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
