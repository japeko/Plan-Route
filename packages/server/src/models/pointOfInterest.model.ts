import { Schema, model } from "mongoose";
import type { PoiType } from "@poi/shared";

export interface GeoPointDocument {
  type: "Point";
  coordinates: [number, number];
}

export interface PointOfInterestDocument {
  name: string;
  type: PoiType;
  location: GeoPointDocument;
  address?: string;
  hasGasoline?: boolean;
  hasElectricCharging?: boolean;
  hasRestaurant?: boolean;
}

const geoPointSchema = new Schema<GeoPointDocument>(
  {
    type: { type: String, enum: ["Point"], required: true },
    coordinates: { type: [Number], required: true },
  },
  { _id: false },
);

const toJsonTransform = {
  virtuals: true,
  versionKey: false,
  transform: (_doc: unknown, ret: Record<string, unknown>): void => {
    delete ret._id;
  },
};

const pointOfInterestSchema = new Schema<PointOfInterestDocument>(
  {
    name: { type: String, required: true },
    type: { type: String, enum: ["gas_station", "restaurant"], required: true },
    location: { type: geoPointSchema, required: true },
    address: { type: String, required: false },
  },
  {
    timestamps: true,
    discriminatorKey: "type",
    toJSON: toJsonTransform,
    id: true,
  },
);

pointOfInterestSchema.index({ location: "2dsphere" });
pointOfInterestSchema.index({ type: 1 });

export const PointOfInterestModel = model<PointOfInterestDocument>(
  "PointOfInterest",
  pointOfInterestSchema,
  "pointsOfInterest",
);

export interface GasStationDocument extends PointOfInterestDocument {
  hasGasoline: boolean;
  hasElectricCharging: boolean;
  hasRestaurant: boolean;
}

export const GasStationModel = PointOfInterestModel.discriminator<GasStationDocument>(
  "gas_station",
  new Schema<GasStationDocument>({
    hasGasoline: { type: Boolean, required: true },
    hasElectricCharging: { type: Boolean, required: true },
    hasRestaurant: { type: Boolean, required: true },
  }),
);

export const RestaurantModel = PointOfInterestModel.discriminator(
  "restaurant",
  new Schema({}),
);
