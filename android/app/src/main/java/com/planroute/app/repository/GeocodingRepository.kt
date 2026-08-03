package com.planroute.app.repository

import com.planroute.app.network.nominatimApi
import java.io.IOException
import org.osmdroid.util.GeoPoint
import retrofit2.HttpException

/** Wraps Nominatim address search/reverse geocoding — called directly by the client, not proxied through packages/server. */
object GeocodingRepository {
    suspend fun search(query: String): GeoPoint? {
        if (query.isBlank()) return null
        return try {
            nominatimApi.search(query = query, countryCodes = "fi")
                .firstOrNull()
                ?.let { GeoPoint(it.lat.toDouble(), it.lon.toDouble()) }
        } catch (e: IOException) {
            null
        } catch (e: HttpException) {
            null
        }
    }

    /** Human-readable address for a point — used to fill in the "use my current location" field. */
    suspend fun reverseGeocode(point: GeoPoint): String? {
        return try {
            nominatimApi.reverse(lat = point.latitude, lon = point.longitude).displayName
        } catch (e: IOException) {
            null
        } catch (e: HttpException) {
            null
        }
    }
}
