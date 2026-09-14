import React, { useState, useEffect, useRef } from 'react';
import { useConversationalVad } from '../hooks/useConversationalVad';
import { useConversationalTts } from '../hooks/useConversationalTts';
import { apiFetch, showToast } from '../utils/api';
import { useTranslation } from 'react-i18next';

export default function ConversationalAgentFAB({ role = 'ASHA' }) {
  const [isOpen, setIsOpen] = useState(false);
  const [status, setStatus] = useState('IDLE'); // IDLE, LISTENING, PROCESSING, SPEAKING, ERROR
  const sessionIdRef = useRef(`session_${Date.now()}`);
  const isFirstTurnRef = useRef(true);
  const { t } = useTranslation();

  const handleSpeechStart = () => {
    setStatus('LISTENING');
  };

  const { speak, stop: stopSpeaking } = useConversationalTts({
    pauseListening: () => {
      setStatus('SPEAKING');
      vad.pauseListening();
    },
    resumeListening: () => {
      setStatus('IDLE');
      vad.resumeListening();
    },
    onSpeakComplete: () => {
      setStatus('IDLE');
    }
  });

  const handleTurnComplete = async (audioBlob) => {
    setStatus('PROCESSING');
    try {
      // 1. Transcribe the audio
      const formData = new FormData();
      formData.append('audio', audioBlob, 'turn.webm');
      const sttResponse = await apiFetch('/api/voice/transcribe', {
        method: 'POST',
        body: formData,
      });

      let utterance = '';
      if (sttResponse.transcript) {
        utterance = sttResponse.transcript;
      } else {
        utterance = typeof sttResponse === 'string' ? sttResponse : JSON.stringify(sttResponse);
      }

      // 2. Classify intent if first turn
      if (isFirstTurnRef.current) {
        await apiFetch('/api/agent/classify-intent', {
          method: 'POST',
          body: JSON.stringify({
            utterance,
            session_id: sessionIdRef.current,
            use_llm: true
          })
        });
        isFirstTurnRef.current = false;
      }

      // 3. Process the agent turn
      const turnResponse = await apiFetch('/api/agent/turn', {
        method: 'POST',
        body: JSON.stringify({
          session_id: sessionIdRef.current,
          utterance,
          role
        })
      });

      // 4. Handle tools execution side effects
      if (turnResponse.tools_executed) {
        turnResponse.tools_executed.forEach(tool => {
          if (tool.tool === 'export_pdf') {
            showToast(t('Report exported successfully!'));
            // Optionally redirect to reports or show download link
          }
          if (tool.tool === 'get_draft_summary' && role === 'ASHA') {
            showToast(t('Draft summary ready'));
            // Could auto-navigate to the review page if the agent signals it's done
            // e.g. navigate(`/asha/draft/${sessionIdRef.current}`);
          }
        });
      }

      // 5. Speak the reply
      if (turnResponse.reply) {
        speak(turnResponse.reply);
      } else {
        setStatus('IDLE');
      }

    } catch (err) {
      console.error('[AgentFAB] Turn error:', err);
      setStatus('ERROR');
      showToast(t('Agent encountered an error'), 'error');
      setTimeout(() => setStatus('IDLE'), 2000);
    }
  };

  const vad = useConversationalVad({
    onTurnComplete: handleTurnComplete,
    onSpeechStart: handleSpeechStart,
    silenceDurationMs: 1200
  });

  const toggleSession = async () => {
    if (isOpen) {
      // Close session
      vad.stopSession();
      stopSpeaking();
      setIsOpen(false);
      setStatus('IDLE');
      // Reset session ID for next time
      sessionIdRef.current = `session_${Date.now()}`;
      isFirstTurnRef.current = true;
    } else {
      // Start session
      setIsOpen(true);
      setStatus('LISTENING');
      await vad.startSession();
      // Optional greeting
      // speak(t('Hi, how can I help you?'));
    }
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      vad.stopSession();
      stopSpeaking();
    };
  }, []);

  return (
    <div className="fixed bottom-6 right-6 z-50 flex flex-col items-end">
      {isOpen && (
        <div className="mb-4 bg-white rounded-xl p-4 shadow-lg border border-[#D3D1C7] w-64 animate-fade-in-up">
          <div className="flex items-center space-x-3">
            <div className={`w-3 h-3 rounded-full ${
              status === 'LISTENING' ? 'bg-red-500 animate-pulse' :
              status === 'PROCESSING' ? 'bg-yellow-500 animate-bounce' :
              status === 'SPEAKING' ? 'bg-green-500' :
              status === 'ERROR' ? 'bg-red-700' :
              'bg-[#1D9E75]'
            }`} />
            <span className="text-sm font-medium text-[#5F5E5A]">
              {status === 'LISTENING' ? t('Listening...') :
               status === 'PROCESSING' ? t('Thinking...') :
               status === 'SPEAKING' ? t('Speaking...') :
               status === 'ERROR' ? t('Error') :
               t('AshaAI Agent')}
            </span>
          </div>
          {status === 'LISTENING' && vad.volumeLevel > 0.05 && (
            <div className="mt-2 h-1 bg-gray-200 rounded overflow-hidden">
              <div className="h-full bg-red-500 transition-all duration-75" style={{ width: `${Math.min(vad.volumeLevel * 100, 100)}%` }} />
            </div>
          )}
          <div className="mt-3 text-xs text-gray-500">
            {role === 'ASHA' ? t('Tip: Try "Fill a family survey" or "Export a report"') : t('Tip: Try "Draft a new survey"')}
          </div>
        </div>
      )}

      <button
        onClick={toggleSession}
        className={`w-14 h-14 rounded-full flex items-center justify-center shadow-xl transition-all duration-300 ${
          isOpen ? 'bg-[#E24B4A] hover:bg-red-600 rotate-45' : 'bg-[#085041] hover:bg-[#0a6652] hover:scale-105'
        }`}
        aria-label="Toggle Conversational Agent"
      >
        <span className="material-symbols-outlined text-white text-3xl">
          {isOpen ? 'close' : 'support_agent'}
        </span>
      </button>
    </div>
  );
}
