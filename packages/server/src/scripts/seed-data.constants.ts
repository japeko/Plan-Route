import type { CreatePoiDto } from "@poi/shared";

export const SEED_POIS: CreatePoiDto[] = [
  {
    type: "restaurant",
    name: "Harbor Bistro",
    location: { type: "Point", coordinates: [24.9384, 60.1699] },
    address: "Pohjoisesplanadi 1, Helsinki",
  },
  {
    type: "restaurant",
    name: "Kallio Kitchen",
    location: { type: "Point", coordinates: [24.9508, 60.1841] },
    address: "Vaasankatu 12, Helsinki",
  },
  {
    type: "gas_station",
    name: "Ruoholahti Fuel Stop",
    location: { type: "Point", coordinates: [24.9147, 60.1613] },
    address: "Itamerenkatu 5, Helsinki",
    hasRestaurant: false,
  },
  {
    type: "gas_station",
    name: "Pasila Travel Center",
    location: { type: "Point", coordinates: [24.9327, 60.1985] },
    address: "Ratapihantie 9, Helsinki",
    hasRestaurant: true,
  },
];
