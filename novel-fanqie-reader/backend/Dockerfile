FROM python:3.13-slim

WORKDIR /app

RUN apt-get update \
  && apt-get install -y --no-install-recommends \
  gcc \
  libmariadb-dev-compat libmariadb-dev pkg-config python3-dev libssl-dev openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt
