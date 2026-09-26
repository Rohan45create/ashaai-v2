import { create } from 'zustand';

// Backend URL configuration - injected at build time via VITE_BACKEND_URL or VITE_API_BASE_URL
const BASE_URL = (import.meta.env.VITE_BACKEND_URL || import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').trim().replace(/\/+$/, '');

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
