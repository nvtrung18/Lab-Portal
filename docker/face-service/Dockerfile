FROM python:3.12-slim

WORKDIR /app

COPY face-service/requirements.txt /tmp/requirements.txt
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --retries 20 --timeout 120 -r /tmp/requirements.txt

COPY face-service/scripts/download_models.py /tmp/download_models.py
RUN FACE_MODEL_DIR=/models python /tmp/download_models.py

COPY face-service /app

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091"]
