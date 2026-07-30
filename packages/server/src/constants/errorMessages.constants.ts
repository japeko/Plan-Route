export const POI_ERROR_MESSAGES = {
  NOT_FOUND: "Point of interest not found.",
  INVALID_ID: "Invalid point of interest id.",
  INVALID_PAYLOAD: "Invalid point of interest payload.",
  CREATE_FAILED: "Failed to create point of interest.",
  FETCH_FAILED: "Failed to fetch points of interest.",
  UPDATE_FAILED: "Failed to update point of interest.",
  DELETE_FAILED: "Failed to delete point of interest.",
} as const;

export const CONSTRUCTION_ZONE_ERROR_MESSAGES = {
  NOT_FOUND: "Construction zone report not found.",
  INVALID_ID: "Invalid construction zone report id.",
} as const;

export const GENERAL_ERROR_MESSAGES = {
  INTERNAL_SERVER_ERROR: "Internal server error.",
  ROUTE_NOT_FOUND: "Route not found.",
} as const;
