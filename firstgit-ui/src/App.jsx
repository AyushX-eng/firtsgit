import React, { useState, useEffect } from 'react';
import { useAuth } from './hooks/useAuth';
import { deployApi } from './api/client';
import './App.css';

/**
 * FirstGit — Secure GitHub Deployment Tool
 *
 * Security features:
 * - Authentication via GitHub OAuth only (no personal tokens in UI)
 * - JWT stored in HttpOnly cookie (inaccessible to JavaScript)
 * - CSRF protection via cookie-to-header pattern
 * - All API calls use credentials: 'include' for cookie forwarding
 * - No tokens stored in localStorage or sessionStorage
 * - Input validation on repository name
 */

const LOADING_STEPS = [
  'Validating files...',
  'Preparing upload...',
  'Uploading to server...',
  'Creating repository...',
  'Pushing to GitHub...',
  'Finalizing...',
];

function App() {
  const { user, loading: authLoading, isAuthenticated, login, logout, checkAuth } = useAuth();
  const [selectedFile, setSelectedFile] = useState(null);
  const [repoName, setRepoName] = useState('');
  const [isPrivate, setIsPrivate] = useState(true);
  const [deploying, setDeploying] = useState(false);
  const [deployProgress, setDeployProgress] = useState('');
  const [deployResult, setDeployResult] = useState(null);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const [validationError, setValidationError] = useState('');

  // Check auth on mount and when URL has ?auth=success
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get('auth') === 'success') {
      // Clean URL after OAuth redirect
      window.history.replaceState({}, document.title, window.location.pathname);
      checkAuth();
    }
  }, [checkAuth]);

  const validateRepoName = (name) => {
    if (!name || !name.trim()) return 'Repository name is required.';
    if (name.length > 100) return 'Repository name must be 100 characters or less.';
    if (!/^[a-zA-Z0-9_.-]+$/.test(name)) {
      return 'Repository name can only contain letters, numbers, hyphens, underscores, and periods.';
    }
    return '';
  };

  const handleDeploy = async () => {
    setDeployResult(null);
    setShowConfirmation(false);
    setValidationError('');

    // Validate
    if (!selectedFile) {
      setDeployResult({ ok: false, message: 'Please select a ZIP file to upload.' });
      return;
    }

    if (!selectedFile.name.toLowerCase().endsWith('.zip')) {
      setDeployResult({ ok: false, message: 'Only ZIP files are supported.' });
      return;
    }

    const nameError = validateRepoName(repoName);
    if (nameError) {
      setValidationError(nameError);
      setDeployResult({ ok: false, message: nameError });
      return;
    }

    setDeploying(true);
    setDeployProgress(LOADING_STEPS[0]);

    try {
      // Simulate progressive loading steps
      let stepIndex = 0;
      const progressInterval = setInterval(() => {
        stepIndex++;
        if (stepIndex < LOADING_STEPS.length) {
          setDeployProgress(LOADING_STEPS[stepIndex]);
        } else {
          clearInterval(progressInterval);
        }
      }, 800);

      const data = await deployApi.deploy(selectedFile, repoName.trim(), isPrivate);
      clearInterval(progressInterval);

      setDeployProgress('');
      setDeployResult({
        ok: true,
        message: 'Repository created successfully!',
        repoUrl: data.repositoryUrl,
      });
      setShowConfirmation(true);
      setSelectedFile(null);
      setRepoName('');
    } catch (error) {
      setDeployProgress('');
      setDeployResult({
        ok: false,
        message: error.message || 'Deployment failed. Please try again.',
      });
    } finally {
      setDeploying(false);
    }
  };

  const handleFileSelect = (file) => {
    if (file) {
      setSelectedFile(file);
      setValidationError('');
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !deploying && isAuthenticated) {
      handleDeploy();
    }
  };

  // Loading state
  if (authLoading) {
    return (
      <div style={styles.loadingContainer}>
        <div style={styles.spinner} />
        <p style={{ color: '#666', marginTop: '16px' }}>Checking authentication...</p>
      </div>
    );
  }

  // Not authenticated — show login screen
  if (!isAuthenticated) {
    return (
      <div style={styles.container}>
        <div style={styles.card}>
          <div style={{ textAlign: 'center', marginBottom: '32px' }}>
            <div style={{ fontSize: '48px', marginBottom: '16px' }}>🚀</div>
            <h1 style={styles.title}>FirstGit</h1>
            <p style={styles.subtitle}>Deploy your projects to GitHub in seconds</p>
          </div>

          <button onClick={login} style={styles.githubButton}>
            <svg width="20" height="20" viewBox="0 0 16 16" fill="white" style={{ marginRight: '10px' }}>
              <path fillRule="evenodd" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
            </svg>
            Sign in with GitHub
          </button>

          <div style={styles.footer}>
            <p style={{ fontSize: '12px', color: '#999', margin: 0 }}>
              Uses GitHub OAuth — no personal access token required.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Authenticated — show deployment UI
  return (
    <div style={styles.container}>
      {/* Loading Overlay */}
      {deploying && (
        <div style={styles.overlay}>
          <div style={styles.modal}>
            <div style={styles.spinner} />
            <h2 style={{ margin: '20px 0 10px', color: '#333' }}>Deploying...</h2>
            <p style={{ color: '#666', fontSize: '14px', minHeight: '20px' }}>{deployProgress}</p>
            <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '16px' }}>
              {[0, 0.2, 0.4].map((delay, i) => (
                <span key={i} style={{
                  display: 'inline-block',
                  width: '8px',
                  height: '8px',
                  background: '#667eea',
                  borderRadius: '50%',
                  animation: `bounce 1.4s infinite`,
                  animationDelay: `${delay}s`,
                }} />
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal */}
      {showConfirmation && deployResult?.ok && (
        <div style={styles.overlay}>
          <div style={{ ...styles.modal, textAlign: 'center', maxWidth: '450px' }}>
            <div style={{
              width: '80px', height: '80px', background: '#d4edda',
              borderRadius: '50%', margin: '0 auto 20px',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '40px', color: '#155724',
            }}>
              ✓
            </div>
            <h2 style={{ margin: '20px 0', color: '#155724', fontSize: '28px' }}>Success!</h2>
            <p style={{ color: '#666', marginBottom: '25px', lineHeight: '1.6' }}>
              Your project has been deployed to GitHub.
            </p>
            <div style={{
              background: '#f5f5f5', padding: '15px', borderRadius: '8px',
              marginBottom: '25px', wordBreak: 'break-all',
            }}>
              <p style={{ margin: '0 0 8px 0', color: '#999', fontSize: '12px' }}>Repository URL:</p>
              <a href={deployResult.repoUrl} target="_blank" rel="noopener noreferrer"
                 style={{ color: '#667eea', fontSize: '14px', fontWeight: 'bold', textDecoration: 'none' }}>
                {deployResult.repoUrl}
              </a>
            </div>
            <div style={{ display: 'flex', gap: '10px', justifyContent: 'center' }}>
              <button onClick={() => window.open(deployResult.repoUrl, '_blank')} style={styles.primaryButton}>
                View on GitHub
              </button>
              <button onClick={() => setShowConfirmation(false)} style={styles.secondaryButton}>
                Deploy Another
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Card */}
      <div style={styles.card}>
        {/* User Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', paddingBottom: '16px', borderBottom: '1px solid #eee' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <img
              src={user.avatarUrl}
              alt={user.login}
              style={{ width: '40px', height: '40px', borderRadius: '50%', border: '2px solid #667eea' }}
              onError={(e) => { e.target.style.display = 'none'; }}
            />
            <div>
              <p style={{ margin: 0, fontWeight: '600', color: '#333' }}>{user.name || user.login}</p>
              <p style={{ margin: 0, fontSize: '12px', color: '#999' }}>@{user.login}</p>
            </div>
          </div>
          <button onClick={logout} style={styles.logoutButton}>
            Logout
          </button>
        </div>

        <h1 style={{ ...styles.title, marginBottom: '8px' }}>Deploy to GitHub</h1>
        <p style={styles.subtitle}>Upload a ZIP file and deploy it as a repository</p>

        {/* File Upload */}
        <div style={{ marginBottom: '20px' }}>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600', color: '#333' }}>
            Project ZIP File
          </label>
          <div
            style={{
              padding: '30px 20px',
              border: `2px dashed ${dragOver ? '#667eea' : '#ddd'}`,
              borderRadius: '8px',
              cursor: 'pointer',
              background: dragOver ? '#f0f0ff' : '#fafafa',
              textAlign: 'center',
              transition: 'all 0.3s',
            }}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => { e.preventDefault(); setDragOver(false); if (e.dataTransfer.files?.[0]) handleFileSelect(e.dataTransfer.files[0]); }}
            onClick={() => document.getElementById('fileInput').click()}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') document.getElementById('fileInput').click(); }}
            tabIndex={0}
            role="button"
            aria-label="Upload ZIP file"
          >
            <input
              type="file"
              accept=".zip"
              id="fileInput"
              style={{ display: 'none' }}
              onChange={(e) => handleFileSelect(e.target.files?.[0] || null)}
            />
            <div style={{ fontSize: '30px', marginBottom: '10px' }}>📦</div>
            <p style={{ margin: '0 0 5px 0', color: '#333', fontWeight: '600' }}>
              {selectedFile ? 'Click to change file' : 'Click to upload or drag and drop'}
            </p>
            <p style={{ margin: 0, color: '#999', fontSize: '12px' }}>ZIP files only (max 100MB)</p>
          </div>
          {selectedFile && (
            <p style={{ fontSize: '12px', color: '#667eea', marginTop: '10px', fontWeight: '600' }}>
              ✓ {selectedFile.name} ({(selectedFile.size / 1024 / 1024).toFixed(2)} MB)
            </p>
          )}
        </div>

        {/* Repository Name */}
        <div style={{ marginBottom: '20px' }}>
          <label style={{ display: 'block', marginBottom: '8px', fontWeight: '600', color: '#333' }}>
            Repository Name
          </label>
          <input
            type="text"
            placeholder="my-awesome-project"
            value={repoName}
            onChange={(e) => { setRepoName(e.target.value); setValidationError(''); }}
            onKeyDown={handleKeyDown}
            maxLength={100}
            style={{
              width: '100%',
              padding: '12px',
              border: `2px solid ${validationError ? '#dc3545' : '#e0e0e0'}`,
              borderRadius: '8px',
              fontSize: '14px',
              boxSizing: 'border-box',
              transition: 'border-color 0.3s',
            }}
            onFocus={(e) => { if (!validationError) e.target.style.borderColor = '#667eea'; }}
            onBlur={(e) => { if (!validationError) e.target.style.borderColor = '#e0e0e0'; }}
          />
          {validationError && (
            <p style={{ fontSize: '12px', color: '#dc3545', marginTop: '6px' }}>{validationError}</p>
          )}
        </div>

        {/* Private Toggle */}
        <div style={{ marginBottom: '25px', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <input
            type="checkbox"
            id="privateRepo"
            checked={isPrivate}
            onChange={(e) => setIsPrivate(e.target.checked)}
            style={{ cursor: 'pointer' }}
          />
          <label htmlFor="privateRepo" style={{ color: '#333', cursor: 'pointer', fontWeight: '500' }}>
            Make repository private
          </label>
        </div>

        {/* Deploy Button */}
        <button
          onClick={handleDeploy}
          disabled={deploying}
          style={{
            width: '100%',
            padding: '14px',
            background: deploying ? '#999' : '#667eea',
            color: 'white',
            border: 'none',
            borderRadius: '8px',
            fontSize: '16px',
            fontWeight: 'bold',
            cursor: deploying ? 'not-allowed' : 'pointer',
            transition: 'all 0.3s',
            boxShadow: deploying ? 'none' : '0 4px 15px rgba(102, 126, 234, 0.3)',
          }}
          onMouseEnter={(e) => {
            if (!deploying) {
              e.target.style.background = '#5568d3';
              e.target.style.transform = 'translateY(-2px)';
              e.target.style.boxShadow = '0 6px 20px rgba(102, 126, 234, 0.4)';
            }
          }}
          onMouseLeave={(e) => {
            if (!deploying) {
              e.target.style.background = '#667eea';
              e.target.style.transform = 'translateY(0)';
              e.target.style.boxShadow = '0 4px 15px rgba(102, 126, 234, 0.3)';
            }
          }}
        >
          {deploying ? 'Deploying...' : 'Deploy to GitHub'}
        </button>

        {/* Error Message */}
        {deployResult && !deployResult.ok && (
          <div style={styles.errorBox}>
            ⚠️ {deployResult.message}
          </div>
        )}
      </div>
    </div>
  );
}

// Inline styles
const styles = {
  container: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: '20px',
    fontFamily: "'Segoe UI', Tahoma, Geneva, Verdana, sans-serif",
  },
  card: {
    background: 'white',
    borderRadius: '15px',
    boxShadow: '0 10px 40px rgba(0,0,0,0.2)',
    padding: '40px',
    maxWidth: '500px',
    width: '100%',
    animation: 'slideUp 0.5s ease-out',
    position: 'relative',
  },
  title: {
    textAlign: 'center',
    color: '#333',
    fontSize: '32px',
    margin: 0,
  },
  subtitle: {
    textAlign: 'center',
    color: '#666',
    marginBottom: '30px',
    fontSize: '15px',
  },
  githubButton: {
    width: '100%',
    padding: '14px',
    background: '#24292e',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    fontSize: '16px',
    fontWeight: 'bold',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    transition: 'background 0.3s, transform 0.3s',
    boxShadow: '0 4px 15px rgba(0,0,0,0.2)',
  },
  primaryButton: {
    padding: '12px 30px',
    background: '#667eea',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 'bold',
    transition: 'background 0.3s',
  },
  secondaryButton: {
    padding: '12px 30px',
    background: '#f0f0f0',
    color: '#333',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 'bold',
    transition: 'background 0.3s',
  },
  logoutButton: {
    padding: '8px 16px',
    background: '#f8f8f8',
    color: '#666',
    border: '1px solid #ddd',
    borderRadius: '6px',
    cursor: 'pointer',
    fontSize: '13px',
    fontWeight: '500',
    transition: 'all 0.3s',
  },
  loadingContainer: {
    minHeight: '100vh',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
  },
  spinner: {
    width: '48px',
    height: '48px',
    border: '4px solid rgba(255,255,255,0.3)',
    borderTop: '4px solid white',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
  },
  overlay: {
    position: 'fixed',
    top: 0, left: 0, right: 0, bottom: 0,
    background: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 100,
    animation: 'fadeIn 0.3s ease-in',
  },
  modal: {
    background: 'white',
    padding: '40px',
    borderRadius: '15px',
    textAlign: 'center',
    boxShadow: '0 20px 60px rgba(0,0,0,0.3)',
    animation: 'slideUp 0.4s ease-out',
  },
  errorBox: {
    marginTop: '20px',
    padding: '15px',
    borderRadius: '8px',
    background: '#f8d7da',
    color: '#721c24',
    border: '1px solid #f5c6cb',
    fontSize: '14px',
    animation: 'slideUp 0.3s ease-out',
  },
  footer: {
    marginTop: '30px',
    paddingTop: '20px',
    borderTop: '1px solid #eee',
    textAlign: 'center',
  },
};

export default App;

