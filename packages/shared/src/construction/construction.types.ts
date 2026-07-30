import type { GeoPoint } from "../poi/poi.types.js";

// User-reported only — official Digitraffic roadworks are fetched
// directly by the client from Fintraffic's public API and never stored
// here, so this collection only ever holds crowd-sourced reports.
export interface ConstructionZoneReport {
  id: string;
  location: GeoPoint;
  createdAt: string;
}
