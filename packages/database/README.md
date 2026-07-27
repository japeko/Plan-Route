# packages/database

MongoDB for the points-of-interest data, plus the job that populates it from OpenStreetMap.

```
database/
├── docker-compose.yml   # mongo (persistent volume, healthcheck) + importer (one-shot)
└── script/               # Python import job
    ├── Dockerfile
    ├── import_finland_pois.py
    ├── requirements.txt
    └── .env.example
```

## How it works

`docker compose up` starts two services:

1. **`mongo`** — a `mongo:7` container storing data in a named volume (`poi-mongo-data`), so it survives container restarts/removal. Exposed on the host at `localhost:27017`, matching `MONGODB_URI` in `packages/server/.env`. Has a healthcheck (`mongosh` ping).
2. **`importer`** — builds from `script/Dockerfile`, installs the script's Python dependencies, and waits for `mongo` to report healthy before running `import_finland_pois.py` once against `mongo:27017` over the compose network. It fetches Finnish gas stations, EV charging points, and restaurants from the Overpass API (OpenStreetMap), merges co-located fuel/charging points into single stations, and upserts everything into the `pointsOfInterest` collection keyed by OSM id — safe to re-run, it updates existing records instead of duplicating them. Exits with code 0 when done; it isn't a long-running service.

## Usage

Start the database (and populate it on first run):

```bash
cd packages/database
docker compose up -d
```

Re-run the import later (e.g. to pick up new/changed OSM data) without touching Mongo:

```bash
docker compose up --build importer
```

Start just the database without importing:

```bash
docker compose up -d mongo
```

Stop everything (data is preserved in the volume):

```bash
docker compose down
```

Stop and wipe all imported data:

```bash
docker compose down -v
```
