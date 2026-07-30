import { Router, type Router as ExpressRouter } from "express";
import {
  createConstructionZoneReportHandler,
  deleteConstructionZoneReportHandler,
  listConstructionZoneReportsAlongRouteHandler,
} from "../controllers/constructionZone.controller.js";

export const constructionZoneRouter: ExpressRouter = Router();

constructionZoneRouter.post("/along-route", listConstructionZoneReportsAlongRouteHandler);
constructionZoneRouter.post("/", createConstructionZoneReportHandler);
constructionZoneRouter.delete("/:id", deleteConstructionZoneReportHandler);
