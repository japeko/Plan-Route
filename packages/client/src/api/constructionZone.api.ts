import type {
  ConstructionZoneReport,
  ConstructionZonesAlongRouteRequestDto,
  CreateConstructionZoneReportDto,
  GeoLineString,
} from "@poi/shared";
import { DEFAULT_CONSTRUCTION_ZONE_RADIUS_METERS } from "@poi/shared";
import { CONSTRUCTION_ZONE_API_BASE_URL } from "@/constants/api.constants";

export class ConstructionZoneApiError extends Error {}

// The server sends a specific, meaningful message (e.g. "A road work
// report already exists near this location") as { message } — prefer
// that over a generic status-code string whenever it's present.
async function errorMessageFrom(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string };
    return body.message ?? fallback;
  } catch {
    return fallback;
  }
}

export async function fetchConstructionZoneReportsAlongRoute(
  route: GeoLineString,
): Promise<ConstructionZoneReport[]> {
  const body: ConstructionZonesAlongRouteRequestDto = {
    route,
    radiusMeters: DEFAULT_CONSTRUCTION_ZONE_RADIUS_METERS,
  };

  const response = await fetch(`${CONSTRUCTION_ZONE_API_BASE_URL}/along-route`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new ConstructionZoneApiError(
      await errorMessageFrom(response, `Failed to fetch construction zone reports (status ${response.status}).`),
    );
  }

  return (await response.json()) as ConstructionZoneReport[];
}

export async function reportConstructionZone(dto: CreateConstructionZoneReportDto): Promise<ConstructionZoneReport> {
  const response = await fetch(CONSTRUCTION_ZONE_API_BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dto),
  });

  if (!response.ok) {
    throw new ConstructionZoneApiError(
      await errorMessageFrom(response, `Failed to report construction zone (status ${response.status}).`),
    );
  }

  return (await response.json()) as ConstructionZoneReport;
}

export async function removeConstructionZoneReport(id: string): Promise<void> {
  const response = await fetch(`${CONSTRUCTION_ZONE_API_BASE_URL}/${id}`, { method: "DELETE" });

  if (!response.ok) {
    throw new ConstructionZoneApiError(
      await errorMessageFrom(response, `Failed to remove construction zone report (status ${response.status}).`),
    );
  }
}
