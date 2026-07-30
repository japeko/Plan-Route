#!/usr/bin/env python3
"""Import Finnish gas stations, restaurants, camping areas, and accommodation from OpenStreetMap into the poi database.

Data source: the Overpass API (OpenStreetMap), the same free/no-key OSM stack
already used by packages/client for tiles, geocoding, and routing. Safe to
re-run: records are upserted by their OSM element id, so reruns update
existing entries instead of duplicating them.

A "gas station" record can offer gasoline, electric charging, or both:
fuel (amenity=fuel) and charging (amenity=charging_station) OSM elements
found within STATION_MERGE_PROXIMITY_METERS of each other are merged into
a single station; a charging point with no nearby fuel pump becomes its
own electric-only station, and vice versa.

A "camping" record can offer tent sites, caravan sites, or both, read from
OSM's tourism=camp_site / tourism=caravan_site tags (and their tents=/
caravans= sub-tags).

An "accommodation" record is either a hotel or a hostel, read directly
from OSM's tourism=hotel / tourism=hostel tags.

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
STATION_MERGE_PROXIMITY_METERS = 50
REQUEST_TIMEOUT_SECONDS = 220

def build_query(tag_key: str, tag_value: str) -> str:
    # One tag per request: overpass-api.de's public gateway has a hard
    # timeout shorter than the [timeout:N] budget below, and a single
    # combined nationwide query across every category reliably exceeded it
    # (504) even though each tag alone comfortably fits.
    return f"""
[out:json][timeout:120];
area["ISO3166-1"="{FINLAND_ISO_CODE}"][admin_level=2]->.finland;
(
  node["{tag_key}"="{tag_value}"](area.finland);
  way["{tag_key}"="{tag_value}"](area.finland);
);
out center tags;
"""


@dataclass
class OsmElement:
    osm_id: str
    tag_value: str
    lat: float
    lon: float
    tags: dict[str, str]


@dataclass
class StationRecord:
    osm_id: str
    name: str
    address: str | None
    lat: float
    lon: float
    has_gasoline: bool
    has_electric_charging: bool
    has_restaurant: bool = False


def fetch_osm_elements(tag_key: str, tag_value: str, *, max_attempts: int = 4) -> list[OsmElement]:
    query = build_query(tag_key, tag_value)
    response: requests.Response | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            response = requests.post(
                OVERPASS_URL,
                data={"data": query},
                headers=REQUEST_HEADERS,
                timeout=REQUEST_TIMEOUT_SECONDS,
            )
            response.raise_for_status()
            break
        except requests.RequestException as err:
            if attempt == max_attempts:
                raise
            wait_seconds = 10 * attempt
            print(
                f"Overpass request for '{tag_key}={tag_value}' failed (attempt {attempt}/{max_attempts}): {err}. "
                f"Retrying in {wait_seconds}s...",
                file=sys.stderr,
            )
            time.sleep(wait_seconds)

    assert response is not None
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
        el_tag_value = tags.get(tag_key)
        if el_tag_value != tag_value:
            continue

        elements.append(
            OsmElement(
                osm_id=f"{el['type']}/{el['id']}",
                tag_value=el_tag_value,
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


def is_nearby(a: OsmElement, b: OsmElement, max_meters: float) -> bool:
    # Bounding-box pre-filter (~0.001 deg lat, ~0.002 deg lon at Finnish
    # latitudes is comfortably wider than any max_meters we use) before
    # paying for the haversine call.
    if abs(a.lat - b.lat) > 0.001 or abs(a.lon - b.lon) > 0.002:
        return False
    return haversine_meters(a.lat, a.lon, b.lat, b.lon) <= max_meters


def has_attached_restaurant(element: OsmElement, restaurants: list[OsmElement]) -> bool:
    amenity_tag = element.tags.get("amenity", "")
    if "restaurant" in amenity_tag.split(";") or element.tags.get("cuisine"):
        return True
    return any(is_nearby(element, restaurant, RESTAURANT_PROXIMITY_METERS) for restaurant in restaurants)


def merge_fuel_and_charging(
    fuel_elements: list[OsmElement],
    charging_elements: list[OsmElement],
    restaurant_elements: list[OsmElement],
) -> list[StationRecord]:
    matched_charging_ids: set[str] = set()
    stations: list[StationRecord] = []

    for fuel in fuel_elements:
        has_electric = False
        for charging in charging_elements:
            if charging.osm_id in matched_charging_ids:
                continue
            if is_nearby(fuel, charging, STATION_MERGE_PROXIMITY_METERS):
                has_electric = True
                matched_charging_ids.add(charging.osm_id)
                break

        stations.append(
            StationRecord(
                osm_id=fuel.osm_id,
                name=build_name(fuel.tags, "Gas station"),
                address=build_address(fuel.tags),
                lat=fuel.lat,
                lon=fuel.lon,
                has_gasoline=True,
                has_electric_charging=has_electric,
                has_restaurant=has_attached_restaurant(fuel, restaurant_elements),
            )
        )

    for charging in charging_elements:
        if charging.osm_id in matched_charging_ids:
            continue
        stations.append(
            StationRecord(
                osm_id=charging.osm_id,
                name=build_name(charging.tags, "EV charging station"),
                address=build_address(charging.tags),
                lat=charging.lat,
                lon=charging.lon,
                has_gasoline=False,
                has_electric_charging=True,
                has_restaurant=has_attached_restaurant(charging, restaurant_elements),
            )
        )

    return stations


def station_to_document(station: StationRecord) -> dict[str, Any]:
    return {
        "osmId": station.osm_id,
        "name": station.name,
        "type": "gas_station",
        "location": {"type": "Point", "coordinates": [station.lon, station.lat]},
        "address": station.address,
        "hasGasoline": station.has_gasoline,
        "hasElectricCharging": station.has_electric_charging,
        "hasRestaurant": station.has_restaurant,
        "updatedAt": datetime.now(timezone.utc),
    }


def restaurant_to_document(restaurant: OsmElement) -> dict[str, Any]:
    return {
        "osmId": restaurant.osm_id,
        "name": build_name(restaurant.tags, "Restaurant"),
        "type": "restaurant",
        "location": {"type": "Point", "coordinates": [restaurant.lon, restaurant.lat]},
        "address": build_address(restaurant.tags),
        "updatedAt": datetime.now(timezone.utc),
    }


def camping_to_document(camp: OsmElement) -> dict[str, Any]:
    # camp_site is tent-oriented and caravan_site is caravan-oriented by
    # default; either can be overridden by an explicit tents=/caravans=
    # tag (e.g. a caravan_site that also allows tents).
    is_caravan_site = camp.tag_value == "caravan_site"
    has_tent_sites = camp.tags.get("tents", "no" if is_caravan_site else "yes") == "yes"
    has_caravan_sites = camp.tags.get("caravans", "yes" if is_caravan_site else "no") == "yes"

    return {
        "osmId": camp.osm_id,
        "name": build_name(camp.tags, "Camping area"),
        "type": "camping",
        "location": {"type": "Point", "coordinates": [camp.lon, camp.lat]},
        "address": build_address(camp.tags),
        "hasTentSites": has_tent_sites,
        "hasCaravanSites": has_caravan_sites,
        "updatedAt": datetime.now(timezone.utc),
    }


def accommodation_to_document(place: OsmElement) -> dict[str, Any]:
    category = "hostel" if place.tag_value == "hostel" else "hotel"
    return {
        "osmId": place.osm_id,
        "name": build_name(place.tags, "Hotel" if category == "hotel" else "Hostel"),
        "type": "accommodation",
        "location": {"type": "Point", "coordinates": [place.lon, place.lat]},
        "address": build_address(place.tags),
        "category": category,
        "updatedAt": datetime.now(timezone.utc),
    }


def import_pois(mongodb_uri: str) -> None:
    print(
        "Fetching gas stations, EV charging points, restaurants, camping areas, and accommodation "
        "in Finland from Overpass API..."
    )
    print("  (one request per category, paced to avoid the public Overpass gateway's rate limit/timeout)")
    fuel_elements = fetch_osm_elements("amenity", "fuel")
    print(f"  fuel: {len(fuel_elements)}")
    time.sleep(5)
    charging_elements = fetch_osm_elements("amenity", "charging_station")
    print(f"  charging_station: {len(charging_elements)}")
    time.sleep(5)
    restaurant_elements = fetch_osm_elements("amenity", "restaurant")
    print(f"  restaurant: {len(restaurant_elements)}")
    time.sleep(5)
    camp_site_elements = fetch_osm_elements("tourism", "camp_site")
    print(f"  camp_site: {len(camp_site_elements)}")
    time.sleep(5)
    caravan_site_elements = fetch_osm_elements("tourism", "caravan_site")
    print(f"  caravan_site: {len(caravan_site_elements)}")
    time.sleep(5)
    hotel_elements = fetch_osm_elements("tourism", "hotel")
    print(f"  hotel: {len(hotel_elements)}")
    time.sleep(5)
    hostel_elements = fetch_osm_elements("tourism", "hostel")
    print(f"  hostel: {len(hostel_elements)}")

    print("Merging co-located fuel/charging points into stations and checking for attached restaurants...")
    stations = merge_fuel_and_charging(fuel_elements, charging_elements, restaurant_elements)

    excluded_count = sum(1 for s in stations if "teboil" in s.name.lower())
    stations = [s for s in stations if "teboil" not in s.name.lower()]
    if excluded_count:
        print(f"Excluding {excluded_count} Teboil station(s).")

    both_count = sum(1 for s in stations if s.has_gasoline and s.has_electric_charging)
    print(
        f"Built {len(stations)} gas stations ({both_count} offer both gasoline and electric charging)."
    )

    camping_elements = camp_site_elements + caravan_site_elements
    print(f"Built {len(camping_elements)} camping areas.")

    accommodation_elements = hotel_elements + hostel_elements
    print(f"Built {len(accommodation_elements)} accommodation places.")

    documents: list[dict[str, Any]] = [station_to_document(s) for s in stations]
    documents += [restaurant_to_document(r) for r in restaurant_elements]
    documents += [camping_to_document(c) for c in camping_elements]
    documents += [accommodation_to_document(a) for a in accommodation_elements]

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
