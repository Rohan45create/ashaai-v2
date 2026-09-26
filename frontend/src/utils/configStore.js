import { create } from 'zustand';

// Backend URL configuration - injected at build time via VITE_BACKEND_URL or VITE_API_BASE_URL
const formatBackendUrl = (url) => {
  if (!url) return 'http://localhost:8080';
  let cleanUrl = url.trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(cleanUrl) && !cleanUrl.startsWith('localhost') && !cleanUrl.startsWith('127.0.0.1')) {
    return `https://${cleanUrl}`;
  }
  return cleanUrl;
};

const BASE_URL = formatBackendUrl(import.meta.env.VITE_BACKEND_URL || import.meta.env.VITE_API_BASE_URL);

export const useConfigStore = create((set) => ({
  supabaseUrl: null,
  supabaseAnonKey: null,
  googleMapsApiKey: null,
  ngoFormNewUrl: null,
  ngoFormExistingUrl: null,
  ngoFormRescheduleUrl: null,
  isConfigLoaded: false,
  error: null,

  fetchConfig: async () => {
    try {
      const response = await fetch(`${BASE_URL}/api/public-config`);
      if (!response.ok) {
        throw new Error('Failed to fetch public configuration');
      }
      
      const config = await response.json();
      
      set({
        supabaseUrl: config.supabaseUrl,
        supabaseAnonKey: config.supabaseAnonKey,
        googleMapsApiKey: config.googleMapsApiKey,
        ngoFormNewUrl: config.ngoFormNewUrl,
        ngoFormExistingUrl: config.ngoFormExistingUrl,
        ngoFormRescheduleUrl: config.ngoFormRescheduleUrl,
        isConfigLoaded: true,
        error: null,
      });
    } catch (error) {
      console.error('Configuration load error:', error);
      set({ error: error.message, isConfigLoaded: false });
    }
  }
}));
