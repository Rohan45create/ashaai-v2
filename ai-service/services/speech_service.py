import os
import azure.cognitiveservices.speech as speechsdk

class SpeechService:
    def __init__(self):
        self.speech_key = os.getenv("AZURE_SPEECH_KEY")
        self.speech_region = os.getenv("AZURE_SPEECH_REGION", "eastus")

    def transcribe_audio(self, audio_data: bytes, language: str = "mr-IN") -> str:
        """
        Transcribe audio using Azure Cognitive Services Speech.
        """
        if not self.speech_key:
            print("WARNING: AZURE_SPEECH_KEY not set. Returning stub transcription.")
            return "This is a stub transcription because AZURE_SPEECH_KEY is missing."

        # Write bytes to a temporary file since SpeechConfig needs a file path or custom stream
        import tempfile
        with tempfile.NamedTemporaryFile(delete=False, suffix=".wav") as tmp_file:
            tmp_file.write(audio_data)
            tmp_file_path = tmp_file.name

        try:
            speech_config = speechsdk.SpeechConfig(subscription=self.speech_key, region=self.speech_region)
            speech_config.speech_recognition_language = language
            
            audio_config = speechsdk.audio.AudioConfig(filename=tmp_file_path)
            speech_recognizer = speechsdk.SpeechRecognizer(speech_config=speech_config, audio_config=audio_config)

            # Perform recognition
            result = speech_recognizer.recognize_once()

            if result.reason == speechsdk.ResultReason.RecognizedSpeech:
                return result.text
            elif result.reason == speechsdk.ResultReason.NoMatch:
                return ""
            elif result.reason == speechsdk.ResultReason.Canceled:
                cancellation_details = result.cancellation_details
                raise RuntimeError(f"Speech Recognition canceled: {cancellation_details.reason}. Error Details: {cancellation_details.error_details}")
            
            return ""
        finally:
            if os.path.exists(tmp_file_path):
                os.remove(tmp_file_path)

# Singleton instance
speech_service = SpeechService()
