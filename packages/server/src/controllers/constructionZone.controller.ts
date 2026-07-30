import type { NextFunction, Request, Response } from "express";
import { isValidObjectId } from "mongoose";
import { HttpError } from "../errors/HttpError.js";
import { CONSTRUCTION_ZONE_ERROR_MESSAGES } from "../constants/errorMessages.constants.js";
import {
  createConstructionZoneReport,
  deleteConstructionZoneReport,
  listConstructionZoneReportsAlongRoute,
} from "../services/constructionZone.service.js";
import {
  constructionZonesAlongRouteRequestSchema,
  createConstructionZoneReportSchema,
} from "../validators/constructionZone.validator.js";

function requireValidId(id: string | undefined): string {
  if (!id || !isValidObjectId(id)) {
    throw new HttpError(400, CONSTRUCTION_ZONE_ERROR_MESSAGES.INVALID_ID);
  }
  return id;
}

export async function listConstructionZoneReportsAlongRouteHandler(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const body = constructionZonesAlongRouteRequestSchema.parse(req.body);
    const reports = await listConstructionZoneReportsAlongRoute(body);
    res.json(reports);
  } catch (err) {
    next(err);
  }
}

export async function createConstructionZoneReportHandler(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const dto = createConstructionZoneReportSchema.parse(req.body);
    const report = await createConstructionZoneReport(dto);
    res.status(201).json(report);
  } catch (err) {
    next(err);
  }
}

export async function deleteConstructionZoneReportHandler(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const id = requireValidId(req.params.id);
    const deleted = await deleteConstructionZoneReport(id);
    if (!deleted) {
      throw new HttpError(404, CONSTRUCTION_ZONE_ERROR_MESSAGES.NOT_FOUND);
    }
    res.status(204).send();
  } catch (err) {
    next(err);
  }
}
