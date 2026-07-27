import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { HttpError } from "../errors/HttpError.js";
import { GENERAL_ERROR_MESSAGES, POI_ERROR_MESSAGES } from "../constants/errorMessages.constants.js";

export function errorHandlerMiddleware(
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction,
): void {
  if (err instanceof HttpError) {
    res.status(err.statusCode).json({ message: err.message });
    return;
  }

  if (err instanceof ZodError) {
    res.status(400).json({ message: POI_ERROR_MESSAGES.INVALID_PAYLOAD, issues: err.issues });
    return;
  }

  res.status(500).json({ message: GENERAL_ERROR_MESSAGES.INTERNAL_SERVER_ERROR });
}

export function notFoundMiddleware(_req: Request, res: Response): void {
  res.status(404).json({ message: GENERAL_ERROR_MESSAGES.ROUTE_NOT_FOUND });
}
