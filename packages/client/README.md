# packages/client

Vue 3 + Vite frontend: a Finland-only map of gas stations and restaurants, with route planning and route-based filtering.

## How it works

- **Map** ([MapView.vue](src/components/MapView.vue)) — Leaflet + OpenStreetMap tiles, bounded to Finland. Points of interest are only fetched and shown once a route exists, filtered to a corridor around it (`POST /api/pois/along-route` on the server).
- **Routing** ([RoutePlanner.vue](src/components/RoutePlanner.vue)) — start/end address fields plus optional "pass by" stops. Addresses are geocoded via Nominatim (OpenStreetMap), then routed via OSRM's Trip API, which finds the shortest path through all stops (reordering pass-by stops as needed) rather than forcing the order they were typed.
- **Filters** ([RouteFilters.vue](src/components/RouteFilters.vue)) — radius slider (default 0.5 km) and checkboxes for restaurants / gasoline / electric charging / restaurant-only stations, all applied server-side against the route corridor.
- **API/service layers** — [api/poi.api.ts](src/api/poi.api.ts) talks to the `@poi/server` API; [services/geocoding.service.ts](src/services/geocoding.service.ts) and [services/routing.service.ts](src/services/routing.service.ts) talk to Nominatim/OSRM directly from the browser. All are free, no-API-key public services — fine for development, not for production traffic.

## Usage

```bash
pnpm dev      # dev server at http://localhost:5173, proxies /api to the server on :3000
pnpm build    # type-check + production build to dist/
pnpm preview  # serve the production build locally
```

## Docker

`Dockerfile` builds the whole pnpm workspace (needed for the `@poi/shared` dependency) and serves the production build with Vite's `vite preview`. `docker-compose.yml` runs that image with Traefik routing labels attached, expecting an external Traefik instance on a `traefik` Docker network — see the compose file for the exact setup steps.
