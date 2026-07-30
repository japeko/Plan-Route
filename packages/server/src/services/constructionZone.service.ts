import * as turf from "@turf/turf";
import type { ConstructionZoneReport, ConstructionZonesAlongRouteRequestDto, CreateConstructionZoneReportDto } from "@poi/shared";
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
  const doc = await ConstructionZoneReportModel.create({ location: dto.location });
  return toConstructionZoneReport(doc.toObject() as unknown as LeanConstructionZoneReportDocument);
}

export async function deleteConstructionZoneReport(id: string): Promise<boolean> {
  const result = await ConstructionZoneReportModel.findByIdAndDelete(id).lean();
  return result !== null;
}
