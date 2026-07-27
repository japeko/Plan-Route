import { FUEL_TYPES } from "@poi/shared";
import { z } from "zod";

const geoPointSchema = z.object({
  type: z.literal("Point"),
  coordinates: z.tuple([z.number(), z.number()]),
});

const geoLineStringSchema = z.object({
  type: z.literal("LineString"),
  coordinates: z.array(z.tuple([z.number(), z.number()])).min(2),
});

const basePoiFields = {
  name: z.string().min(1),
  location: geoPointSchema,
  address: z.string().optional(),
};

const gasStationSchema = z.object({
  ...basePoiFields,
  type: z.literal("gas_station"),
  hasGasoline: z.boolean(),
  hasElectricCharging: z.boolean(),
  hasRestaurant: z.boolean(),
});

const restaurantSchema = z.object({
  ...basePoiFields,
  type: z.literal("restaurant"),
});

// refine wraps (rather than joins) the union so discriminatedUnion still
// sees plain object members, which it requires for its "type" discrimination.
export const createPoiSchema = z
  .discriminatedUnion("type", [gasStationSchema, restaurantSchema])
  .refine((poi) => poi.type !== "gas_station" || poi.hasGasoline || poi.hasElectricCharging, {
    message: "A gas station must offer gasoline, electric charging, or both.",
  });

export const updatePoiSchema = z
  .object({
    name: z.string().min(1),
    location: geoPointSchema,
    address: z.string().optional(),
    hasGasoline: z.boolean(),
    hasElectricCharging: z.boolean(),
    hasRestaurant: z.boolean(),
  })
  .partial();

export const fuelTypeSchema = z.enum(FUEL_TYPES);

export const alongRouteRequestSchema = z.object({
  route: geoLineStringSchema,
  radiusMeters: z.number().positive(),
  showRestaurants: z.boolean(),
  showGasStations: z.boolean(),
  fuelTypes: z.array(fuelTypeSchema),
  onlyWithRestaurant: z.boolean(),
});

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
