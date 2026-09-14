/**
 * Pure Web Audio API Voice Activity Detector (VAD).
 * Operates client-side without any cloud dependency.
 * Analyzes audio energy (RMS) to detect speech start, speaking duration, and pause/silence.
 */

export class VadDetector {
  constructor(options = {}) {
    this.speechThreshold = options.speechThreshold || 0.02; // Minimum RMS to trigger speech
    this.silenceDurationMs = options.silenceDurationMs || 1200; // 1.2s silence = turn complete
    this.minSpeechDurationMs = options.minSpeechDurationMs || 300; // 300ms minimum to qualify as speech
    this.noiseFloorAlpha = options.noiseFloorAlpha || 0.05; // Learning rate for background noise floor

    this.isSpeaking = false;
    this.speechStartTime = null;
    this.lastSpeechTime = null;
    this.noiseFloor = 0.005;

    this.onSpeechStart = options.onSpeechStart || null;
    this.onSpeechEnd = options.onSpeechEnd || null; // Fired when silence threshold reached (turn complete)
    this.onVolumeChange = options.onVolumeChange || null;

    this._silenceTimer = null;
  }

  /**
   * Calculates Root Mean Square (RMS) energy from time-domain audio data array.
   */
  calculateRms(timeDomainData) {
    let sumSquares = 0;
    for (let i = 0; i < timeDomainData.length; i++) {
      const normalized = (timeDomainData[i] - 128) / 128; // byte data 0..255 -> -1..1
      sumSquares += normalized * normalized;
    }
    return Math.sqrt(sumSquares / timeDomainData.length);
  }

  /**
   * Process a single audio frame (called from requestAnimationFrame or ScriptProcessor/AudioWorklet).
   * @param {Uint8Array} timeDomainData
   * @param {number} currentTimeMs - performance.now()
   */
  processFrame(timeDomainData, currentTimeMs = performance.now()) {
    const rms = this.calculateRms(timeDomainData);

    // Update dynamic background noise floor when not speaking
    if (!this.isSpeaking && rms < this.speechThreshold) {
      this.noiseFloor = (1 - this.noiseFloorAlpha) * this.noiseFloor + this.noiseFloorAlpha * rms;
    }

    const effectiveThreshold = Math.max(this.speechThreshold, this.noiseFloor * 2.5);
    const hasVoice = rms > effectiveThreshold;

    if (this.onVolumeChange) {
      // Normalize volume for UI animation (0.0 to 1.0)
      const normalizedVol = Math.min(1, Math.max(0, (rms - this.noiseFloor) / (this.speechThreshold * 4)));
      this.onVolumeChange(normalizedVol, rms);
    }

    if (hasVoice) {
      this.lastSpeechTime = currentTimeMs;

      if (!this.isSpeaking) {
        if (this.speechStartTime === null) {
          this.speechStartTime = currentTimeMs;
        } else if (currentTimeMs - this.speechStartTime >= this.minSpeechDurationMs) {
          this.isSpeaking = true;
          if (this.onSpeechStart) {
            this.onSpeechStart();
          }
        }
      }
    } else {
      // Silence detected
      if (this.isSpeaking) {
        const lastSpeech = this.lastSpeechTime !== null ? this.lastSpeechTime : currentTimeMs;
        const silenceElapsed = currentTimeMs - lastSpeech;
        if (silenceElapsed >= this.silenceDurationMs) {
          // Pause reached: turn complete!
          this.isSpeaking = false;
          this.speechStartTime = null;
          this.lastSpeechTime = null;

          if (this.onSpeechEnd) {
            this.onSpeechEnd({ silenceDurationMs: silenceElapsed });
          }
        }
      } else {
        // Reset speechStartTime if noise burst was shorter than minSpeechDurationMs
        if (this.speechStartTime !== null && currentTimeMs - this.speechStartTime > this.minSpeechDurationMs * 2) {
          this.speechStartTime = null;
        }
      }
    }

    return {
      rms,
      isSpeaking: this.isSpeaking,
      effectiveThreshold
    };
  }

  reset() {
    this.isSpeaking = false;
    this.speechStartTime = null;
    this.lastSpeechTime = null;
  }
}
