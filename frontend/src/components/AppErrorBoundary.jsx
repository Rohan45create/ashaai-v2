import React from 'react';

/**
 * AppErrorBoundary — top-level React error boundary.
 *
 * Wraps the route tree so that any uncaught render error (e.g. a missing method
 * on a shim, a null-deref, a bad API response shape) shows a visible error card
 * instead of silently blanking the page.
 *
 * Per RULES.md error-handling conventions: surface a clear message to the user
 * rather than letting a raw stack trace hide behind a blank screen.
 */
export default class AppErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    // Log for debugging — component stack helps trace the offending component.
    console.error('[AppErrorBoundary] Uncaught render error:', error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            minHeight: '100vh',
            background: '#F1EFE8',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '24px',
            fontFamily: 'system-ui, sans-serif'
          }}
        >
          <div
            style={{
              background: '#fff',
              borderRadius: '16px',
              padding: '32px',
              maxWidth: '420px',
              width: '100%',
              boxShadow: '0 2px 16px rgba(0,0,0,0.08)',
              textAlign: 'center'
            }}
          >
            <div style={{ fontSize: '40px', marginBottom: '12px' }}>⚠️</div>
            <h2 style={{ color: '#E24B4A', fontWeight: 700, marginBottom: '8px', fontSize: '18px' }}>
              Something went wrong
            </h2>
            <p style={{ color: '#5F5E5A', fontSize: '14px', marginBottom: '8px' }}>
              {this.state.error?.message || 'An unexpected error occurred.'}
            </p>
            <p style={{ color: '#9E9D99', fontSize: '12px', marginBottom: '20px' }}>
              If this keeps happening, contact your supervisor or check your internet connection.
            </p>
            <button
              onClick={() => window.location.reload()}
              style={{
                background: '#1D9E75',
                color: '#fff',
                border: 'none',
                borderRadius: '10px',
                padding: '10px 28px',
                fontSize: '14px',
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              Reload App
            </button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
