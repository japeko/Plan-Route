#!/usr/bin/env python3
"""Import Finnish gas stations and restaurants from OpenStreetMap into the poi database.

Data source: the Overpass API (OpenStreetMap), the same free/no-key OSM stack
already used by packages/client for tiles, geocoding, and routing. Safe to
re-run: records are upserted by their OSM element id, so reruns update
existing entries instead of duplicating them.

Usage:
    pip install -r requirements.txt
    python import_finland_pois.py [--mongodb-uri mongodb://localhost:27017/poi]
"""

from __future__ import annotations

import argparse
import math
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

import requests
from dotenv import load_dotenv
from pymongo import MongoClient, UpdateOne

OVERPASS_URL = "https://overpass-api.de/api/interpreter"
# Overpass rejects requests with no descriptive User-Agent (406 Not Acceptable).
REQUEST_HEADERS = {"User-Agent": "finland-poi-import-script/1.0 (dev/personal project)"}
FINLAND_ISO_CODE = "FI"
COLLECTION_NAME = "pointsOfInterest"
RESTAURANT_PROXIMITY_METERS = 60
REQUEST_TIMEOUT_SECONDS = 220

OVERPASS_QUERY = f"""
[out:json][timeout:180];
area["ISO3166-1"="{FINLAND_ISO_CODE}"][admin_level=2]->.finland;
(
  node["amenity"="fuel"](area.finland);
  way["amenity"="fuel"](area.finland);
  node["amenity"="restaurant"](area.finland);
  way["amenity"="restaurant"](area.finland);
);
out center tags;
"""


@dataclass
class OsmElement:
    osm_id: str
    amenity: str
    lat: float
    lon: float
    tags: dict[str, str]


def fetch_osm_elements() -> list[OsmElement]:
    response = requests.post(
        OVERPASS_URL,
        data={"data": OVERPASS_QUERY},
        headers=REQUEST_HEADERS,
        timeout=REQUEST_TIMEOUT_SECONDS,
    )
    response.raise_for_status()
    payload: dict[str, Any] = response.json()

    elements: list[OsmElement] = []
    for el in payload.get("elements", []):
        if el["type"] == "node":
            lat, lon = el.get("lat"), el.get("lon")
        else:
            center = el.get("center")
            lat, lon = (center["lat"], center["lon"]) if center else (None, None)

        if lat is None or lon is None:
            continue

        tags = el.get("tags", {})
        amenity = tags.get("amenity")
        if amenity not in ("fuel", "restaurant"):
            continue

        elements.append(
            OsmElement(
                osm_id=f"{el['type']}/{el['id']}",
                amenity=amenity,
                lat=lat,
                lon=lon,
                tags=tags,
            )
        )
    return elements


def build_address(tags: dict[str, str]) -> str | None:
    street = tags.get("addr:street")
    house_number = tags.get("addr:housenumber")
    city = tags.get("addr:city")

    line = street
    if line and house_number:
        line = f"{line} {house_number}"

    parts = [part for part in (line, city) if part]
    return ", ".join(parts) if parts else None


def build_name(tags: dict[str, str], fallback: str) -> str:
    return tags.get("name") or tags.get("brand") or fallback


def haversine_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371000
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def has_attached_restaurant(fuel: OsmElement, restaurants: list[OsmElement]) -> bool:
    amenity_tag = fuel.tags.get("amenity", "")
    if "restaurant" in amenity_tag.split(";") or fuel.tags.get("cuisine"):
        return True

    # Bounding-box pre-filter (~0.001 deg lat, ~0.002 deg lon at Finnish
    # latitudes is comfortably wider than RESTAURANT_PROXIMITY_METERS)
    # before paying for the haversine call.
    for restaurant in restaurants:
        if abs(restaurant.lat - fuel.lat) > 0.001 or abs(restaurant.lon - fuel.lon) > 0.002:
            continue
        if haversine_meters(fuel.lat, fuel.lon, restaurant.lat, restaurant.lon) <= RESTAURANT_PROXIMITY_METERS:
            return True
    return False


def to_poi_document(element: OsmElement, *, has_restaurant: bool | None = None) -> dict[str, Any]:
    now = datetime.now(timezone.utc)
    doc: dict[str, Any] = {
        "osmId": element.osm_id,
        "name": build_name(element.tags, "Gas station" if element.amenity == "fuel" else "Restaurant"),
        "type": "gas_station" if element.amenity == "fuel" else "restaurant",
        "location": {"type": "Point", "coordinates": [element.lon, element.lat]},
        "address": build_address(element.tags),
        "updatedAt": now,
    }
    if element.amenity == "fuel":
        doc["hasRestaurant"] = bool(has_restaurant)
    return doc


def import_pois(mongodb_uri: str) -> None:
    print("Fetching gas stations and restaurants in Finland from Overpass API...")
    elements = fetch_osm_elements()

    fuel_elements = [el for el in elements if el.amenity == "fuel"]
    restaurant_elements = [el for el in elements if el.amenity == "restaurant"]
    print(f"Fetched {len(fuel_elements)} gas stations and {len(restaurant_elements)} restaurants.")

    print("Determining which gas stations have an attached restaurant...")
    documents: list[dict[str, Any]] = []
    for fuel in fuel_elements:
        documents.append(to_poi_document(fuel, has_restaurant=has_attached_restaurant(fuel, restaurant_elements)))
    for restaurant in restaurant_elements:
        documents.append(to_poi_document(restaurant))

    client = MongoClient(mongodb_uri)
    collection = client.get_default_database()[COLLECTION_NAME]

    print(f"Upserting {len(documents)} points of interest into '{COLLECTION_NAME}'...")
    operations = [UpdateOne({"osmId": doc["osmId"]}, {"$set": doc}, upsert=True) for doc in documents]

    batch_size = 1000
    upserted = modified = 0
    for i in range(0, len(operations), batch_size):
        batch = operations[i : i + batch_size]
        result = collection.bulk_write(batch, ordered=False)
        upserted += len(result.upserted_ids)
        modified += result.modified_count
        print(f"  ...{min(i + batch_size, len(operations))}/{len(operations)}")

    collection.create_index([("location", "2dsphere")])
    collection.create_index("type")
    # sparse: the collection also holds hand-seeded/manually-created POIs
    # with no osmId at all; a plain unique index would treat those missing
    # fields as duplicate nulls and fail.
    collection.create_index("osmId", unique=True, sparse=True)

    client.close()
    print(f"Done. Inserted {upserted} new, updated {modified} existing points of interest.")


def main() -> None:
    load_dotenv()

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--mongodb-uri",
        default=os.environ.get("MONGODB_URI", "mongodb://localhost:27017/poi"),
        help="MongoDB connection string (default: $MONGODB_URI or mongodb://localhost:27017/poi)",
    )
    args = parser.parse_args()

    start = time.monotonic()
    try:
        import_pois(args.mongodb_uri)
    except requests.RequestException as err:
        print(f"Overpass API request failed: {err}", file=sys.stderr)
        sys.exit(1)
    print(f"Finished in {time.monotonic() - start:.1f}s.")


if __name__ == "__main__":
    main()
