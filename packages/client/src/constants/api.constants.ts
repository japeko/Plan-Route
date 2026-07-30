export const POI_API_BASE_URL = "/api/pois";
export const CONSTRUCTION_ZONE_API_BASE_URL = "/api/construction-zones";
// Fintraffic's free, official, government roadworks feed for Finland —
// no API key required. See https://www.digitraffic.fi/en/road-traffic/.
export const DIGITRAFFIC_ROADWORKS_URL = "https://tie.digitraffic.fi/api/traffic-message/v2/roadworks";

// Public demo/community services — no API key required, but rate-limited
// and intended for light/dev use. Swap for a self-hosted or paid provider
// (Mapbox, HERE, OpenRouteService) before any production traffic.
export const NOMINATIM_SEARCH_URL = "https://nominatim.openstreetmap.org/search";
export const NOMINATIM_REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
// The Trip service (not Route) solves for the shortest path visiting all
// stops with a fixed start/end, reordering pass-by stops as needed —
// Route would instead force them in whatever order they were typed,
// which can produce a much longer trip with unnecessary backtracking.
export const OSRM_TRIP_BASE_URL = "https://router.project-osrm.org/trip/v1/driving";
// Plain Route (not Trip) — the only one of the two that supports
// `alternatives`, used for a direct start->end trip with no via stops.
export const OSRM_ROUTE_BASE_URL = "https://router.project-osrm.org/route/v1/driving";

export const FINLAND_COUNTRY_CODE = "fi";
