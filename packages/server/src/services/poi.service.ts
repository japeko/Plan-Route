import * as turf from "@turf/turf";
import type {
  CreatePoiDto,
  PoiAlongRouteRequestDto,
  PoiListQueryDto,
  PoiNearbyQueryDto,
  PoiViewportQueryDto,
  PointOfInterest,
  UpdatePoiDto,
} from "@poi/shared";
import { GasStationModel, PointOfInterestModel, RestaurantModel } from "../models/pointOfInterest.model.js";
import { POI_LIST_PROJECTION } from "../constants/poi.constants.js";

// Projection is applied per the CLAUDE.md convention. POI documents are
// small, so this is a pattern-consistency measure rather than a
// performance-critical one — keep it so the convention still holds once
// larger fields (photos, reviews) are added later.

interface LeanPoiDocument {
  _id: unknown;
  name: string;
  type: PointOfInterest["type"];
  location: { type: "Point"; coordinates: [number, number] };
  address?: string;
  hasGasoline?: boolean;
  hasElectricCharging?: boolean;
  hasRestaurant?: boolean;
}

function toPointOfInterest(doc: LeanPoiDocument): PointOfInterest {
  const base = {
    id: String(doc._id),
    name: doc.name,
    location: doc.location,
    address: doc.address,
  };

  if (doc.type === "gas_station") {
    return {
      ...base,
      type: "gas_station",
      hasGasoline: Boolean(doc.hasGasoline),
      hasElectricCharging: Boolean(doc.hasElectricCharging),
      hasRestaurant: Boolean(doc.hasRestaurant),
    };
  }

  return { ...base, type: "restaurant" };
}

export async function listPois(query: PoiListQueryDto): Promise<PointOfInterest[]> {
  const filter = query.type ? { type: query.type } : {};
  const docs = await PointOfInterestModel.find(filter).select(POI_LIST_PROJECTION).lean();
  return docs.map((doc) => toPointOfInterest(doc as unknown as LeanPoiDocument));
}

export async function listPoisInViewport(query: PoiViewportQueryDto): Promise<PointOfInterest[]> {
  const filter = {
    ...(query.type ? { type: query.type } : {}),
    location: {
      $geoWithin: {
        $box: [
          [query.minLng, query.minLat],
          [query.maxLng, query.maxLat],
        ],
      },
    },
  };
  const docs = await PointOfInterestModel.find(filter).select(POI_LIST_PROJECTION).lean();
  return docs.map((doc) => toPointOfInterest(doc as unknown as LeanPoiDocument));
}

export async function listPoisNearby(query: PoiNearbyQueryDto): Promise<PointOfInterest[]> {
  const filter = {
    ...(query.type ? { type: query.type } : {}),
    location: {
      $geoWithin: {
        $centerSphere: [[query.lng, query.lat], query.radiusMeters / 6378137],
      },
    },
  };
  const docs = await PointOfInterestModel.find(filter).select(POI_LIST_PROJECTION).lean();
  return docs.map((doc) => toPointOfInterest(doc as unknown as LeanPoiDocument));
}

function buildAlongRouteTypeFilter(options: PoiAlongRouteRequestDto): Record<string, unknown> {
  const branches: Record<string, unknown>[] = [];

  if (options.showRestaurants) {
    branches.push({ type: "restaurant" });
  }

  if (options.showGasStations && options.fuelTypes.length > 0) {
    branches.push({
      type: "gas_station",
      $or: options.fuelTypes.map((fuel) => (fuel === "gasoline" ? { hasGasoline: true } : { hasElectricCharging: true })),
      ...(options.onlyWithRestaurant ? { hasRestaurant: true } : {}),
    });
  }

  // No branch selected (e.g. both category checkboxes off) should match
  // nothing rather than falling through to "no filter at all".
  return branches.length > 0 ? { $or: branches } : { _id: null };
}

export async function listPoisAlongRoute(options: PoiAlongRouteRequestDto): Promise<PointOfInterest[]> {
  // Simplify before buffering so a long/detailed route (thousands of OSRM
  // geometry points) doesn't produce an unreasonably complex corridor
  // polygon for MongoDB's 2dsphere index to evaluate.
  const line = turf.simplify(turf.lineString(options.route.coordinates), {
    tolerance: 0.001,
    highQuality: false,
  });
  const corridor = turf.buffer(line, options.radiusMeters, { units: "meters", steps: 8 });

  if (!corridor) {
    return [];
  }

  const filter = {
    ...buildAlongRouteTypeFilter(options),
    location: { $geoWithin: { $geometry: corridor.geometry } },
  };

  const docs = await PointOfInterestModel.find(filter).select(POI_LIST_PROJECTION).lean();
  return docs.map((doc) => toPointOfInterest(doc as unknown as LeanPoiDocument));
}

export async function getPoiById(id: string): Promise<PointOfInterest | null> {
  const doc = await PointOfInterestModel.findById(id).select(POI_LIST_PROJECTION).lean();
  return doc ? toPointOfInterest(doc as unknown as LeanPoiDocument) : null;
}

export async function createPoi(dto: CreatePoiDto): Promise<PointOfInterest> {
  const doc = dto.type === "gas_station" ? await GasStationModel.create(dto) : await RestaurantModel.create(dto);
  const lean = doc.toObject();
  return toPointOfInterest(lean as unknown as LeanPoiDocument);
}

export async function updatePoi(id: string, dto: UpdatePoiDto): Promise<PointOfInterest | null> {
  // findByIdAndUpdate runs against the base schema directly, which is
  // strict-mode and doesn't know discriminator-only fields (e.g.
  // hasRestaurant), so those get silently stripped from the update.
  // findById returns a document already hydrated as the correct
  // discriminator subclass, so .set()/.save() applies the right schema.
  const doc = await PointOfInterestModel.findById(id);
  if (!doc) {
    return null;
  }
  doc.set(dto);
  await doc.save();
  return toPointOfInterest(doc.toObject() as unknown as LeanPoiDocument);
}

export async function deletePoi(id: string): Promise<boolean> {
  const result = await PointOfInterestModel.findByIdAndDelete(id).lean();
  return result !== null;
}
