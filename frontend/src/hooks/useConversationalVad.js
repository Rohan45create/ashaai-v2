import { useState, useRef, useCallback, useEffect } from 'react';
import { VadDetector } from '../utils/vadDetector';

/**
 * Custom React hook for continuous client-side Voice Activity Detection (VAD).
 * 
 * Implements the continuous spoken session architecture in docs/ARCHITECTURE.md:
 * - Single tap starts continuous hands-free session
 * - Client-side VAD (Web Audio API) detects speech pauses (turn boundaries)
 * - Emits turn audio blob to onTurnComplete
 * - Can pause/resume listening when on-device TTS is speaking
 *
 * @param {Object} options
 * @param {Function} options.onTurnComplete - callback(audioBlob, mimeType) fired on turn boundary
 * @param {Function} options.onSpeechStart  - optional callback when user starts talking
 * @param {Function} options.onSpeechEnd    - optional callback when user finishes utterance
 * @param {number}   options.silenceDurationMs - pause duration before turn ends (default: 1200ms)
 */
export function useConversationalVad({
  onTurnComplete,
  onSpeechStart,
  onSpeechEnd,
  silenceDurationMs = 1200
}) {
  const [isSessionActive, setIsSessionActive] = useState(false);
  const [isUserSpeaking, setIsUserSpeaking] = useState(false);
  const [volumeLevel, setVolumeLevel] = useState(0);
  const [error, setError] = useState(null);

  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);
  const streamRef = useRef(null);
  const mediaRecorderRef = useRef(null);
  const vadDetectorRef = useRef(null);
  const animationFrameRef = useRef(null);
  const chunksRef = useRef([]);
  const isMutedForTtsRef = useRef(false);
  const isSessionActiveRef = useRef(false);

  // Stop current turn recorder & extract audio blob
  const finalizeCurrentTurn = useCallback(() => {
    if (!mediaRecorderRef.current || mediaRecorderRef.current.state === 'inactive') {
      return;
    }

    try {
      mediaRecorderRef.current.stop();
    } catch (e) {
      console.warn('[VAD] Error stopping turn recorder:', e);
    }
  }, []);

  // Setup / restart MediaRecorder for an utterance
  const setupMediaRecorder = useCallback((stream) => {
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : MediaRecorder.isTypeSupported('audio/webm')
      ? 'audio/webm'
      : 'audio/ogg;codecs=opus';

    const recorder = new MediaRecorder(stream, { mimeType });
    chunksRef.current = [];

    recorder.ondataavailable = (e) => {
      if (e.data && e.data.size > 0) {
        chunksRef.current.push(e.data);
      }
    };

    recorder.onstop = () => {
      if (chunksRef.current.length > 0) {
        const audioBlob = new Blob(chunksRef.current, { type: mimeType });
        chunksRef.current = [];

        // Only emit if session is active and blob has audio content (> 2KB)
        if (isSessionActiveRef.current && audioBlob.size > 2000 && onTurnComplete) {
          onTurnComplete(audioBlob, mimeType);
        }
      }

      // If session is still active, start recording for the next turn
      if (isSessionActiveRef.current && streamRef.current?.active) {
        setupMediaRecorder(streamRef.current);
      }
    };

    mediaRecorderRef.current = recorder;
    recorder.start(200); // 200ms chunk timeslices
  }, [onTurnComplete]);

  // Start continuous VAD session
  const startSession = useCallback(async () => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
          channelCount: 1
        }
      });
      streamRef.current = stream;

      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      const audioCtx = new AudioContextClass();
      audioContextRef.current = audioCtx;

      const source = audioCtx.createMediaStreamSource(stream);
      const analyser = audioCtx.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.4;
      source.connect(analyser);
      analyserRef.current = analyser;

      const detector = new VadDetector({
        silenceDurationMs,
        minSpeechDurationMs: 300,
        speechThreshold: 0.02,
        onSpeechStart: () => {
          if (isMutedForTtsRef.current) return;
          setIsUserSpeaking(true);
          onSpeechStart?.();
        },
        onSpeechEnd: () => {
          if (isMutedForTtsRef.current) return;
          setIsUserSpeaking(false);
          onSpeechEnd?.();
          finalizeCurrentTurn();
        },
        onVolumeChange: (normVol) => {
          if (!isMutedForTtsRef.current) {
            setVolumeLevel(normVol);
          }
        }
      });
      vadDetectorRef.current = detector;

      isSessionActiveRef.current = true;
      setIsSessionActive(true);

      // Start initial turn recorder
      setupMediaRecorder(stream);

      // Analysis frame loop
      const timeDomainData = new Uint8Array(analyser.frequencyBinCount);
      const loop = () => {
        if (!isSessionActiveRef.current) return;

        if (!isMutedForTtsRef.current && analyserRef.current) {
          analyserRef.current.getByteTimeDomainData(timeDomainData);
          vadDetectorRef.current?.processFrame(timeDomainData, performance.now());
        } else {
          setVolumeLevel(0);
        }

        animationFrameRef.current = requestAnimationFrame(loop);
      };

      animationFrameRef.current = requestAnimationFrame(loop);
    } catch (err) {
      console.error('[VAD] Session start failed:', err);
      setError(err.message || 'Microphone access denied');
      setIsSessionActive(false);
      isSessionActiveRef.current = false;
    }
  }, [finalizeCurrentTurn, onSpeechEnd, onSpeechStart, setupMediaRecorder, silenceDurationMs]);

  // Stop continuous session cleanly
  const stopSession = useCallback(() => {
    isSessionActiveRef.current = false;
    setIsSessionActive(false);
    setIsUserSpeaking(false);
    setVolumeLevel(0);

    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }

    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop();
      } catch (e) {}
    }
    mediaRecorderRef.current = null;

    if (audioContextRef.current) {
      try {
        audioContextRef.current.close();
      } catch (e) {}
      audioContextRef.current = null;
    }

    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }

    vadDetectorRef.current?.reset();
  }, []);

  // Pause VAD listening (e.g. while agent speaks TTS)
  const pauseListening = useCallback(() => {
    isMutedForTtsRef.current = true;
    setIsUserSpeaking(false);
    setVolumeLevel(0);
    vadDetectorRef.current?.reset();
  }, []);

  // Resume VAD listening after agent finishes speaking
  const resumeListening = useCallback(() => {
    isMutedForTtsRef.current = false;
    vadDetectorRef.current?.reset();
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopSession();
    };
  }, [stopSession]);

  return {
    isSessionActive,
    isUserSpeaking,
    volumeLevel,
    error,
    startSession,
    stopSession,
    pauseListening,
    resumeListening
  };
}
