import { z } from "zod";

const geoPointSchema = z.object({
  type: z.literal("Point"),
  coordinates: z.tuple([z.number(), z.number()]),
});

const basePoiFields = {
  name: z.string().min(1),
  location: geoPointSchema,
  address: z.string().optional(),
};

const gasStationSchema = z.object({
  ...basePoiFields,
  type: z.literal("gas_station"),
  hasRestaurant: z.boolean(),
});

const restaurantSchema = z.object({
  ...basePoiFields,
  type: z.literal("restaurant"),
});

export const createPoiSchema = z.discriminatedUnion("type", [gasStationSchema, restaurantSchema]);

export const updatePoiSchema = z
  .object({
    name: z.string().min(1),
    location: geoPointSchema,
    address: z.string().optional(),
    hasRestaurant: z.boolean(),
  })
  .partial();

export const poiTypeSchema = z.enum(["gas_station", "restaurant"]);

export const viewportQuerySchema = z.object({
  minLng: z.coerce.number(),
  minLat: z.coerce.number(),
  maxLng: z.coerce.number(),
  maxLat: z.coerce.number(),
  type: poiTypeSchema.optional(),
});

export const nearbyQuerySchema = z.object({
  lng: z.coerce.number(),
  lat: z.coerce.number(),
  radiusMeters: z.coerce.number().positive(),
  type: poiTypeSchema.optional(),
});

export const listQuerySchema = z.object({
  type: poiTypeSchema.optional(),
});
