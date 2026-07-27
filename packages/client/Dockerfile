# Build context must be the repo root (not packages/client), since this is
# a pnpm workspace and the client depends on the @poi/shared package:
#   docker build -f packages/client/Dockerfile .
# (packages/client/docker-compose.yml already points at the repo root.)

FROM node:22-slim AS build
RUN corepack enable
WORKDIR /repo

COPY . .
RUN pnpm install --frozen-lockfile
RUN pnpm --filter @poi/shared build
RUN pnpm --filter @poi/client build

# Serve the production build with Vite's own preview server rather than a
# separate nginx image — Traefik (see docker-compose.yml) sits in front and
# handles routing, so the app container just needs to serve the built app.
WORKDIR /repo/packages/client
EXPOSE 4173
CMD ["pnpm", "exec", "vite", "preview", "--host", "0.0.0.0", "--port", "4173"]
