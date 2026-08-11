# 🔐 Security-First Implementation Roadmap

## Phase 1: Backend Security Foundation
- [x] 1. Update `pom.xml` — Add bucket4j, jjwt, jakarta.validation dependencies ✅
- [x] 2. Update `application.properties` — Remove hardcoded secrets → env vars, add JWT config ✅
- [x] 3. Create `JwtService.java` — JWT generation/validation (15min expiry, HS256 signing) ✅
- [x] 4. Create `JwtCookieUtil.java` — HttpOnly, Secure (conditional), SameSite cookie creation ✅
- [x] 5. Create `OAuth2TokenStore.java` — In-memory ConcurrentHashMap for OAuth2 tokens ✅
- [x] 6. Create `JwtAuthenticationFilter.java` — Extracts JWT from cookie, validates, sets SecurityContext ✅
- [x] 7. Create `RateLimitFilter.java` — Bucket4j rate limiter (per-user/IP) ✅
- [x] 8. Create `GlobalExceptionHandler.java` — Opaque error responses, no stack traces ✅
- [x] 9. Rewrite `SecurityConfig.java` — CSRF enabled, strict CORS, JWT filter chain, HSTS, CSP headers ✅
- [x] 10. Create `CookieConfig.java` — Configures Secure flag based on SECURE_COOKIES env var ✅

## Phase 2: Backend Controllers & Services
- [x] 11. Create `OAuth2AuthenticationSuccessHandler.java` — Auth code exchange pattern ✅
- [x] 12. Rewrite `AuthController.java` — Clean endpoints, JWT cookie auth status ✅
- [x] 13. Create DTOs with jakarta.validation (`DeployRequest.java`, `AuthResponse.java`) ✅
- [x] 14. Rewrite `FileUploadController.java` → Merged into `DeploymentController.java` ✅
- [x] 15. Delete `DeploymentController.java` old — Functionality merged ✅
- [x] 16. Fix `ZipProcessingService.java` — Fix isPrivate compliance, enable cleanup, node_modules handling ✅

## Phase 3: Frontend Security
- [x] 17. Create `firstgit-ui/src/api/client.js` — Secure fetch wrapper with credentials: 'include' ✅
- [x] 18. Create `firstgit-ui/src/hooks/useAuth.js` — Auth state management ✅
- [x] 19. Rewrite `App.jsx` — OAuth-only login, no personal token input, component splitting ✅
- [x] 20. Update `vite.config.js` — Dev proxy config (OAuth + API routes) ✅

## 🐛 Bug Fix: "Access Denied. Session May Have Expired."

### Root Cause
The OAuth login URL was going through the **Vite proxy** (`localhost:5173/oauth2/...`), which set `JSESSIONID` cookie for port 5173. GitHub's OAuth callback hits `localhost:8080`, where the cookie didn't exist → **state parameter mismatch**.

### Fix Applied
**`client.js`**: OAuth login URL now goes **directly** to `http://localhost:8080/oauth2/authorization/github` so `JSESSIONID` is set for port 8080.

### Bug Fix: Cookies silently dropped on localhost HTTP
**`JwtCookieUtil.java`**: `secure=true` was **hardcoded**. On HTTP localhost, browsers **silently drop** Secure cookies. Fixed to use `SECURE_COOKIES` env var (default `false` for localhost).

**`application.properties`**: `server.servlet.session.cookie.secure=${SECURE_COOKIES:false}` — JSESSIONID also had the same issue.

**`CookieConfig.java`**: New class that calls `JwtCookieUtil.setSecureMode()` at startup.

## Phase 4: Testing & Verification
- [x] 21. Build + started backend: `mvn spring-boot:run` ✅
- [ ] 22. Start frontend: `npm run dev` (in separate terminal)
- [ ] 23. Open http://localhost:5173 → Click "Sign in with GitHub"
- [ ] 24. Verify full OAuth flow end-to-end
- [ ] 25. Verify deploy with private repo setting

## Production Deployment (Render)
Set these environment variables:
```
JWT_SECRET=<generate: openssl rand -base64 64>
SECURE_COOKIES=true
GITHUB_CLIENT_ID=<your-app-client-id>
GITHUB_CLIENT_SECRET=<your-app-client-secret>
PORT=8080
```

For the GitHub OAuth app, set the callback URL to:
```
https://<your-render-app>.onrender.com/login/oauth2/code/github
