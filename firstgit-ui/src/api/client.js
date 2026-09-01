/**
 * Secure API client for FirstGit Frontend.
 *
 * Zero-trust security principles:
 * 1. JWT stored in HttpOnly cookie — NOT accessible from JavaScript
 * 2. Auth exchange happens through the Netlify / Vite proxy (same-origin)
 * 3. All requests include credentials (cookies) via credentials: 'include'
 * 4. Never stores tokens in localStorage or sessionStorage
 * 5. CSRF token read from XSRF-TOKEN cookie, sent as X-CSRF-TOKEN header
 * 6. OAuth2-only login — no personal token input
 *
 * Production:
 *   Login URL is relative — routes through Netlify proxy (/oauth2/* → Render backend).
 *   This guarantees the OAuth JSESSIONID cookie is set for the frontend's origin,
 *   so the state parameter survives the GitHub callback.
 */

const API_BASE_URL = '';

/**
 * Extract a cookie value by name from document.cookie.
 * Used to read the XSRF-TOKEN cookie for CSRF protection.
 */
function getCookie(name) {
  const value = `; ${document.cookie}`;
  const parts = value.split(`; ${name}=`);
  if (parts.length === 2) return parts.pop().split(';').shift();
  return null;
}

/**
 * Performs a secure fetch request with cookie credentials.
 * Automatically handles CSRF token exchange:
 * - Reads the XSRF-TOKEN cookie (set by Spring Security)
 * - Sends it as the X-CSRF-TOKEN header on mutating requests
 * All requests go through proxy in development and production (relative URLs).
 */
async function secureFetch(endpoint, options = {}) {
  const url = `${API_BASE_URL}${endpoint}`;

  const defaultHeaders = {
    'Accept': 'application/json',
  };

  const isFormData = options.body instanceof FormData;
  const method = (options.method || 'GET').toUpperCase();

  const csrfToken = getCookie('XSRF-TOKEN');
  if (csrfToken && ['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
    defaultHeaders['X-CSRF-TOKEN'] = csrfToken;
  }

  const config = {
    credentials: 'include',
    headers: {
      ...defaultHeaders,
      ...options.headers,
    },
    ...options,
  };

  if (isFormData) {
    delete config.headers['Content-Type'];
  }

  let response;
  try {
    response = await fetch(url, config);
  } catch (networkError) {
    throw new Error('Network error. Please check your connection.');
  }

  if (response.status === 403 && !csrfToken) {
    await fetch(`${API_BASE_URL}/api/auth/status`, { credentials: 'include' });
    const retryCsrfToken = getCookie('XSRF-TOKEN');
    if (retryCsrfToken) {
      config.headers['X-CSRF-TOKEN'] = retryCsrfToken;
      try {
        response = await fetch(url, config);
      } catch (retryError) {
        throw new Error('Network error. Please check your connection.');
      }
    }
  }

  if (response.status === 401) {
    window.dispatchEvent(new CustomEvent('auth:expired'));
    throw new Error('Session expired. Please log in again.');
  }

  if (response.status === 429) {
    throw new Error('Too many requests. Please wait a moment and try again.');
  }

  if (response.status === 403) {
    throw new Error('Access denied. Your session may have expired.');
  }

  let data;
  try {
    data = await response.json();
  } catch (parseError) {
    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`);
    }
    data = null;
  }

  if (!response.ok) {
    const errorMessage = data?.error || `Request failed with status ${response.status}`;
    throw new Error(errorMessage);
  }

  return data;
}

/**
 * Authentication API methods.
 */
export const authApi = {
  /** Check if the user is authenticated via JWT cookie */
  status: () => secureFetch('/api/auth/status'),

  /** Exchange OAuth auth code for a JWT cookie (same-origin via proxy) */
  exchangeCode: (code) => {
    return secureFetch(`/api/auth/exchange?code=${encodeURIComponent(code)}`, { method: 'POST' });
  },

  /** Logout — clears the JWT cookie */
  logout: () => secureFetch('/api/auth/logout', { method: 'POST' }),

  /**
   * Get GitHub OAuth2 login URL.
   * Always relative (same origin) so it goes through the Netlify / Vite
   * proxy to the backend, preserving origin for OAuth cookies.
   */
  getLoginUrl: () => `/oauth2/authorization/github`,
};

/**
 * Deployment API methods.
 */
export const deployApi = {
  /** Deploy a ZIP file as a GitHub repository */
  deploy: async (file, repoName, isPrivate) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('repoName', repoName);
    formData.append('isPrivate', String(isPrivate));

    return secureFetch('/api/v1/deploy', {
      method: 'POST',
      body: formData,
    });
  },

  /** Fetch user's existing GitHub repositories */
  getRepos: () => secureFetch('/api/v1/repos'),
};

export default secureFetch;
