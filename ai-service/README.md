# AshaAI AI service

The FastAPI service is an internal dependency of Spring Boot; it is not a frontend API.

## Run locally

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn main:app --host 127.0.0.1 --port 8000
```

Check `http://127.0.0.1:8000/health`; it returns `{"status":"UP"}`.
