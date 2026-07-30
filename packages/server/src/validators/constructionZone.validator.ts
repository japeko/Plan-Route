import { z } from "zod";

const geoPointSchema = z.object({
  type: z.literal("Point"),
  coordinates: z.tuple([z.number(), z.number()]),
});

const geoLineStringSchema = z.object({
  type: z.literal("LineString"),
  coordinates: z.array(z.tuple([z.number(), z.number()])).min(2),
});

export const createConstructionZoneReportSchema = z.object({
  location: geoPointSchema,
});

export const constructionZonesAlongRouteRequestSchema = z.object({
  route: geoLineStringSchema,
  radiusMeters: z.number().positive(),
});
