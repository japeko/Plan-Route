import { Schema, model } from "mongoose";
import { CONSTRUCTION_ZONE_REPORT_TTL_SECONDS } from "@poi/shared";

export interface GeoPointDocument {
  type: "Point";
  coordinates: [number, number];
}

export interface ConstructionZoneReportDocument {
  location: GeoPointDocument;
  createdAt: Date;
}

const geoPointSchema = new Schema<GeoPointDocument>(
  {
    type: { type: String, enum: ["Point"], required: true },
    coordinates: { type: [Number], required: true },
  },
  { _id: false },
);

const constructionZoneReportSchema = new Schema<ConstructionZoneReportDocument>(
  {
    location: { type: geoPointSchema, required: true },
    createdAt: { type: Date, required: true, default: Date.now },
  },
  {
    toJSON: {
      versionKey: false,
      transform: (_doc: unknown, ret: Record<string, unknown>): void => {
        delete ret._id;
      },
    },
    id: true,
  },
);

constructionZoneReportSchema.index({ location: "2dsphere" });
// No moderation/verification exists for these reports, so they need to
// expire on their own rather than accumulate forever — MongoDB deletes
// the document automatically once createdAt + TTL has passed.
constructionZoneReportSchema.index({ createdAt: 1 }, { expireAfterSeconds: CONSTRUCTION_ZONE_REPORT_TTL_SECONDS });

export const ConstructionZoneReportModel = model<ConstructionZoneReportDocument>(
  "ConstructionZoneReport",
  constructionZoneReportSchema,
  "constructionZoneReports",
);
