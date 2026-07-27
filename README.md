# Plan-Route

A points-of-interest / route planning app: Vue 3 client, Express API server, and a
MongoDB-backed OSM import job, managed as a pnpm workspace.

## Packages

- `packages/client` — Vue 3 frontend (Vite)
- `packages/server` — Express API
- `packages/shared` — Shared types, DTOs, and utilities used by both client and server
- `packages/database` — MongoDB via `docker-compose.yml`, plus a Python OSM import job in `script/`

## Development

```bash
pnpm install
pnpm dev
```

## Production build

```bash
pnpm build
```

Runs each package's build (`vue-tsc -b && vite build` for the client, `tsc -b`
for the server, both depending on `@poi/shared` being built first).

## Distribution builds (Docker)

The client and server ship as **separate Docker images**, each built from its
own Dockerfile at the repo root's build context (required because both depend
on the `@poi/shared` workspace package):

- `packages/client/Dockerfile` — builds the client and serves it with Vite's
  preview server on port `4173`
- `packages/server/Dockerfile` — builds the server and runs it with Node on
  port `3000`

### Build the images locally

From the repo root (not from inside `packages/client` or `packages/server` —
the build context must be the root so the Dockerfile can see `@poi/shared`):

```bash
docker build -f packages/client/Dockerfile -t poi-client .
docker build -f packages/server/Dockerfile -t poi-server .
```

### Run them

```bash
docker run -d -p 4173:4173 --name poi-client poi-client

docker run -d -p 3000:3000 \
  -e MONGODB_URI="mongodb://host.docker.internal:27017/poi" \
  -e CLIENT_ORIGIN="http://localhost:4173" \
  --name poi-server poi-server
```

The server needs a reachable MongoDB — either run `packages/database`'s
compose stack locally (`docker compose up` inside `packages/database`) or
point `MONGODB_URI` at MongoDB Atlas.

### Build and publish for deployment

To build both images, tag them with the current git commit, and push to
GitHub Container Registry (ghcr.io):

```bash
GITHUB_OWNER=your-github-username pnpm release
```

See [`scripts/release-images.sh`](scripts/release-images.sh) for what this
does, and [`deploy/README.md`](deploy/README.md) for the full guide to
deploying the published images to a cloud host behind Traefik with TLS.
