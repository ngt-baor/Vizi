FROM python:3.13-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    U2NET_HOME=/models

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .
RUN useradd --create-home --shell /usr/sbin/nologin vizi-rembg \
    && mkdir -p /models \
    && chown -R vizi-rembg:vizi-rembg /app /models

USER vizi-rembg
EXPOSE 7000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "7000"]
