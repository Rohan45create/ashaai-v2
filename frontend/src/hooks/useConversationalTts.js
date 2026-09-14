import { useState, useCallback, useEffect, useRef } from 'react';
import { speechTts } from '../utils/speechTts';
import { useLanguageStore } from '../stores/languageStore';

/**
 * Hook for conversational TTS with automatic VAD pause/resume coordination.
 * 
 * Prevents the microphone / VAD turn detector from hearing and transcribing
 * the agent's own spoken response.
 *
 * @param {Object} options
 * @param {Function} [options.pauseListening] - function to mute/pause VAD input
 * @param {Function} [options.resumeListening] - function to resume VAD input
 * @param {Function} [options.onSpeakComplete] - callback when agent finishes speaking
 */
export function useConversationalTts({
  pauseListening,
  resumeListening,
  onSpeakComplete
} = {}) {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [error, setError] = useState(null);
  const { language } = useLanguageStore();

  const pauseListeningRef = useRef(pauseListening);
  const resumeListeningRef = useRef(resumeListening);
  const onSpeakCompleteRef = useRef(onSpeakComplete);

  useEffect(() => {
    pauseListeningRef.current = pauseListening;
    resumeListeningRef.current = resumeListening;
    onSpeakCompleteRef.current = onSpeakComplete;
  });

  // Stop speech when hook unmounts
  useEffect(() => {
    return () => {
      speechTts.stop();
    };
  }, []);

  const speak = useCallback(async (text, options = {}) => {
    if (!text || !text.trim()) return false;

    const lang = options.lang || language || 'en';
    const rate = options.rate || 1.0;
    const pitch = options.pitch || 1.0;

    // 1. Mute/pause VAD before starting playback to avoid self-triggering
    if (pauseListeningRef.current) {
      pauseListeningRef.current();
    }
    setIsSpeaking(true);
    setError(null);

    try {
      const success = await speechTts.speak(text, {
        lang,
        rate,
        pitch,
        onStart: () => {
          setIsSpeaking(true);
        },
        onEnd: () => {
          setIsSpeaking(false);
          // 2. Resume VAD listening after playback finishes
          if (resumeListeningRef.current) {
            resumeListeningRef.current();
          }
          if (onSpeakCompleteRef.current) {
            onSpeakCompleteRef.current();
          }
        },
        onError: (err) => {
          console.warn('[useConversationalTts] Playback error:', err);
          setIsSpeaking(false);
          setError(err);
          // Resume VAD even on error so conversation doesn't get stuck
          if (resumeListeningRef.current) {
            resumeListeningRef.current();
          }
        }
      });

      return success;
    } catch (err) {
      setIsSpeaking(false);
      setError(err);
      if (resumeListeningRef.current) {
        resumeListeningRef.current();
      }
      return false;
    }
  }, [language]);

  const stop = useCallback(() => {
    speechTts.stop();
    setIsSpeaking(false);
    if (resumeListeningRef.current) {
      resumeListeningRef.current();
    }
  }, []);

  return {
    isSpeaking,
    error,
    speak,
    stop,
    isSupported: speechTts.isSupported()
  };
}
