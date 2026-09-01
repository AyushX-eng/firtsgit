# GitHub Setup Guide for FirstGit Deployment

## Step 1: Create GitHub Repository

1. Go to **https://github.com/new** (or click "+" → "New repository" in GitHub)
2. Fill in repository details:
   - **Repository name**: `firstgit` (or your preferred name)
   - **Description**: "Secure GitHub Deployment Tool"
   - **Visibility**: Public (recommended) or Private (your choice)
   - **Initialize repository**: Leave unchecked (we already have files)
3. Click **Create repository**
4. You'll see the quick setup page

## Step 2: Get Your Repository URL

After creating the repository, GitHub shows you two options:

### Option A: HTTPS (Recommended for beginners)
```
https://github.com/YOUR_USERNAME/firstgit.git
```

### Option B: SSH (Requires SSH key setup)
```
git@github.com:YOUR_USERNAME/firstgit.git
```

## Step 3: Add Remote and Push to GitHub

Once you have your repository URL, run these commands:

```bash
# Add remote (replace with your actual URL)
git remote add origin https://github.com/YOUR_USERNAME/firstgit.git

# Verify remote was added
git remote -v

# Push the code to GitHub
git branch -M main
git push -u origin main
```

## Step 4: Configure Render Auto-Deploy

After pushing to GitHub:

1. Go to **Render Dashboard** (https://dashboard.render.com)
2. Click **New +** → **Web Service**
3. Select **Deploy an existing repository from GitHub**
4. Choose your `firstgit` repository
5. Configure:
   - **Name**: `firstgit-api`
   - **Branch**: `main`
   - **Build Command**: `mvn clean package -q`
   - **Start Command**: `java -jar target/api-0.0.1-SNAPSHOT.jar`
   - **Environment**: Add all required variables (see below)

## Required Render Environment Variables

Add these to Render dashboard → Service → Environment:

```env
GITHUB_CLIENT_ID=<your-github-app-client-id>
GITHUB_CLIENT_SECRET=<your-github-app-secret>
JWT_SECRET=<generated-secret>
FRONTEND_URL=https://firstgit-ui.netlify.app
SECURE_COOKIES=true
PORT=8080
```

### Generate JWT_SECRET

Run this command in terminal:
```bash
openssl rand -base64 64
```

On Windows (if OpenSSL not installed):
```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

## Step 5: Get GitHub OAuth Credentials

1. Go to **GitHub Settings** → **Developer settings** → **OAuth Apps**
2. Click **New OAuth App**
3. Fill in:
   - **Application name**: `FirstGit`
   - **Homepage URL**: `https://firstgit-api.onrender.com`
   - **Authorization callback URL**: `https://firstgit-api.onrender.com/oauth2/callback/github`
4. GitHub will generate:
   - **Client ID** → Copy to `GITHUB_CLIENT_ID`
   - **Client Secret** → Copy to `GITHUB_CLIENT_SECRET`

## Step 6: Deploy Frontend to Netlify

1. Fork or create a separate repository for `firstgit-ui`
2. Go to **https://netlify.com**
3. Click **New site from Git**
4. Select your `firstgit-ui` repository
5. Configure:
   - **Build Command**: `npm run build`
   - **Publish Directory**: `dist`
6. Add environment variable:
   - `VITE_API_URL=https://firstgit-api.onrender.com`
7. Click **Deploy**

## Step 7: Verify Deployment

After all is set up:

1. **Check Render logs**: https://dashboard.render.com → firstgit-api → Logs
2. **Test OAuth flow**: Visit `https://firstgit-ui.netlify.app` → "Login with GitHub"
3. **Verify deployment**: Upload a test ZIP file
4. **Check GitHub**: New repository should appear in your GitHub account

## Troubleshooting

### Push fails with "Permission denied"
- **HTTPS**: Verify GitHub username/password
- **SSH**: Set up SSH keys (https://github.com/settings/ssh/new)

### Render deployment fails
- Check environment variables are set correctly
- Verify `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` are correct
- Check logs: https://dashboard.render.com → Logs

### OAuth redirect fails
- Verify `Authorization callback URL` in GitHub OAuth app matches Render URL
- Verify `FRONTEND_URL` environment variable is correct on Render

---

**Next**: Once you have your GitHub repository URL, reply with it and I'll complete the git push!
