import * as turf from "@turf/turf";
import {
  CONSTRUCTION_ZONE_REPORT_MIN_DISTANCE_METERS,
  type ConstructionZoneReport,
  type ConstructionZonesAlongRouteRequestDto,
  type CreateConstructionZoneReportDto,
} from "@poi/shared";
import { HttpError } from "../errors/HttpError.js";
import { CONSTRUCTION_ZONE_ERROR_MESSAGES } from "../constants/errorMessages.constants.js";
import { ConstructionZoneReportModel } from "../models/constructionZoneReport.model.js";
import { CONSTRUCTION_ZONE_REPORT_PROJECTION } from "../constants/constructionZone.constants.js";

interface LeanConstructionZoneReportDocument {
  _id: unknown;
  location: { type: "Point"; coordinates: [number, number] };
  createdAt: Date;
}

function toConstructionZoneReport(doc: LeanConstructionZoneReportDocument): ConstructionZoneReport {
  return {
    id: String(doc._id),
    location: doc.location,
    createdAt: doc.createdAt.toISOString(),
  };
}

export async function listConstructionZoneReportsAlongRoute(
  options: ConstructionZonesAlongRouteRequestDto,
): Promise<ConstructionZoneReport[]> {
  const line = turf.simplify(turf.lineString(options.route.coordinates), {
    tolerance: 0.001,
    highQuality: false,
  });
  const corridor = turf.buffer(line, options.radiusMeters, { units: "meters", steps: 8 });

  if (!corridor) {
    return [];
  }

  const docs = await ConstructionZoneReportModel.find({
    location: { $geoWithin: { $geometry: corridor.geometry } },
  })
    .select(CONSTRUCTION_ZONE_REPORT_PROJECTION)
    .lean();

  return docs.map((doc) => toConstructionZoneReport(doc as unknown as LeanConstructionZoneReportDocument));
}

export async function createConstructionZoneReport(
  dto: CreateConstructionZoneReportDto,
): Promise<ConstructionZoneReport> {
  // Without this, two different users reporting the same real-world
  // road work from slightly different spots (or the same user tapping
  // twice) would each create their own marker rather than being treated
  // as the same incident.
  const existingNearby = await ConstructionZoneReportModel.findOne({
    location: {
      $nearSphere: {
        $geometry: dto.location,
        $maxDistance: CONSTRUCTION_ZONE_REPORT_MIN_DISTANCE_METERS,
      },
    },
  }).lean();

  if (existingNearby) {
    throw new HttpError(409, CONSTRUCTION_ZONE_ERROR_MESSAGES.TOO_CLOSE_TO_EXISTING);
  }

  const doc = await ConstructionZoneReportModel.create({ location: dto.location });
  return toConstructionZoneReport(doc.toObject() as unknown as LeanConstructionZoneReportDocument);
}

export async function deleteConstructionZoneReport(id: string): Promise<boolean> {
  const result = await ConstructionZoneReportModel.findByIdAndDelete(id).lean();
  return result !== null;
}
