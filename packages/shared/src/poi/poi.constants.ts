import type { PoiType } from "./poi.types.js";

export const POI_TYPES = ["gas_station", "restaurant"] as const satisfies readonly PoiType[];
