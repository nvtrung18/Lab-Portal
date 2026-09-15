FROM python:3.12-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY ai-service/requirements-api.txt ai-service/requirements-runtime-t4.txt /tmp/ai-requirements/
RUN python -m pip install --upgrade pip==26.2 \
    && python -m pip install --timeout 180 --retries 5 \
      -r /tmp/ai-requirements/requirements-runtime-t4.txt \
    && rm -rf /tmp/ai-requirements

RUN groupadd --system --gid 10001 ai-service \
    && useradd --system --uid 10001 --gid ai-service --home-dir /app ai-service

COPY --chown=ai-service:ai-service ai-service/app /app/app
COPY --chown=ai-service:ai-service ai-service/config /app/config
COPY --chown=ai-service:ai-service ai-service/scripts /app/scripts

USER ai-service

EXPOSE 8090

HEALTHCHECK --interval=30s --timeout=10s --start-period=10m --retries=3 \
  CMD ["python", "-c", "import os,urllib.request; urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:8090/ready',headers={'X-Internal-Service-Token':os.environ['AI_INTERNAL_SERVICE_TOKEN']}),timeout=8)"]

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--no-access-log"]
