// Reports fetched within this distance of the route — kept generous
// relative to the gas-station/restaurant radius since construction
// reports are sparse and there's no per-user UI control for it (v1: no
// filter toggle, always shown alongside a planned route).
export const DEFAULT_CONSTRUCTION_ZONE_RADIUS_METERS = 2000;

// User reports auto-expire after this long — there's no verification or
// moderation, so stale/no-longer-true reports need to age out on their
// own rather than accumulate forever. Enforced by a MongoDB TTL index
// (see the server model), not application code.
export const CONSTRUCTION_ZONE_REPORT_TTL_SECONDS = 12 * 60 * 60;
