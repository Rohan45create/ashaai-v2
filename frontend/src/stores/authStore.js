import { create } from 'zustand';
import { resolveIdentity, showToast } from '../utils/api';
import { createClient } from '@supabase/supabase-js';
import { useConfigStore } from '../utils/configStore';

export const useAuthStore = create((set, get) => ({
  user: null,
  role: null,
  docId: null,
  headId: null,       // Kept for backward compatibility
  ashaId: null,       // Kept for backward compatibility
  isAuthenticated: false,
  isLoading: true,
  supabase: null,

  setUser: (user) => set({ user }),
  setRole: (role) => set({ role }),
  setLoading: (isLoading) => set({ isLoading }),
  setHeadId: (headId) => set({ headId }),
  setAshaId: (ashaId) => set({ ashaId }),

  getSupabase: () => {
    let { supabase } = get();
    if (!supabase) {
      const { supabaseUrl, supabaseAnonKey } = useConfigStore.getState();
      if (!supabaseUrl || !supabaseAnonKey) {
        throw new Error('Supabase config not loaded');
      }
      supabase = createClient(supabaseUrl, supabaseAnonKey, {
        auth: {
          autoRefreshToken: true,
          persistSession: true
        }
      });
      set({ supabase });
    }
    return supabase;
  },

  login: async (email, password, navigate) => {
    try {
      const supabase = get().getSupabase();
      
      const { data, error } = await supabase.auth.signInWithPassword({
        email,
        password,
      });

      if (error) throw error;
      
      if (!data.session) {
        throw new Error('No session established');
      }

      const token = data.session.access_token;
      
      // Resolve identity against Spring Boot to get our custom role/doc_id
      const identity = await resolveIdentity(token);

      const docId = identity.doc_id;
      const role = identity.role;

      set({
        user: data.user,
        docId: docId,
        role: role,
        headId: role === 'asha_head' ? docId : null,
        ashaId: role === 'asha_worker' ? docId : null,
        isAuthenticated: true
      });

      if (role === 'asha_head') {
        navigate('/admin/dashboard');
      } else {
        navigate('/asha/home');
      }

    } catch (err) {
      if (err.code === 'PROFILE_NOT_FOUND') {
        showToast("Account not found. Please contact your supervisor.", "error");
      } else {
        showToast(err.message || "Login failed. Please try again.", "error");
      }
      await get().logout();
      throw err;
    }
  },

  sendOtp: async (phone) => {
    try {
      const supabase = get().getSupabase();
      const { error } = await supabase.auth.signInWithOtp({ phone });
      if (error) throw error;
    } catch (err) {
      showToast(err.message || "Failed to send OTP", "error");
      throw err;
    }
  },

  verifyOtp: async (phone, otp, navigate) => {
    try {
      const supabase = get().getSupabase();
      const { data, error } = await supabase.auth.verifyOtp({
        phone,
        token: otp,
        type: 'sms',
      });

      if (error) throw error;
      
      if (!data.session) {
        throw new Error('No session established');
      }

      const token = data.session.access_token;
      const identity = await resolveIdentity(token);

      const docId = identity.doc_id;
      const role = identity.role;

      set({
        user: data.user,
        docId: docId,
        role: role,
        headId: role === 'asha_head' ? docId : null,
        ashaId: role === 'asha_worker' ? docId : null,
        isAuthenticated: true
      });

      if (role === 'asha_head') {
        navigate('/admin/dashboard');
      } else {
        navigate('/asha/home');
      }
    } catch (err) {
      if (err.code === 'PROFILE_NOT_FOUND') {
        showToast("Account not found. Please contact your supervisor.", "error");
      } else {
        showToast(err.message || "Invalid OTP. Please try again.", "error");
      }
      await get().logout();
      throw err;
    }
  },

  logout: async () => {
    try {
      const supabase = get().getSupabase();
      await supabase.auth.signOut();
    } catch (e) {}
    set({
      user: null,
      role: null,
      docId: null,
      headId: null,
      ashaId: null,
      isAuthenticated: false,
      isLoading: false
    });
  }
}));
