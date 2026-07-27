import type { LatLngTuple } from "leaflet";
import { OSRM_TRIP_BASE_URL } from "@/constants/api.constants";
import type { NavigationStep } from "@/types/route.types";

export class RoutingError extends Error {}

interface OsrmManeuver {
  type: string;
  modifier?: string;
}

interface OsrmStep {
  maneuver: OsrmManeuver;
  name: string;
  distance: number;
  duration: number;
}

interface OsrmLeg {
  steps: OsrmStep[];
}

interface OsrmTrip {
  geometry: { coordinates: [number, number][] };
  legs: OsrmLeg[];
  distance: number;
  duration: number;
}

interface OsrmWaypoint {
  waypoint_index: number;
}

interface OsrmTripResponse {
  code: string;
  trips: OsrmTrip[];
  waypoints: OsrmWaypoint[];
}

export interface RoadTrip {
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
  // Original-array indices of `stops`, reordered into the order the trip
  // actually visits them (start and end stay fixed; only via stops move).
  visitOrder: number[];
  // One step list per leg (leg N runs from stop N to stop N+1 in visit
  // order), so the caller can label each leg's arrival step with the
  // actual stop name rather than OSRM's generic "arrive".
  legs: NavigationStep[][];
}

const TURN_PHRASES: Record<string, string> = {
  uturn: "Make a U-turn",
  "sharp right": "Turn sharp right",
  right: "Turn right",
  "slight right": "Turn slightly right",
  straight: "Continue straight",
  "slight left": "Turn slightly left",
  left: "Turn left",
  "sharp left": "Turn sharp left",
};

function describeStep(step: OsrmStep): string {
  const road = step.name || "the road";
  const { type, modifier } = step.maneuver;

  switch (type) {
    case "depart":
      return `Head out on ${road}`;
    case "arrive":
      return "Arrive at your destination";
    case "merge":
      return `Merge onto ${road}`;
    case "on ramp":
      return `Take the ramp onto ${road}`;
    case "off ramp":
      return `Take the exit onto ${road}`;
    case "fork":
    case "end of road":
    case "turn":
      return `${(modifier && TURN_PHRASES[modifier]) || "Turn"} onto ${road}`;
    case "roundabout":
    case "rotary":
    case "roundabout turn":
      return `At the roundabout, continue onto ${road}`;
    case "exit roundabout":
    case "exit rotary":
      return `Exit the roundabout onto ${road}`;
    default:
      return `Continue onto ${road}`;
  }
}

export async function fetchRoadTrip(stops: LatLngTuple[]): Promise<RoadTrip> {
  if (stops.length < 2) {
    throw new RoutingError("At least a start and an end location are required.");
  }

  const coordinates = stops.map(([lat, lng]) => `${lng},${lat}`).join(";");
  const params = new URLSearchParams({
    overview: "full",
    geometries: "geojson",
    steps: "true",
    source: "first",
    destination: "last",
    roundtrip: "false",
  });

  const response = await fetch(`${OSRM_TRIP_BASE_URL}/${coordinates}?${params.toString()}`);

  if (!response.ok) {
    throw new RoutingError(`Routing request failed (status ${response.status}).`);
  }

  const data = (await response.json()) as OsrmTripResponse;
  const trip = data.trips[0];

  if (data.code !== "Ok" || !trip) {
    throw new RoutingError("No road route could be found through the given locations.");
  }

  const visitOrder = data.waypoints
    .map((waypoint, originalIndex) => ({ originalIndex, visitPosition: waypoint.waypoint_index }))
    .sort((a, b) => a.visitPosition - b.visitPosition)
    .map((entry) => entry.originalIndex);

  const legs = trip.legs.map((leg) =>
    leg.steps.map((step) => ({
      instruction: describeStep(step),
      distanceMeters: step.distance,
      durationSeconds: step.duration,
    })),
  );

  return {
    path: trip.geometry.coordinates.map(([lon, lat]) => [lat, lon]),
    distanceMeters: trip.distance,
    durationSeconds: trip.duration,
    visitOrder,
    legs,
  };
}
