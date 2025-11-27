FROM ubuntu:noble-20250127

RUN apt update && apt install -y curl nodejs git unzip

RUN curl -fsSL https://bun.sh/install | bash && \
  mv /root/.bun/bin/bun /usr/local/bin/bun
