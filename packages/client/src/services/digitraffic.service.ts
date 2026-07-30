import { DIGITRAFFIC_ROADWORKS_URL } from "@/constants/api.constants";
import type { ConstructionZone } from "@/types/route.types";

export class DigitrafficError extends Error {}

export interface BoundingBox {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
}

interface DigitrafficAnnouncement {
  language: string;
  title?: string;
  location?: { description?: string };
}

interface DigitrafficFeature {
  geometry?: { type: string; coordinates: [number, number] };
  properties?: {
    situationId?: string;
    announcements?: DigitrafficAnnouncement[];
  };
}

interface DigitrafficFeatureCollection {
  features?: DigitrafficFeature[];
}

// Prefer an English announcement (the app's default UI language) over
// whatever the feed happens to list first, but Finnish government feeds
// don't guarantee an English entry exists — Finnish is always present.
function pickAnnouncement(
  announcements: DigitrafficAnnouncement[] | undefined,
): DigitrafficAnnouncement | undefined {
  if (!announcements || announcements.length === 0) {
    return undefined;
  }
  return announcements.find((announcement) => announcement.language === "en") ?? announcements[0];
}

function toConstructionZone(feature: DigitrafficFeature): ConstructionZone | null {
  const geometry = feature.geometry;
  if (!geometry || geometry.type !== "Point" || !geometry.coordinates) {
    return null;
  }

  const [lng, lat] = geometry.coordinates;
  const announcement = pickAnnouncement(feature.properties?.announcements);
  const description = announcement?.location?.description || announcement?.title || "Road work";

  return {
    id: feature.properties?.situationId ?? `official-${lat}-${lng}`,
    position: [lat, lng],
    description,
    source: "official",
  };
}

// Fetches Fintraffic's live, official roadworks within a bounding box —
// a coarser filter than the route-corridor buffer used for POIs, but
// roadwork markers are sparse enough nationally that this is a reasonable
// simplification rather than needing server-side geospatial filtering.
export async function fetchOfficialRoadworks(bounds: BoundingBox): Promise<ConstructionZone[]> {
  const params = new URLSearchParams({
    xMin: String(bounds.minLng),
    xMax: String(bounds.maxLng),
    yMin: String(bounds.minLat),
    yMax: String(bounds.maxLat),
  });

  // Digitraffic asks API consumers to self-identify via this header (not
  // strictly enforced, but good etiquette — mirrors the Overpass API
  // User-Agent requirement used elsewhere in this app).
  const response = await fetch(`${DIGITRAFFIC_ROADWORKS_URL}?${params.toString()}`, {
    headers: { "Digitraffic-User": "plan-route-app" },
  });

  if (!response.ok) {
    throw new DigitrafficError(`Failed to fetch official roadworks (status ${response.status}).`);
  }

  const data = (await response.json()) as DigitrafficFeatureCollection;

  return (data.features ?? [])
    .map(toConstructionZone)
    .filter((zone): zone is ConstructionZone => zone !== null);
}
