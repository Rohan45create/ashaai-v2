/**
 * On-Device Text-to-Speech (TTS) utility using the Web Speech API (window.speechSynthesis).
 * 
 * Benefits per docs/ARCHITECTURE.md:
 * - Free: No cloud API calls, zero credit consumption.
 * - Works offline: Runs directly on the user's browser/device.
 * - Zero network latency for synthesized voice feedback.
 */

const LANG_MAPPINGS = {
  hi: ['hi-IN', 'hi'],
  mr: ['mr-IN', 'mr', 'hi-IN'], // Marathi falls back to Hindi phonetics if mr-IN voice not installed
  en: ['en-IN', 'en-GB', 'en-US', 'en']
};

class SpeechTts {
  constructor() {
    this.voices = [];
    this.activeUtterances = [];
    this.isInitialized = false;

    if (this.isSupported()) {
      this._loadVoices();
      if (typeof window.speechSynthesis.onvoiceschanged !== 'undefined') {
        window.speechSynthesis.onvoiceschanged = () => {
          this._loadVoices();
        };
      }
    }
  }

  isSupported() {
    return typeof window !== 'undefined' &&
      'speechSynthesis' in window &&
      'SpeechSynthesisUtterance' in window;
  }

  _loadVoices() {
    try {
      this.voices = window.speechSynthesis.getVoices() || [];
      this.isInitialized = true;
    } catch (e) {
      console.warn('[SpeechTts] Error loading voices:', e);
      this.voices = [];
    }
  }

  /**
   * Find the most appropriate system voice for the requested language code.
   * @param {string} langCode - 'en', 'hi', 'mr', or BCP-47 tag
   */
  findVoice(langCode = 'en') {
    if (!this.isSupported()) return null;
    if (!this.voices.length) {
      this._loadVoices();
    }

    const preferredTags = LANG_MAPPINGS[langCode.toLowerCase()] || [langCode];

    for (const tag of preferredTags) {
      const target = tag.toLowerCase();
      // Exact match
      const exact = this.voices.find(v => v.lang.toLowerCase() === target);
      if (exact) return exact;

      // Prefix match (e.g. 'en-' matches 'en-US', 'en-IN')
      const prefix = this.voices.find(v => v.lang.toLowerCase().startsWith(target.split('-')[0]));
      if (prefix) return prefix;
    }

    // Default voice fallback
    return this.voices.find(v => v.default) || this.voices[0] || null;
  }

  /**
   * Split long text into manageable sentences to prevent browser utterance timeouts.
   * Handles English punctuation as well as Devanagari danda (।).
   */
  splitSentences(text) {
    if (!text) return [];
    // Match sentences ending in ., !, ?, or । (Devanagari danda)
    const regex = /[^.!?।\n]+[.!?।\n]+|[^.!?।\n]+$/g;
    const matches = text.match(regex);
    if (!matches) return [text.trim()];
    return matches.map(s => s.trim()).filter(Boolean);
  }

  /**
   * Speak a text string on the local device.
   * 
   * @param {string} text - text to speak
   * @param {Object} options
   * @param {string} [options.lang='en'] - language ('en', 'hi', 'mr')
   * @param {number} [options.rate=1.0] - speed multiplier (0.8 - 1.2 recommended)
   * @param {number} [options.pitch=1.0] - pitch (0.5 - 1.5)
   * @param {Function} [options.onStart] - called when speech begins
   * @param {Function} [options.onEnd] - called when all sentences finish
   * @param {Function} [options.onError] - called on error
   * @returns {Promise<boolean>} resolves true when speech finishes normally, false if cancelled
   */
  speak(text, {
    lang = 'en',
    rate = 1.0,
    pitch = 1.0,
    onStart,
    onEnd,
    onError
  } = {}) {
    if (!this.isSupported()) {
      const err = new Error('Web Speech API (speechSynthesis) is not supported in this browser');
      if (onError) onError(err);
      return Promise.reject(err);
    }

    // Stop any existing utterance
    this.stop();

    const sentences = this.splitSentences(text);
    if (sentences.length === 0) {
      if (onEnd) onEnd();
      return Promise.resolve(true);
    }

    const voice = this.findVoice(lang);
    let started = false;

    return new Promise((resolve, reject) => {
      let currentIndex = 0;

      const speakNextSentence = () => {
        if (currentIndex >= sentences.length) {
          this.activeUtterances = [];
          if (onEnd) onEnd();
          resolve(true);
          return;
        }

        const sentenceText = sentences[currentIndex];
        const utterance = new SpeechSynthesisUtterance(sentenceText);

        if (voice) {
          utterance.voice = voice;
          utterance.lang = voice.lang;
        } else {
          utterance.lang = lang === 'hi' ? 'hi-IN' : (lang === 'mr' ? 'mr-IN' : 'en-IN');
        }

        utterance.rate = rate;
        utterance.pitch = pitch;

        utterance.onstart = () => {
          if (!started) {
            started = true;
            if (onStart) onStart();
          }
        };

        utterance.onend = () => {
          currentIndex++;
          speakNextSentence();
        };

        utterance.onerror = (e) => {
          // If interrupted or canceled by stop(), do not treat as fatal error
          if (e.error === 'interrupted' || e.error === 'canceled') {
            this.activeUtterances = [];
            resolve(false);
            return;
          }
          console.warn('[SpeechTts] Utterance error:', e);
          this.activeUtterances = [];
          if (onError) onError(e);
          reject(e);
        };

        // Retain utterance reference to avoid premature garbage collection bug in Chrome
        this.activeUtterances = [utterance];
        window.speechSynthesis.speak(utterance);
      };

      // Resume in case speech synthesis was stuck paused (browser bug workaround)
      if (window.speechSynthesis.paused) {
        window.speechSynthesis.resume();
      }

      speakNextSentence();
    });
  }

  /**
   * Immediately cancel any in-flight utterance.
   */
  stop() {
    if (!this.isSupported()) return;
    try {
      this.activeUtterances = [];
      window.speechSynthesis.cancel();
    } catch (e) {
      console.warn('[SpeechTts] Error during stop:', e);
    }
  }

  /**
   * Check if speech is currently playing.
   */
  isSpeaking() {
    if (!this.isSupported()) return false;
    return window.speechSynthesis.speaking;
  }
}

export const speechTts = new SpeechTts();
