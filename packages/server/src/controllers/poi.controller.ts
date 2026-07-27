import type { NextFunction, Request, Response } from "express";
import { isValidObjectId } from "mongoose";
import { HttpError } from "../errors/HttpError.js";
import { POI_ERROR_MESSAGES } from "../constants/errorMessages.constants.js";
import {
  createPoi,
  deletePoi,
  getPoiById,
  listPois,
  listPoisInViewport,
  listPoisNearby,
  updatePoi,
} from "../services/poi.service.js";
import {
  createPoiSchema,
  listQuerySchema,
  nearbyQuerySchema,
  updatePoiSchema,
  viewportQuerySchema,
} from "../validators/poi.validator.js";

function requireValidId(id: string | undefined): string {
  if (!id || !isValidObjectId(id)) {
    throw new HttpError(400, POI_ERROR_MESSAGES.INVALID_ID);
  }
  return id;
}

export async function listPoisHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const query = listQuerySchema.parse(req.query);
    const pois = await listPois(query);
    res.json(pois);
  } catch (err) {
    next(err);
  }
}

export async function listPoisInViewportHandler(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const query = viewportQuerySchema.parse(req.query);
    const pois = await listPoisInViewport(query);
    res.json(pois);
  } catch (err) {
    next(err);
  }
}

export async function listPoisNearbyHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const query = nearbyQuerySchema.parse(req.query);
    const pois = await listPoisNearby(query);
    res.json(pois);
  } catch (err) {
    next(err);
  }
}

export async function getPoiByIdHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = requireValidId(req.params.id);
    const poi = await getPoiById(id);
    if (!poi) {
      throw new HttpError(404, POI_ERROR_MESSAGES.NOT_FOUND);
    }
    res.json(poi);
  } catch (err) {
    next(err);
  }
}

export async function createPoiHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const dto = createPoiSchema.parse(req.body);
    const poi = await createPoi(dto);
    res.status(201).json(poi);
  } catch (err) {
    next(err);
  }
}

export async function updatePoiHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = requireValidId(req.params.id);
    const dto = updatePoiSchema.parse(req.body);
    const poi = await updatePoi(id, dto);
    if (!poi) {
      throw new HttpError(404, POI_ERROR_MESSAGES.NOT_FOUND);
    }
    res.json(poi);
  } catch (err) {
    next(err);
  }
}

export async function deletePoiHandler(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const id = requireValidId(req.params.id);
    const deleted = await deletePoi(id);
    if (!deleted) {
      throw new HttpError(404, POI_ERROR_MESSAGES.NOT_FOUND);
    }
    res.status(204).send();
  } catch (err) {
    next(err);
  }
}
