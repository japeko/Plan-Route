# Deploying to the cloud

The client and server ship as Docker images ("distribution packets") built from
`packages/client/Dockerfile` and `packages/server/Dockerfile`, pushed to GitHub
Container Registry (ghcr.io), and pulled down on whatever cloud host runs them.
Mongo runs on the same host as its own container (see `mongo` in
`docker-compose.yml`), with its data in a named volume — the host never needs
this source repo, only this `deploy/` folder.

## 1. One-time setup

- A GitHub Personal Access Token (classic) with the `write:packages` scope
  (for pushing) — create one at https://github.com/settings/tokens. A token
  with `read:packages` is enough for the cloud host to only pull.
- Log in to the registry wherever you'll run `pnpm release`:
  ```bash
  docker login ghcr.io -u <your-github-username>
  # paste the token as the password
  ```
- A cloud VM (DigitalOcean, Hetzner, AWS Lightsail, etc.) with Docker and the
  Docker Compose plugin installed. Only ports 22, 80, and 443 need to be open
  — Mongo has no published port, so it's reachable only from the `server`
  container over the compose network, not from the internet.
- A domain name pointed (A record) at the VM's IP address — required for
  Traefik to obtain a Let's Encrypt TLS certificate.

## 2. Build and publish the images

From the repo root, on your dev machine (or CI):

```bash
GITHUB_OWNER=your-github-username pnpm release
```

This builds `packages/client/Dockerfile` and `packages/server/Dockerfile` and
pushes both to `ghcr.io/<owner>/poi-client` and `ghcr.io/<owner>/poi-server`,
tagged with the current git commit hash and `latest`. See
`scripts/release-images.sh` for details.

## 3. Deploy

Copy just this `deploy/` folder to the cloud host (scp, rsync, or a sparse
git checkout — the rest of the repo isn't needed there):

```bash
scp -r deploy your-user@your-host:~/poi-app
```

On the host:

```bash
cd ~/poi-app
cp .env.example .env
# edit .env: GITHUB_OWNER, DOMAIN, ACME_EMAIL, MONGODB_URI
docker login ghcr.io -u <your-github-username>   # paste a read:packages token
docker compose pull
docker compose up -d
```

Traefik will request a TLS certificate for `DOMAIN` automatically on first
request. The client is served at `https://<DOMAIN>/`, the API at
`https://<DOMAIN>/api/*`.

## 4. Shipping an update

```bash
GITHUB_OWNER=your-github-username pnpm release   # on your dev machine
```

Then on the host:

```bash
docker compose pull
docker compose up -d
```

To deploy a specific past build instead of `latest`, set `IMAGE_TAG` in `.env`
to the git commit hash printed by `pnpm release`.

## 5. Populating Mongo on the host

This stack's `mongo` container starts empty. Run the OSM importer from
`packages/database/script` against it once, from your dev machine (or
anywhere with network access to the host):

```bash
docker build -t poi-importer packages/database/script
docker run --rm \
  -e MONGODB_URI="mongodb://<your-user>@<hetzner-ip>:27017/poi" \
  poi-importer
```

Since `mongo` isn't published on a host port in `deploy/docker-compose.yml`,
either temporarily add `ports: ["27017:27017"]` to run this remotely, or
simpler — copy the importer image/script onto the host and run it there
against `mongo:27017` on the compose network directly, then remove the
temporary port mapping.

## 6. Backups

Since Mongo's data now lives only on this VM (in the `mongo-data` named
volume), back it up regularly — there's no managed fallback like Atlas
provides:

```bash
# on the host
docker exec poi-mongo mongodump --archive=/data/db/backup.archive --db=poi
docker cp poi-mongo:/data/db/backup.archive ./poi-backup-$(date +%F).archive
```

Copy that archive off the VM (e.g. to S3/Backblaze or your dev machine), or
just enable Hetzner's built-in volume/server snapshots for a coarser but
lower-effort safety net.
