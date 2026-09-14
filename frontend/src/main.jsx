import { StrictMode, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './i18n';
import App from './App.jsx'
import { TranslationProvider } from './context/TranslationContext.jsx'
import { useConfigStore } from './utils/configStore'

// When a new service worker is available, reload automatically
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    window.location.reload()
  })
}

const Root = () => {
  const { isConfigLoaded, error, fetchConfig } = useConfigStore();

  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);

  if (error) {
    return (
      <div style={{ padding: '2rem', color: 'red', fontFamily: 'sans-serif' }}>
        <h2>Failed to load configuration</h2>
        <p>{error}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
      </div>
    );
  }

  if (!isConfigLoaded) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', fontFamily: 'sans-serif' }}>
        <h2>Loading AshaAI...</h2>
      </div>
    );
  }

  return (
    <StrictMode>
      <TranslationProvider>
        <App />
      </TranslationProvider>
    </StrictMode>
  );
};

createRoot(document.getElementById('root')).render(<Root />);
