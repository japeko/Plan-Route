import { Router, type Router as ExpressRouter } from "express";
import {
  createPoiHandler,
  deletePoiHandler,
  getPoiByIdHandler,
  listPoisHandler,
  listPoisInViewportHandler,
  listPoisNearbyHandler,
  updatePoiHandler,
} from "../controllers/poi.controller.js";

export const poiRouter: ExpressRouter = Router();

poiRouter.get("/viewport", listPoisInViewportHandler);
poiRouter.get("/nearby", listPoisNearbyHandler);
poiRouter.get("/:id", getPoiByIdHandler);
poiRouter.get("/", listPoisHandler);
poiRouter.post("/", createPoiHandler);
poiRouter.patch("/:id", updatePoiHandler);
poiRouter.delete("/:id", deletePoiHandler);
