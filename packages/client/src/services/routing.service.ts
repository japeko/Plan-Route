import type { LatLngTuple } from "leaflet";
import { OSRM_ROUTE_BASE_URL, OSRM_TRIP_BASE_URL } from "@/constants/api.constants";
import type { NavigationLanguage, NavigationStep } from "@/types/route.types";

const NAVIGATION_LANGUAGES: NavigationLanguage[] = ["en", "fi", "sv"];

export class RoutingError extends Error {}

interface OsrmManeuver {
  type: string;
  modifier?: string;
  location: [number, number];
  // Which exit to take, counting from 1, on roundabout/rotary maneuvers.
  exit?: number;
}

interface OsrmStep {
  maneuver: OsrmManeuver;
  name: string;
  // Populated by OSRM on many motorway ramps/junctions even when `name`
  // isn't — the signed destinations (e.g. "Turku, Pori") and the road's
  // reference number (e.g. "E4") respectively.
  destinations?: string;
  ref?: string;
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

// The plain Route service (as opposed to Trip) shares the same trip/leg/
// step shape, minus the waypoint-reordering fields — but it's the only
// one of the two that supports alternatives, which Trip's TSP-style
// solver doesn't.
interface OsrmRoute {
  geometry: { coordinates: [number, number][] };
  legs: OsrmLeg[];
  distance: number;
  duration: number;
}

interface OsrmRouteResponse {
  code: string;
  routes: OsrmRoute[];
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

// A single start->end option among possibly several — only available via
// the plain Route service (see fetchRouteAlternatives), which means only
// for a direct trip with no via stops to reorder.
export interface RouteAlternative {
  path: LatLngTuple[];
  distanceMeters: number;
  durationSeconds: number;
  steps: NavigationStep[];
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

// Ordinal words for roundabout exit counts ("2nd exit", "toinen
// poistumistie", "andra avfarten") — realistically only ever needs a
// handful of entries; anything beyond falls back to a bare number.
const ORDINAL_WORDS: Record<Exclude<NavigationLanguage, "en">, string[]> = {
  fi: ["ensimmäinen", "toinen", "kolmas", "neljäs", "viides", "kuudes", "seitsemäs", "kahdeksas"],
  sv: ["första", "andra", "tredje", "fjärde", "femte", "sjätte", "sjunde", "åttonde"],
};

function ordinal(n: number, lang: NavigationLanguage): string {
  if (lang === "en") {
    const lastTwoDigits = n % 100;
    if (lastTwoDigits >= 11 && lastTwoDigits <= 13) {
      return `${n}th`;
    }
    switch (n % 10) {
      case 1:
        return `${n}st`;
      case 2:
        return `${n}nd`;
      case 3:
        return `${n}rd`;
      default:
        return `${n}th`;
    }
  }
  return ORDINAL_WORDS[lang][n - 1] ?? `${n}.`;
}

// The best available label for where a maneuver leads: OSRM often leaves
// `name` empty on motorway ramps/junctions, but still provides the signed
// destinations ("Turku, Pori") or the road's reference number ("E4") —
// both far more useful than a generic "the road" filler when present.
function namedRoad(step: OsrmStep): string {
  return step.name || step.destinations || step.ref || "";
}

function describeStep(step: OsrmStep, lang: NavigationLanguage): string {
  const road = namedRoad(step);
  const { type, modifier } = step.maneuver;
  const turnPhrase = (modifier && TURN_PHRASES[lang][modifier]) || TURN_PHRASES[lang].straight;

  switch (lang) {
    case "fi": {
      // Each base phrase already ends in the case-inflected word for
      // "onto/towards the road" ("tielle"/"tietä") — that alone is a
      // complete, grammatical sentence when there's no name to append, so
      // road is appended only when there is one, instead of substituting
      // a generic filler word that would just repeat it.
      const withRoad = (base: string) => (road ? `${base} ${road}` : base);
      switch (type) {
        case "depart":
          return withRoad("Aja tielle");
        case "arrive":
          return "Olet perillä";
        case "merge":
          return withRoad("Liity tielle");
        case "on ramp":
          return withRoad("Aja rampille tielle");
        case "off ramp":
          return withRoad("Poistu rampilta tielle");
        case "fork":
        case "end of road":
        case "turn":
          return withRoad(`${turnPhrase} tielle`);
        case "roundabout":
        case "rotary":
        case "roundabout turn":
          return withRoad(
            step.maneuver.exit
              ? `Ota liikenneympyrästä ${ordinal(step.maneuver.exit, "fi")} poistumistie tielle`
              : "Jatka liikenneympyrässä tielle",
          );
        case "exit roundabout":
        case "exit rotary":
          return withRoad("Poistu liikenneympyrästä tielle");
        default:
          return withRoad("Jatka tietä");
      }
    }
    case "sv": {
      const withFallback = road || "vägen";
      switch (type) {
        case "depart":
          return `Kör ut på ${withFallback}`;
        case "arrive":
          return "Du har anlänt";
        case "merge":
          return `Anslut till ${withFallback}`;
        case "on ramp":
          return `Kör upp på påfarten mot ${withFallback}`;
        case "off ramp":
          return `Kör av mot ${withFallback}`;
        case "fork":
        case "end of road":
        case "turn":
          return `${turnPhrase} in på ${withFallback}`;
        case "roundabout":
        case "rotary":
        case "roundabout turn":
          return step.maneuver.exit
            ? `I rondellen, ta ${ordinal(step.maneuver.exit, "sv")} avfarten mot ${withFallback}`
            : `Fortsätt i rondellen mot ${withFallback}`;
        case "exit roundabout":
        case "exit rotary":
          return `Kör ut ur rondellen mot ${withFallback}`;
        default:
          return `Fortsätt på ${withFallback}`;
      }
    }
    default: {
      const withFallback = road || "the road";
      switch (type) {
        case "depart":
          return `Head out on ${withFallback}`;
        case "arrive":
          return "Arrive at your destination";
        case "merge":
          return `Merge onto ${withFallback}`;
        case "on ramp":
          return `Take the ramp onto ${withFallback}`;
        case "off ramp":
          return `Take the exit onto ${withFallback}`;
        case "fork":
        case "end of road":
        case "turn":
          return `${turnPhrase} onto ${withFallback}`;
        case "roundabout":
        case "rotary":
        case "roundabout turn":
          return step.maneuver.exit
            ? `At the roundabout, take the ${ordinal(step.maneuver.exit, "en")} exit onto ${withFallback}`
            : `At the roundabout, continue onto ${withFallback}`;
        case "exit roundabout":
        case "exit rotary":
          return `Exit the roundabout onto ${withFallback}`;
        default:
          return `Continue onto ${withFallback}`;
      }
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
  return namedRoad(step) || "the road";
}

function buildNavigationSteps(steps: OsrmStep[]): NavigationStep[] {
  return steps.map((step) => ({
    instructions: describeStepAllLanguages(step),
    arrow: maneuverArrow(step.maneuver),
    roadLabel: roadLabel(step),
    distanceMeters: step.distance,
    durationSeconds: step.duration,
    location: [step.maneuver.location[1], step.maneuver.location[0]] as LatLngTuple,
  }));
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

  const legs = trip.legs.map((leg) => buildNavigationSteps(leg.steps));

  return {
    path: trip.geometry.coordinates.map(([lon, lat]) => [lat, lon]),
    distanceMeters: trip.distance,
    durationSeconds: trip.duration,
    visitOrder,
    legs,
  };
}

// Alternative routes for a direct start->end trip — OSRM's Route service
// supports `alternatives`, unlike Trip's waypoint-reordering solver, but
// as a tradeoff can't reorder/optimize via stops, so this is only used
// when there are none. Even then, OSRM only returns more than one route
// when a genuinely distinct, not-too-much-longer alternative exists for
// that specific start/end pair — often it'll just be the one.
export async function fetchRouteAlternatives(start: LatLngTuple, end: LatLngTuple): Promise<RouteAlternative[]> {
  const coordinates = `${start[1]},${start[0]};${end[1]},${end[0]}`;
  const params = new URLSearchParams({
    overview: "full",
    geometries: "geojson",
    steps: "true",
    alternatives: "true",
  });

  const response = await fetch(`${OSRM_ROUTE_BASE_URL}/${coordinates}?${params.toString()}`);

  if (!response.ok) {
    throw new RoutingError(`Routing request failed (status ${response.status}).`);
  }

  const data = (await response.json()) as OsrmRouteResponse;

  if (data.code !== "Ok" || !data.routes || data.routes.length === 0) {
    throw new RoutingError("No road route could be found through the given locations.");
  }

  return data.routes.map((route) => ({
    path: route.geometry.coordinates.map(([lon, lat]) => [lat, lon] as LatLngTuple),
    distanceMeters: route.distance,
    durationSeconds: route.duration,
    steps: buildNavigationSteps(route.legs.flatMap((leg) => leg.steps)),
  }));
}
