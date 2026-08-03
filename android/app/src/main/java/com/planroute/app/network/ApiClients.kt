package com.planroute.app.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.planroute.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create

/**
 * Open data services the client talks to directly — no packages/server
 * hop for geocoding, routing, or road-work data:
 *   - Nominatim: address search
 *   - OSRM (public demo instance): route + alternatives
 *   - Digitraffic (Fintraffic): official Finnish road-work situations
 *
 * All three are shared, best-effort public services with modest rate
 * limits (Nominatim and the OSRM demo server both cap at ~1 request/sec
 * and ask for non-bulk, "directly triggered by an end user" traffic).
 * That's fine for a single device tapping "Plan route" by hand, but
 * production deployment should self-host Nominatim/OSRM or move to a
 * commercial provider rather than pointing real user traffic at these.
 *
 * [poiApi] is different: it's this project's own server (gas stations /
 * camping / hotels along a route), address supplied locally via .env —
 * see BuildConfig.SERVER_BASE_URL and .env.example.
 */
private const val PlanRouteUserAgent = "PlanRoute-Android/1.0 (client-only demo build)"

private val sharedHttpClient = OkHttpClient.Builder()
    .addInterceptor(
        Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", PlanRouteUserAgent)
                .build()
            chain.proceed(request)
        },
    )
    .build()

// encodeDefaults matters here: without it, a property left at its declared
// default (e.g. GeoLineStringDto.type = "LineString") is omitted from the
// outgoing JSON entirely rather than sent as that value — which broke
// PoiApi's request, since the server's Zod schema requires that literal
// field to actually be present.
private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun retrofitFor(baseUrl: String): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(sharedHttpClient)
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

val nominatimApi: NominatimApi by lazy { retrofitFor("https://nominatim.openstreetmap.org/").create() }
val osrmApi: OsrmApi by lazy { retrofitFor("https://router.project-osrm.org/").create() }
val digitrafficApi: DigitrafficApi by lazy { retrofitFor("https://tie.digitraffic.fi/").create() }
val poiApi: PoiApi by lazy { retrofitFor(BuildConfig.SERVER_BASE_URL).create() }
