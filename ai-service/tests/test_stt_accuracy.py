"""STT Accuracy comparison script (Indic-Whisper vs Gemini)."""
import os
import sys
import glob

# Ensure ai-service is in Python path for local testing
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from services.provider_client import _call_hf_indic_whisper, _call_gemini_multimodal

def evaluate_stt_accuracy(samples_dir: str):
    """
    Evaluates transcription accuracy comparing Indic-Whisper to Gemini STT 
    for all .wav/.webm/.mp3 files in the samples_dir.
    """
    audio_files = []
    for ext in ("*.wav", "*.webm", "*.mp3"):
        audio_files.extend(glob.glob(os.path.join(samples_dir, ext)))
        
    if not audio_files:
        print(f"No audio files found in {samples_dir}.")
        return

    print("==================================================")
    print("      STT ACCURACY COMPARISON REPORT               ")
    print("==================================================")
    
    for audio_path in audio_files:
        print(f"\nProcessing file: {os.path.basename(audio_path)}")
        try:
            # 1. Indic-Whisper (Hugging Face)
            print("Running Indic-Whisper...")
            try:
                whisper_result = _call_hf_indic_whisper(audio_or_image=audio_path)
            except Exception as e:
                whisper_result = f"ERROR: {e}"

            # 2. Gemini
            print("Running Gemini Multimodal STT...")
            try:
                gemini_result = _call_gemini_multimodal(
                    prompt="Please accurately transcribe this audio.",
                    audio_or_image=audio_path,
                    mime_type="audio/wav" # Fallback mime, should ideally be inferred
                )
            except Exception as e:
                gemini_result = f"ERROR: {e}"

            print("\n--- RESULTS ---")
            print(f"INDIC-WHISPER: {whisper_result}")
            print(f"GEMINI:        {gemini_result}")
            print("--------------------------------------------------")
            
        except Exception as overall_e:
            print(f"Failed to process {audio_path}: {overall_e}")

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Compare STT Accuracy")
    parser.add_argument("--dir", type=str, default="samples", help="Directory containing sample audio files")
    args = parser.parse_args()
    
    evaluate_stt_accuracy(args.dir)
