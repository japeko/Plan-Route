# Deploying to the cloud

The client and server ship as Docker images ("distribution packets") built from
`packages/client/Dockerfile` and `packages/server/Dockerfile`, pushed to GitHub
Container Registry (ghcr.io), and pulled down on whatever cloud host runs them.
The host never needs this source repo — only this `deploy/` folder.

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
  Docker Compose plugin installed, with ports 80 and 443 open.
- A domain name pointed (A record) at the VM's IP address — required for
  Traefik to obtain a Let's Encrypt TLS certificate.
- A reachable MongoDB — either point at MongoDB Atlas's free tier, or run
  `packages/database`'s compose stack on the same VM and use its connection
  string.

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
