FROM python:3.11-slim

# Install system dependencies required for Azure Cognitive Services Speech SDK,
# OpenCV, MediaPipe, and PyTorch / ONNX C++ runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libssl-dev \
    ca-certificates \
    libasound2 \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    wget \
    curl \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Download MediaPipe Pose Landmarker lite model bundle into container root
RUN wget -q -O /app/pose_landmarker_lite.task \
    https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task || true

# Copy requirements first to leverage Docker cache
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application
COPY . .

# Expose the FastAPI port
EXPOSE 80

# Run the application
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "80"]

