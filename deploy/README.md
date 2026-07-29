# Deploying to the cloud

The client and server ship as Docker images ("distribution packets") built from
`packages/client/Dockerfile` and `packages/server/Dockerfile`, pushed to GitHub
Container Registry (ghcr.io), and pulled down on whatever cloud host runs them.
Mongo runs on the same host as its own container (see `mongo` in
`docker-compose.yml`), with its data in a named volume, and mongo-express
gives you a web UI to browse it at `https://<DOMAIN>/db/` — the host never
needs this source repo, only this `deploy/` folder.

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
  — Mongo itself has no published port, so it's reachable only from the
  `server` and `mongo-express` containers over the compose network, never
  directly from the internet. mongo-express *is* internet-facing (behind
  Traefik at `/db/`), which is why Mongo now requires auth
  (`MONGO_ROOT_USERNAME`/`MONGO_ROOT_PASSWORD`) and mongo-express itself is
  behind its own basic-auth login (`MONGO_EXPRESS_USERNAME`/`PASSWORD`) —
  set real values for all four in `.env`, not the placeholders.
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
# edit .env: GITHUB_OWNER, DOMAIN, ACME_EMAIL, MONGO_ROOT_USERNAME,
# MONGO_ROOT_PASSWORD, MONGO_EXPRESS_USERNAME, MONGO_EXPRESS_PASSWORD
docker login ghcr.io -u <your-github-username>   # paste a read:packages token
docker compose pull
docker compose up -d
```

Traefik will request a TLS certificate for `DOMAIN` automatically on first
request. The client is served at `https://<DOMAIN>/`, the API at
`https://<DOMAIN>/api/*`, and the Mongo admin UI at `https://<DOMAIN>/db/`
(prompts for `MONGO_EXPRESS_USERNAME`/`PASSWORD`).

## 4. Updating the client/server images

For an ordinary code change (nothing in `deploy/docker-compose.yml` or
`.env` needs to change), the best-practice update only touches the two
services whose images actually changed — `mongo`, `mongo-express`, and
`traefik` are left running untouched:

On your dev machine:

```bash
GITHUB_OWNER=your-github-username pnpm release
```

This rebuilds and pushes both `poi-client` and `poi-server`, tagged with
the current git commit hash and `latest`. See `scripts/release-images.sh`
for details.

On the host:

```bash
cd ~/poi-app
docker compose pull client server
docker compose up -d client server
```

Scoping `pull`/`up -d` to just `client server` avoids recreating the other
containers for no reason, and keeps the brief restart to exactly the two
that changed. (If you *did* change `docker-compose.yml` or `.env` — a new
env var, a new service — run the plain `docker compose pull && docker
compose up -d` from step 3 instead, so every affected service picks it up.)

Roll back to a specific previous build by setting `IMAGE_TAG` in `.env` to
the git commit hash printed by `pnpm release` (older tags are also listed
under your GitHub account's Packages tab), then repeat the `pull`/`up -d`
above.

Verify the update landed:

```bash
docker compose logs client --tail=20
docker compose logs server --tail=20
```

and hard-refresh `https://<DOMAIN>/` in the browser to bypass any cached
client bundle.

## 5. Populating Mongo on the host

This stack's `mongo` container starts empty. Easiest: browse to
`https://<DOMAIN>/db/` and add documents by hand for a quick check, or run
the OSM importer from `packages/database/script` against it once. Since
`mongo` isn't published on a host port, copy the importer image/script onto
the host and run it there against `mongo:27017` on the compose network:

```bash
# on the host, inside ~/poi-app
docker build -t poi-importer packages/database/script   # after copying that folder over too
docker run --rm --network poi-app_default \
  -e MONGODB_URI="mongodb://${MONGO_ROOT_USERNAME}:${MONGO_ROOT_PASSWORD}@mongo:27017/poi?authSource=admin" \
  poi-importer
```

(`poi-app_default` is the compose project's default network — check the
actual name with `docker network ls` if your `deploy/` folder is named
differently on the host.)

## 6. Backups

Since Mongo's data now lives only on this VM (in the `mongo-data` named
volume), back it up regularly — there's no managed fallback like Atlas
provides:

```bash
# on the host
docker exec poi-mongo mongodump \
  -u "$MONGO_ROOT_USERNAME" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase=admin \
  --archive=/data/db/backup.archive --db=poi
docker cp poi-mongo:/data/db/backup.archive ./poi-backup-$(date +%F).archive
```

Copy that archive off the VM (e.g. to S3/Backblaze or your dev machine), or
just enable Hetzner's built-in volume/server snapshots for a coarser but
lower-effort safety net.
