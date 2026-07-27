import "dotenv/config";
import { connectToDatabase, disconnectFromDatabase } from "../db/connection.js";
import { PointOfInterestModel } from "../models/pointOfInterest.model.js";
import { SEED_POIS } from "./seed-data.constants.js";

const MONGODB_URI: string = process.env.MONGODB_URI ?? "mongodb://localhost:27017/poi";

async function seed(): Promise<void> {
  await connectToDatabase(MONGODB_URI);

  if (process.env.NODE_ENV === "production") {
    throw new Error("Refusing to run the seed script against a production environment.");
  }

  await PointOfInterestModel.deleteMany({});
  await PointOfInterestModel.insertMany(SEED_POIS);

   
  console.log(`Seeded ${SEED_POIS.length} points of interest.`);

  await disconnectFromDatabase();
}

seed().catch((err: unknown) => {
   
  console.error("Seed failed:", err);
  process.exit(1);
});
