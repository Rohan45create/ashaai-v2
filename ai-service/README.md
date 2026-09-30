# AshaAI — AI Service

The AshaAI AI service is a **Python FastAPI microservice** responsible for AI-related processing.

It is called **only by the Spring Boot backend**. The frontend never communicates with this service directly.

The service handles:

- AI provider fallback and routing
- Open-source Hugging Face models
- Near AI integration
- Gemini and Groq providers
- The malnutrition detection pipeline
- Voice transcription
- The Conversational Agent

The AI service **only generates drafts, suggestions, classifications, or other AI outputs**. It does not write application data directly to the database.

For the complete provider architecture and AI safety rules, see [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) and [`docs/AI_AUTONOMY_POLICY.md`](../docs/AI_AUTONOMY_POLICY.md).

## Prerequisites

- **Python 3.12+**
- **pip**
- A **Hugging Face account** with a read-scope access token
  - Required for the private malnutrition model repository
  - Also used by the open-source provider layer
- A **Gemini API key**
- A **Groq API key**
- **Docker**, only if you plan to build or run the container locally

Gemini API keys can be created through [Google AI Studio](https://aistudio.google.com).

## 1. Enter the folder and install dependencies

From the repository root:

```bash
cd ashaai-v2/ai-service
pip install -r requirements.txt --break-system-packages
```

This project can run with a regular Python installation, so the example above uses `--break-system-packages`.

If you prefer an isolated environment, create a virtual environment instead:

### Linux / macOS

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### Windows

```bash
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

## 2. Create the environment file

Create:

```text
ai-service/.env
```

If `main.py` uses a different environment-file name, use the name expected by the application.

Add the following configuration:

```env
# Gemini
GEMINI_API_KEY=<your-gemini-key>

# Groq
GROQ_API_KEY=<your-groq-key>

# Near AI
# Leave blank to disable Near AI.
# Near AI requires purchased credits and does not provide a free tier.
NEAR_AI_API_KEY=

# Hugging Face
# Use a read-scope token.
# The same token is used for the private malnutrition model repository
# and the open-source provider layer.
HUGGINGFACE_API_KEY=<your-hf-read-token>
HF_TOKEN=<same-value-as-HUGGINGFACE_API_KEY>

# Private Hugging Face repository containing the trained
# malnutrition ONNX classifier
ONNX_MALNUTRITION_REPO=<your-username>/<your-model-repo>
ONNX_MALNUTRITION_FILE=<exact-onnx-filename-in-that-repo>

# Service port
PORT=8000
```

**Never commit `.env` or any API keys to Git.**

## 3. Start the service

Run:

```bash
uvicorn main:app --reload --port 8000
```

The service will be available at:

```text
http://localhost:8000
```

FastAPI's interactive Swagger documentation is available at:

```text
http://localhost:8000/docs
```

You can use this page to inspect and test the available API endpoints during development.

## 4. Connect the backend

The Spring Boot backend communicates with the AI service through its internal HTTP API.

For local development, set the following in:

```text
backend/.env
```

```env
AI_SERVICE_URL=http://localhost:8000
```

The request flow is:

```text
Frontend
    │
    ▼
Spring Boot Backend
    │
    ▼
AI Service
    │
    ├──► Near AI
    ├──► Hugging Face
    ├──► Gemini
    ├──► Groq
    └──► Local / hosted ML models
```

The AI service does **not** call the backend and does **not** access the application database directly.

## 5. Run the tests

Run:

```bash
pytest
```

Make sure the required dependencies and environment variables are configured before running integration tests.

## Security and architecture notes

### Aadhaar and identity documents

Never send a raw Aadhaar or other identity-card image to an AI model through this service.

Aadhaar OCR is performed **on-device in the frontend**. The original identity document does not pass through the AI service.

### Model availability

If a voice or vision request starts failing with an unfamiliar provider error, first verify that the configured model still exists.

For example, Gemini and Groq model identifiers can change or become deprecated. A provider-side model change can therefore look like an application error even when the service logic itself has not changed.

### Critical consensus

`call_critical_consensus()` intentionally uses a maximum of **two providers**.

This limit is deliberate and should not be increased without changing the resource and reliability design.

## AI autonomy boundary

The AI service is an **advisory layer**.

It can:

- Generate drafts
- Produce suggestions
- Classify inputs
- Transcribe voice
- Analyze images through the configured ML pipeline
- Provide conversational responses

It cannot independently:

- Write directly to the application database
- Modify application records
- Bypass the Spring Boot backend
- Communicate directly with the frontend

The backend remains responsible for application-data writes and the final execution of application actions.