import { useState, useEffect, useCallback } from 'react';
import { authApi, deployApi } from '../api/client';

/**
 * Custom hook for authentication state management.
 *
 * Security principles:
 * - JWT token is NEVER stored in React state (it's in HttpOnly cookie)
 * - Auth state is determined by API call to /api/auth/status
 * - On auth failure/expiry, user is redirected to OAuth login
 * - No tokens are stored in localStorage or sessionStorage
 *
 * Auth code exchange flow:
 * 1. User clicks login → browser redirects to /oauth2/authorization/github
 * 2. GitHub OAuth succeeds → backend redirects to frontend?auth_code=<code>
 * 3. This hook detects ?auth_code= in URL → calls POST /api/auth/exchange
 * 4. Backend validates code, creates JWT, sets HttpOnly cookie
 * 5. Hook then calls /api/auth/status to get user data
 */
export function useAuth() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /**
   * Check authentication status by calling the backend.
   * The JWT cookie is sent automatically (credentials: 'include').
   */
  const checkAuth = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const data = await authApi.status();

      if (data && data.authenticated) {
        setUser({
          login: data.username,
          avatarUrl: data.avatarUrl,
          name: data.name,
        });
      } else {
        setUser(null);
      }
    } catch (err) {
      console.error('Auth check failed:', err.message);
      setUser(null);
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  /**
   * Initiate GitHub OAuth2 login.
   * Redirects the browser to the backend's OAuth2 endpoint.
   */
  const login = useCallback(() => {
    window.location.href = authApi.getLoginUrl();
  }, []);

  /**
   * Logout — calls the backend to clear the JWT cookie.
   */
  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (err) {
      console.error('Logout failed:', err.message);
    } finally {
      setUser(null);
    }
  }, []);

  /**
   * Fetch user's existing repositories from GitHub.
   */
  const fetchRepos = useCallback(async () => {
    if (!user) return [];
    try {
      return await deployApi.getRepos();
    } catch (err) {
      console.error('Failed to fetch repos:', err.message);
      return [];
    }
  }, [user]);

  /**
   * Exchange the OAuth auth code (from URL query param) for a JWT cookie.
   * This MUST happen through the Vite proxy (same-origin) so the cookie
   * is set for the frontend's origin, resolving SameSite cross-origin issues.
   */
  const exchangeAuthCode = useCallback(async (code) => {
    try {
      setLoading(true);
      await authApi.exchangeCode(code);
      await checkAuth();
    } catch (err) {
      console.error('Auth code exchange failed:', err.message);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, [checkAuth]);

  /**
   * On mount: handle auth code exchange and check auth status.
   * The auth_code parameter comes from the OAuth callback redirect.
   */
  useEffect(() => {
    // Check URL for auth_code from OAuth callback
    const params = new URLSearchParams(window.location.search);
    const authCode = params.get('auth_code');

    if (authCode) {
      // Clean the URL immediately (remove auth_code param for security)
      window.history.replaceState({}, document.title, window.location.pathname);
      // Exchange auth code for JWT cookie
      exchangeAuthCode(authCode);
    } else {
      // Normal auth check
      checkAuth();
    }

    // Listen for session expiry events from the API client
    const handleExpired = () => {
      setUser(null);
    };

    window.addEventListener('auth:expired', handleExpired);
    return () => window.removeEventListener('auth:expired', handleExpired);
  }, [checkAuth, exchangeAuthCode]);

  return {
    user,
    loading,
    error,
    isAuthenticated: !!user,
    login,
    logout,
    checkAuth,
    fetchRepos,
  };
}
