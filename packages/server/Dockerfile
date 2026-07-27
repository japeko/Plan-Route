# Build context must be the repo root (not packages/server), since this is
# a pnpm workspace and the server depends on the @poi/shared package:
#   docker build -f packages/server/Dockerfile .
# (packages/server/docker-compose.yml already points at the repo root.)

FROM node:22-slim AS build
RUN corepack enable
WORKDIR /repo

COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm --filter @poi/shared build
RUN pnpm --filter @poi/server build

WORKDIR /repo/packages/server
EXPOSE 3000
CMD ["node", "dist/index.js"]
