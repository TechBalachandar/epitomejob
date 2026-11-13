# EpitomeJob web — W4 (contract §2b).
# node:22-alpine verified on Docker Hub 2026-11-12 (exists, 2026-11-12).
# corepack pnpm 10 matches package.json ("packageManager": "pnpm@10.0.0")
# and pnpm-lock.yaml (lockfileVersion 9.0, pnpm 10 format).
# curl is installed: node:22-alpine ships no curl, and the compose
# healthcheck probes http://localhost:3000/login with curl.
# No `output: standalone` (not configured in next.config.ts);
# plain `pnpm build && pnpm start` (next start, port 3000).

FROM node:22-alpine
RUN apk add --no-cache curl
RUN corepack enable && corepack prepare pnpm@10.0.0 --activate
WORKDIR /app
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . ./
RUN pnpm build
EXPOSE 3000
CMD ["pnpm", "start"]
