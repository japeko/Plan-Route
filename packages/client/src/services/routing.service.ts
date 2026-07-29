import type { LatLngTuple } from "leaflet";
import { OSRM_TRIP_BASE_URL } from "@/constants/api.constants";
import type { NavigationLanguage, NavigationStep } from "@/types/route.types";

const NAVIGATION_LANGUAGES: NavigationLanguage[] = ["en", "fi", "sv"];

export class RoutingError extends Error {}

interface OsrmManeuver {
  type: string;
  modifier?: string;
  location: [number, number];
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

const TURN_PHRASES: Record<NavigationLanguage, Record<string, string>> = {
  en: {
    uturn: "Make a U-turn",
    "sharp right": "Turn sharp right",
    right: "Turn right",
    "slight right": "Turn slightly right",
    straight: "Continue straight",
    "slight left": "Turn slightly left",
    left: "Turn left",
    "sharp left": "Turn sharp left",
  },
  fi: {
    uturn: "Tee U-käännös",
    "sharp right": "Käänny jyrkästi oikealle",
    right: "Käänny oikealle",
    "slight right": "Käänny loivasti oikealle",
    straight: "Jatka suoraan",
    "slight left": "Käänny loivasti vasemmalle",
    left: "Käänny vasemmalle",
    "sharp left": "Käänny jyrkästi vasemmalle",
  },
  sv: {
    uturn: "Gör en U-sväng",
    "sharp right": "Sväng skarpt höger",
    right: "Sväng höger",
    "slight right": "Sväng lätt höger",
    straight: "Fortsätt rakt fram",
    "slight left": "Sväng lätt vänster",
    left: "Sväng vänster",
    "sharp left": "Sväng skarpt vänster",
  },
};

// Arrow glyphs for the on-screen banner, keyed the same way as
// TURN_PHRASES — used instead of spelling out the turn, since a phone
// screen rarely has room for both the instruction and the road name.
const TURN_ARROWS: Record<string, string> = {
  uturn: "↩",
  "sharp right": "↘",
  right: "↱",
  "slight right": "↗",
  straight: "↑",
  "slight left": "↖",
  left: "↰",
  "sharp left": "↙",
};

function describeStep(step: OsrmStep, lang: NavigationLanguage): string {
  const road = step.name || (lang === "en" ? "the road" : lang === "fi" ? "tielle" : "vägen");
  const { type, modifier } = step.maneuver;
  const turnPhrase = (modifier && TURN_PHRASES[lang][modifier]) || TURN_PHRASES[lang].straight;

  switch (lang) {
    case "fi":
      switch (type) {
        case "depart":
          return `Aja tielle ${road}`;
        case "arrive":
          return "Olet perillä";
        case "merge":
          return `Liity tielle ${road}`;
        case "on ramp":
          return `Aja rampille tielle ${road}`;
        case "off ramp":
          return `Poistu rampilta tielle ${road}`;
        case "fork":
        case "end of road":
        case "turn":
          return `${turnPhrase} tielle ${road}`;
        case "roundabout":
        case "rotary":
        case "roundabout turn":
          return `Jatka liikenneympyrässä tielle ${road}`;
        case "exit roundabout":
        case "exit rotary":
          return `Poistu liikenneympyrästä tielle ${road}`;
        default:
          return `Jatka tietä ${road}`;
      }
    case "sv":
      switch (type) {
        case "depart":
          return `Kör ut på ${road}`;
        case "arrive":
          return "Du har anlänt";
        case "merge":
          return `Anslut till ${road}`;
        case "on ramp":
          return `Kör upp på påfarten mot ${road}`;
        case "off ramp":
          return `Kör av mot ${road}`;
        case "fork":
        case "end of road":
        case "turn":
          return `${turnPhrase} in på ${road}`;
        case "roundabout":
        case "rotary":
        case "roundabout turn":
          return `Fortsätt i rondellen mot ${road}`;
        case "exit roundabout":
        case "exit rotary":
          return `Kör ut ur rondellen mot ${road}`;
        default:
          return `Fortsätt på ${road}`;
      }
    default:
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
          return `${turnPhrase} onto ${road}`;
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
}

function describeStepAllLanguages(step: OsrmStep): Record<NavigationLanguage, string> {
  return Object.fromEntries(NAVIGATION_LANGUAGES.map((lang) => [lang, describeStep(step, lang)])) as Record<
    NavigationLanguage,
    string
  >;
}

function maneuverArrow(maneuver: OsrmManeuver): string {
  const { type, modifier } = maneuver;

  switch (type) {
    case "arrive":
      return "🏁";
    case "roundabout":
    case "rotary":
    case "roundabout turn":
    case "exit roundabout":
    case "exit rotary":
      return "⟳";
    default:
      return (modifier && TURN_ARROWS[modifier]) || "↑";
  }
}

function roadLabel(step: OsrmStep): string {
  if (step.maneuver.type === "arrive") {
    return "Destination";
  }
  return step.name || "the road";
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
      instructions: describeStepAllLanguages(step),
      arrow: maneuverArrow(step.maneuver),
      roadLabel: roadLabel(step),
      distanceMeters: step.distance,
      durationSeconds: step.duration,
      location: [step.maneuver.location[1], step.maneuver.location[0]] as LatLngTuple,
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
