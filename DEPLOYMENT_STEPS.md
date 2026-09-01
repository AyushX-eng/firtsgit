# FirstGit Deployment Guide - Next Steps

## ✅ Completed Steps
- ✅ Code review and security fixes
- ✅ Build verification (JAR ready)
- ✅ Git commit to local repository
- ✅ Code pushed to GitHub: https://github.com/AyushX-eng/firtsgit

---

## 🎯 STEP 1: Set Up GitHub OAuth Application

This is required for the GitHub login flow to work.

### Instructions:
1. Go to **GitHub Settings** → **Developer settings** → **OAuth Apps**
   - URL: https://github.com/settings/developers
2. Click **New OAuth App** (or **Register a new application**)
3. Fill in the form:
   ```
   Application name: FirstGit
   Homepage URL: https://firstgit-api.onrender.com
   Application description: Secure GitHub Deployment Tool
   Authorization callback URL: https://firstgit-api.onrender.com/oauth2/callback/github
   ```
4. Click **Register application**
5. You'll see:
   - **Client ID** (copy this)
   - **Client secret** (click to reveal, then copy - only shown once!)

**Save these values - you need them for Render!**

---

## 🎯 STEP 2: Generate JWT Secret

Run this command in your terminal to generate a secure JWT secret:

**On macOS/Linux/Windows with OpenSSL:**
```bash
openssl rand -base64 64
```

**On Windows PowerShell (if OpenSSL not installed):**
```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

**Save the output - you need it for Render!**

---

## 🎯 STEP 3: Deploy Backend to Render

### 3a. Create Render Account
1. Go to https://render.com
2. Sign up with GitHub (recommended - easier auth)
3. Authorize FirstGit app access

### 3b. Create Web Service
1. In Render Dashboard, click **New +** → **Web Service**
2. Click **Deploy an existing repository**
3. Find and select `firtsgit` repository
4. Click **Connect**

### 3c. Configure Service
Fill in these settings:

| Field | Value |
|-------|-------|
| Name | `firstgit-api` |
| Environment | `Docker` |
| Branch | `main` |
| Build Command | `mvn clean package -q` |
| Start Command | (Leave empty - Docker handles it) |

### 3d. Add Environment Variables

Before deploying, add these environment variables:

**Navigate to:** Environment tab

Add each variable:
```
GITHUB_CLIENT_ID = <your GitHub OAuth Client ID>
GITHUB_CLIENT_SECRET = <your GitHub OAuth Client Secret>
JWT_SECRET = <your generated 64-byte secret>
FRONTEND_URL = https://firstgit-ui.netlify.app
SECURE_COOKIES = true
PORT = 8080
```

### 3e. Deploy
1. Click **Create Web Service**
2. Render will:
   - Pull code from GitHub
   - Build Docker image
   - Run application
3. Wait for status to show **Live** (usually 3-5 minutes)

### Verify Deployment:
- Watch the **Logs** tab for successful startup
- URL will be: `https://firstgit-api.onrender.com`
- Test health: `curl https://firstgit-api.onrender.com/api/auth/status`

---

## 🎯 STEP 4: Deploy Frontend to Netlify

### 4a. Create Netlify Account
1. Go to https://netlify.com
2. Sign up with GitHub (recommended)

### 4b. Deploy Frontend
1. Click **Add new site** → **Import an existing project**
2. Select **GitHub** and authorize
3. Find and select `firtsgit` repository
4. Configure build:

| Field | Value |
|-------|-------|
| Branch to deploy | `main` |
| Build command | `npm run build` |
| Publish directory | `dist` |
| Base directory | `firstgit-ui` |

### 4c: Add Environment Variable
Before deploying, add:
```
VITE_API_URL = https://firstgit-api.onrender.com
```

### 4d: Deploy
1. Click **Deploy site**
2. Wait for build to complete (2-3 minutes)
3. You'll get a URL like: `https://firtsgit.netlify.app`

---

## 🎯 STEP 5: Update GitHub OAuth Callback URL

Now that you know your actual Render URL, update the OAuth app:

1. Go to GitHub OAuth app settings: https://github.com/settings/developers
2. Click on your `FirstGit` app
3. Update **Authorization callback URL** to your actual Render URL:
   ```
   https://firstgit-api.onrender.com/oauth2/callback/github
   ```
4. Click **Update application**

---

## 🧪 STEP 6: Test Everything

### Test Backend Health
```bash
curl -I https://firstgit-api.onrender.com/api/auth/status
```

Expected response: `200 OK` with security headers

### Test OAuth Flow
1. Open https://firstgit-ui.netlify.app
2. Click **Login with GitHub**
3. Authorize the application
4. Verify you see:
   - Your GitHub username
   - Your GitHub avatar
   - "Logged in" status

### Test Deployment Feature
1. Create a test ZIP file (with some files)
2. Upload via FirstGit UI
3. Verify:
   - Repository created on your GitHub
   - Files pushed correctly
   - No build artifacts (node_modules, dist, etc.)

### Test Rate Limiting
1. Make 6 rapid deploy requests (within 1 minute)
2. 6th request should return `429 Too Many Requests`

### Verify Security
Open DevTools (F12) → Application → Cookies:
- `firstgit_jwt` should have:
  - ✅ HttpOnly: Yes
  - ✅ Secure: Yes
  - ✅ SameSite: Strict
  - ✅ Expiration: 15 minutes

---

## 📋 Troubleshooting

### OAuth Redirect Fails
**Problem**: "Callback URL mismatch"
**Solution**: 
- Verify your Render URL in GitHub OAuth app settings
- Make sure it matches exactly (including https://)

### Deployment Fails with "Maven not found"
**Problem**: Build command fails
**Solution**:
- Verify `Dockerfile` is present in root
- Render should auto-detect pom.xml

### JWT Token Issues
**Problem**: "Invalid JWT" or "Token expired"
**Solution**:
- Regenerate `JWT_SECRET` on Render
- Clear browser cookies
- Log out and back in

### CORS Errors
**Problem**: Frontend can't call backend API
**Solution**:
- Verify `FRONTEND_URL` is correct on Render
- Check SecurityConfig CORS whitelist in code
- Verify both are using HTTPS

### Rate Limiting Too Strict
**Problem**: Deployment requests blocked
**Solution**:
- Current limits: 5 requests/minute for deploy endpoint
- Edit `RateLimitFilter.java` if you need to increase
- Redeploy to apply changes

---

## ✅ Deployment Checklist

- [ ] GitHub OAuth app created with correct callback URL
- [ ] JWT secret generated (64 bytes)
- [ ] Render account created
- [ ] Backend deployed to Render with all environment variables
- [ ] Backend shows "Live" status
- [ ] Netlify account created
- [ ] Frontend deployed to Netlify with VITE_API_URL set
- [ ] Frontend build successful
- [ ] OAuth login flow tested and working
- [ ] Deployment feature tested (upload ZIP, verify on GitHub)
- [ ] Security headers verified
- [ ] Rate limiting tested
- [ ] All cookies are HttpOnly + Secure + SameSite

---

## 🎉 What's Next?

Once everything is deployed and tested:

1. **Share your app**: Your FirstGit app is live!
   - Frontend: `https://firstgit-ui.netlify.app` (or your custom domain)
   - Backend API: `https://firstgit-api.onrender.com`

2. **Monitor**: Keep an eye on:
   - Render logs for errors
   - GitHub for new deployments
   - User deployments

3. **Scale**: If you need to scale:
   - Upgrade Render plan (currently on free tier)
   - Add database for token storage (optional)
   - Add monitoring/alerting

---

## 📞 Support

If you run into issues:
1. Check the logs in Render Dashboard
2. Verify all environment variables are set
3. Clear browser cache/cookies
4. Try incognito/private window
5. Check GitHub OAuth app settings

**Estimated time to fully deploy**: 15-20 minutes

**Good luck! 🚀**
