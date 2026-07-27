import "dotenv/config";
import cors from "cors";
import express from "express";
import { connectToDatabase } from "./db/connection.js";
import { poiRouter } from "./routes/poi.routes.js";
import { errorHandlerMiddleware, notFoundMiddleware } from "./middleware/errorHandler.middleware.js";

const MONGODB_URI: string = process.env.MONGODB_URI ?? "mongodb://localhost:27017/poi";
const PORT: number = Number(process.env.PORT ?? 3000);
const CLIENT_ORIGIN: string = process.env.CLIENT_ORIGIN ?? "http://localhost:5173";

async function main(): Promise<void> {
  await connectToDatabase(MONGODB_URI);

  const app = express();
  app.use(cors({ origin: CLIENT_ORIGIN }));
  app.use(express.json());

  app.use("/api/pois", poiRouter);

  app.use(notFoundMiddleware);
  app.use(errorHandlerMiddleware);

  app.listen(PORT, () => {
     
    console.log(`Server listening on port ${PORT}`);
  });
}

main().catch((err: unknown) => {
   
  console.error("Failed to start server:", err);
  process.exit(1);
});
