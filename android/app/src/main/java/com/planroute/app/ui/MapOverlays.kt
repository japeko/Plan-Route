package com.planroute.app.ui

import android.graphics.Point
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.planroute.app.model.PoiMarker
import com.planroute.app.model.PoiType
import com.planroute.app.model.ViaStop
import com.planroute.app.repository.PlannedRoute
import com.planroute.app.repository.RoadworkRepository
import com.planroute.app.ui.theme.RouteAltLine
import com.planroute.app.ui.theme.RouteCamping
import com.planroute.app.ui.theme.RouteEnd
import com.planroute.app.ui.theme.RouteHotel
import com.planroute.app.ui.theme.RouteNavAccent
import com.planroute.app.ui.theme.RoutePrimary
import com.planroute.app.ui.theme.RouteStart
import com.planroute.app.ui.theme.RouteWarn
import kotlin.math.roundToInt
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline

/** Fixed points not driven by user input or live data. */
object DemoGeography {
    val MapCenter = GeoPoint(62.15, 25.5)
    val FallbackVehicle = GeoPoint(61.85, 24.75)
}

/**
 * The map surface: a real OSM (osmdroid/Mapnik) tile view plus every
 * overlay the layout proposal calls for — compass, legend, FAB stack,
 * draggable via pins, fixed start/end pins, POI/road-work markers with
 * tappable callouts, and the navigation chrome. Pins and callouts are
 * plain Compose composables positioned each recomposition by projecting
 * their [GeoPoint] through the live [MapView]'s camera, so they track real
 * pan/zoom/rotation. Map-wide taps (dismiss a callout, place a road-work
 * report) go through osmdroid's own [MapEventsOverlay] rather than a
 * Compose-level tap detector, so they don't fight the native pan/zoom
 * gestures. Dragging a via pin calls [onDragViaStop] on every move (so it
 * tracks the finger live) and [onDragViaStopEnd] once, on release, so the
 * caller can re-route through the new position without calling OSRM on
 * every intermediate frame of the drag.
 *
 * Two things are fetched directly from the open data services the client
 * talks to (no packages/server hop): the tiles themselves (osmdroid +
 * Mapnik) and, here, official Digitraffic road-work situations — but only
 * once a route is planned *and* the driver opts in via the "Road works on
 * route" pill; nothing is fetched or shown by default, and results are
 * filtered to a corridor around the selected route rather than whatever
 * happens to be in the current viewport. These are merged with
 * [poiMarkers] (any user-submitted reports the caller owns), [serverPois]
 * (gas stations/camping/hotels along the route — the caller's own POI
 * server, see PoiRepository), and official Digitraffic road-work
 * situations for display. [plannedRoutes] (from OSRM, via the caller) are
 * drawn as native osmdroid polylines.
 */
@Composable
fun ExploreMap(
    modifier: Modifier = Modifier,
    showRoutePins: Boolean,
    isNavigating: Boolean,
    viaStops: List<ViaStop>,
    onDragViaStop: (ViaStop, GeoPoint) -> Unit,
    onDragViaStopEnd: () -> Unit,
    startPosition: GeoPoint?,
    endPosition: GeoPoint?,
    vehiclePosition: GeoPoint,
    poiMarkers: List<PoiMarker>,
    serverPois: List<PoiMarker> = emptyList(),
    onAddPoiAsStop: (PoiMarker) -> Unit,
    onRemoveRoadWorkReport: (PoiMarker) -> Unit,
    isReportingRoadWork: Boolean,
    onMapTapWhileReporting: (GeoPoint) -> Int,
    plannedRoutes: List<PlannedRoute> = emptyList(),
    selectedRouteId: Int? = null,
    canStartNavigation: Boolean,
    onStartNavigation: () -> Unit,
    onSimulateDrive: () -> Unit,
    onToggleReportRoadWork: () -> Unit,
    selectedVoiceLabel: String,
    onOpenVoicePicker: () -> Unit,
    currentSpeedKmh: Double = 0.0,
    vehicleBearingDegrees: Float = 0f,
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val cameraTick = remember { mutableIntStateOf(0) }
    var activeCalloutId by remember { mutableStateOf<Int?>(null) }
    var legendExpanded by remember { mutableStateOf(false) }
    var showRoadWorks by remember { mutableStateOf(false) }
    val officialRoadworks = remember { mutableStateListOf<PoiMarker>() }
    val displayedMarkers = poiMarkers + officialRoadworks + serverPois
    val selectedRoute = plannedRoutes.firstOrNull { it.option.id == selectedRouteId }

    val currentIsReporting = rememberUpdatedState(isReportingRoadWork)
    val currentOnReportTap = rememberUpdatedState(onMapTapWhileReporting)

    // While navigating, a pinch/manual zoom pauses the auto-follow camera
    // for AutoFollowPauseMillis rather than snapping back on the very next
    // fix — 0L means "not paused." Tapping empty map (not a marker; see
    // the MapEventsOverlay below) clears this early to resume immediately.
    val followPausedUntil = remember { mutableStateOf(0L) }
    // The zoom level auto-follow itself last commanded — compared against
    // the map's actual current zoom to detect a user-caused pinch, rather
    // than re-deriving an "expected" zoom from currentSpeedKmh each tick
    // (which would drift from what was last set purely because speed
    // changed, and falsely look like a user zoom).
    val lastCommandedZoom = remember { mutableStateOf<Double?>(null) }

    BoxWithConstraints(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                configureOsmdroid(ctx)
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    minZoomLevel = 5.0
                    maxZoomLevel = 19.0
                    controller.setZoom(6.5)
                    controller.setCenter(DemoGeography.MapCenter)
                    overlays.add(
                        MapEventsOverlay(
                            object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    // A tap here means it landed on the bare
                                    // map, not a marker — those are Compose
                                    // overlays with their own onClick, which
                                    // intercepts the touch before it ever
                                    // reaches this osmdroid-level listener.
                                    followPausedUntil.value = 0L
                                    activeCalloutId = if (currentIsReporting.value) {
                                        currentOnReportTap.value(p)
                                    } else {
                                        null
                                    }
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean = false
                            },
                        ),
                    )
                    addMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                cameraTick.intValue++
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                cameraTick.intValue++
                                return false
                            }
                        },
                    )
                }.also { mapView = it }
            },
            update = { view -> view.syncRouteOverlays(plannedRoutes, selectedRouteId) },
        )

        DisposableEffect(Unit) {
            onDispose { mapView?.onDetach() }
        }

        LaunchedEffect(showRoadWorks, selectedRoute) {
            val route = selectedRoute
            if (!showRoadWorks || route == null) {
                officialRoadworks.clear()
                return@LaunchedEffect
            }
            officialRoadworks.clear()
            officialRoadworks.addAll(RoadworkRepository.roadworksOnRoute(route.geometry))
        }

        // Follows the vehicle while navigating — every live fix (see
        // MainActivity's LocationRepository.trackLocation collector, which
        // is what actually changes vehiclePosition/currentSpeedKmh)
        // re-centers and re-zooms the camera, per "user location is always
        // on center." A manual pinch/zoom pauses this for
        // AutoFollowPauseMillis instead of snapping back on the very next
        // fix — detected by comparing the map's actual zoom against
        // lastCommandedZoom (what we ourselves last set), not a
        // freshly-recomputed "expected" zoom, since that would drift from
        // the last commanded value purely from currentSpeedKmh changing
        // and falsely read as a user zoom. Tapping empty map (see
        // MapEventsOverlay above) or the pause timer elapsing both resume
        // it — either clears followPausedUntil, and the tick after that
        // resume forces a recenter unconditionally rather than re-running
        // the mismatch check (the user's old zoom would still be in
        // effect for that one tick, which would otherwise immediately
        // re-pause it forever).
        LaunchedEffect(isNavigating, vehiclePosition, currentSpeedKmh) {
            if (!isNavigating) return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect
            val now = System.currentTimeMillis()

            if (now < followPausedUntil.value) return@LaunchedEffect

            val wasPaused = followPausedUntil.value != 0L
            if (!wasPaused) {
                val commanded = lastCommandedZoom.value
                if (commanded != null && kotlin.math.abs(mv.zoomLevelDouble - commanded) > ZoomMatchTolerance) {
                    followPausedUntil.value = now + AutoFollowPauseMillis
                    return@LaunchedEffect
                }
            }
            followPausedUntil.value = 0L

            val zoom = zoomForSpeedKmh(currentSpeedKmh)
            mv.controller.setZoom(zoom)
            mv.controller.animateTo(vehiclePosition)
            lastCommandedZoom.value = zoom
        }

        // Fits the selected route's full geometry on screen right after
        // planning (or after picking a different alternative in the
        // comparison list) — otherwise a freshly planned route stays at
        // whatever zoom/center the map happened to be at before. Only
        // while comparing, not navigating: the follow-effect above owns
        // the camera once driving starts.
        LaunchedEffect(selectedRoute, isNavigating) {
            if (isNavigating) return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect
            val geometry = selectedRoute?.geometry ?: return@LaunchedEffect
            if (geometry.isEmpty()) return@LaunchedEffect
            mv.zoomToBoundingBox(BoundingBox.fromGeoPoints(geometry), true, 96)
        }

        val lifecycleOwner = context as? LifecycleOwner
        DisposableEffect(lifecycleOwner, mapView) {
            val owner = lifecycleOwner
            val mv = mapView
            if (owner == null || mv == null) {
                return@DisposableEffect onDispose {}
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mv.onResume()
                    Lifecycle.Event.ON_PAUSE -> mv.onPause()
                    else -> {}
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }

        val mv = mapView
        if (mv != null) {
            // The destination stays visible through navigation too — knowing
            // where you're headed doesn't stop mattering once you start
            // driving. Start/via pins are still COMPARING-only, since they're
            // route-editing affordances that aren't useful mid-drive.
            if (endPosition != null && (showRoutePins || isNavigating)) {
                MapPin(mapView = mv, cameraTick = cameraTick, position = endPosition, color = RouteEnd, size = 20.dp)
            }
            if (showRoutePins && startPosition != null) {
                MapPin(mapView = mv, cameraTick = cameraTick, position = startPosition, color = RouteStart, size = 20.dp)
                viaStops.forEachIndexed { index, stop ->
                    ViaPin(
                        number = index + 1,
                        mapView = mv,
                        cameraTick = cameraTick,
                        stop = stop,
                        onDrag = { newPosition -> onDragViaStop(stop, newPosition) },
                        onDragEnd = onDragViaStopEnd,
                    )
                }
            }

            if (isNavigating) {
                VehicleMarker(
                    mapView = mv,
                    cameraTick = cameraTick,
                    position = vehiclePosition,
                    bearingDegrees = vehicleBearingDegrees,
                )
            }

            if (!isNavigating) {
                displayedMarkers.forEach { marker ->
                    PoiPin(
                        mapView = mv,
                        cameraTick = cameraTick,
                        marker = marker,
                        onClick = { activeCalloutId = if (activeCalloutId == marker.id) null else marker.id },
                    )
                }
                displayedMarkers.firstOrNull { it.id == activeCalloutId }?.let { marker ->
                    PoiCallout(
                        mapView = mv,
                        cameraTick = cameraTick,
                        marker = marker,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        onAddAsStop = {
                            onAddPoiAsStop(marker)
                            activeCalloutId = null
                        },
                        onRemoveReport = {
                            onRemoveRoadWorkReport(marker)
                            activeCalloutId = null
                        },
                    )
                }
            }
        } else {
            Text(
                "Loading map…",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------- Chrome ----------

        if (!isNavigating) {
            // Level with "Start navigation" (same top offset, opposite
            // corner) — see the matching Column below.
            Pill(
                text = selectedVoiceLabel,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                icon = { Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) },
                onClick = onOpenVoicePicker,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 6.dp, start = 12.dp),
            )

            Column(
                // Top padding matches the 6dp gap between pills below
                // (Arrangement.spacedBy), so "Start navigation" sits as far
                // from the status bar as "Voice" sits from "Start
                // navigation" — a consistent rhythm. Both pills move
                // together since they're columns in the same Column.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 6.dp, end = 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(
                    text = "Start navigation",
                    containerColor = RouteNavAccent,
                    contentColor = Color.White,
                    enabled = canStartNavigation,
                    onClick = onStartNavigation,
                )
                // Debug-only — drives navigation off RouteSimulator's
                // fabricated GPS fixes instead of a real location, for
                // testing without actually driving. See RouteSimulator.kt.
                if (canStartNavigation) {
                    Pill(
                        text = "Simulate drive",
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = onSimulateDrive,
                    )
                }
                // Disabled — user-submitted road-work reporting. Re-enable by
                // restoring this Pill; onToggleReportRoadWork/isReportingRoadWork
                // and the map-tap-to-place-report flow are still wired up.
                // Pill(
                //     text = "Report road work",
                //     containerColor = MaterialTheme.colorScheme.surface,
                //     contentColor = MaterialTheme.colorScheme.onSurface,
                //     selected = isReportingRoadWork,
                //     icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = RouteWarn, modifier = Modifier.size(16.dp)) },
                //     onClick = onToggleReportRoadWork,
                // )
            }

            Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp, start = 12.dp)) {
                if (legendExpanded) {
                    LegendPopover(modifier = Modifier.padding(bottom = 8.dp))
                }
                Pill(
                    text = if (legendExpanded) "Legend ▾" else "Legend ▸",
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { legendExpanded = !legendExpanded },
                )
            }

            if (showRoutePins && selectedRoute != null) {
                Pill(
                    text = if (showRoadWorks) "Hide road works" else "Road works on route",
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    selected = showRoadWorks,
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = RouteWarn, modifier = Modifier.size(16.dp)) },
                    onClick = { showRoadWorks = !showRoadWorks },
                    // Same bottom padding as the Legend pill (bottom-start),
                    // so both sit at the same height — just opposite corners.
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp, end = 12.dp),
                )
            }
        }
        // Stop lives inside NavigationBanner itself now (see MainActivity) —
        // it used to float here too, overlapping the banner's turn text. No
        // Recenter pill either: the camera always follows while navigating
        // (see the LaunchedEffect above), so there's never anywhere to
        // recenter back from.

        if (isNavigating) {
            // Bottom-left, not top-left: MainActivity draws NavigationBanner
            // (full-width, top-anchored) as a sibling on top of ExploreMap,
            // so a top-left compass here would just render underneath it,
            // completely hidden — confirmed by an on-device screenshot.
            CompassButton(
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(bottom = 14.dp, start = 12.dp),
            )

            // No background/Surface here on purpose — the map should stay
            // visible behind it. The dark text shadow carries legibility
            // instead, over whatever tiles/roads happen to be underneath.
            Text(
                "${currentSpeedKmh.roundToInt()} km/h",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp),
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    shadow = Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 2f), blurRadius = 10f),
                ),
            )
        }
    }
}

/**
 * Zoomed in enough to read street names at city speeds, progressively
 * wider (lower zoom number = more area visible) as speed climbs, so a
 * driver going faster sees further down the road rather than a
 * close-up that's scrolling past too quickly to read. Breakpoints match
 * the requirement: 50/70/90/110 km/h.
 */
private fun zoomForSpeedKmh(speedKmh: Double): Double = when {
    speedKmh < 50 -> 17.0
    speedKmh < 70 -> 16.0
    speedKmh < 90 -> 15.0
    speedKmh < 110 -> 14.5
    else -> 14.0
}

/** How long a manual pinch/zoom pauses the auto-follow camera before it resumes on its own. */
private const val AutoFollowPauseMillis = 60_000L

/** Zoom-level difference beyond which the map's actual zoom no longer counts as matching what auto-follow last commanded — i.e. the user must have pinched. */
private const val ZoomMatchTolerance = 0.3

/** One-time osmdroid setup: a distinct user agent (required by OSM's tile usage policy) and an app-private tile cache. */
private var osmdroidConfigured = false

private fun configureOsmdroid(context: android.content.Context) {
    if (osmdroidConfigured) return
    osmdroidConfigured = true
    val basePath = java.io.File(context.filesDir, "osmdroid")
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = basePath
        osmdroidTileCache = java.io.File(basePath, "tiles")
    }
}

private fun MapView.screenOffsetPx(point: GeoPoint): IntOffset {
    val out = Point()
    projection.toPixels(point, out)
    return IntOffset(out.x, out.y)
}

/** Redraws OSRM route alternatives as native osmdroid polylines — selected route solid, others dashed. */
private fun MapView.syncRouteOverlays(routes: List<PlannedRoute>, selectedRouteId: Int?) {
    overlays.removeAll { it is Polyline }
    routes.forEach { planned ->
        val isSelected = planned.option.id == selectedRouteId
        // The no-arg constructor is deliberate: Polyline(mapView) auto-attaches
        // a default InfoWindow, so tapping the line pops up an empty bubble
        // (no title/snippet was ever set). Setting infoWindow = null too,
        // belt-and-braces, in case a future osmdroid version changes that.
        val line = Polyline().apply {
            infoWindow = null
            setPoints(planned.geometry)
            outlinePaint.color = (if (isSelected) RoutePrimary else RouteAltLine).toArgb()
            outlinePaint.strokeWidth = if (isSelected) 10f else 6f
            outlinePaint.pathEffect = if (isSelected) null else android.graphics.DashPathEffect(floatArrayOf(16f, 12f), 0f)
        }
        overlays.add(line)
    }
    invalidate()
}

/**
 * All pin/callout placement below uses the lambda form of `Modifier.offset`,
 * reading [cameraTick] as the first thing inside the lambda. That's not
 * cosmetic: `cameraTick` is the only thing that changes on pan/zoom (the
 * pins' own parameters — an osmdroid [MapView] and a [GeoPoint], neither
 * Compose-stable — don't), and a plain composition-time computation of
 * `mapView.screenOffsetPx(...)` was found to go stale under pan/zoom,
 * because the parent recomposing doesn't guarantee these calls
 * re-execute. Reading state *inside* the offset lambda ties recomputation
 * directly to layout invalidation instead, independent of recomposition.
 */
private fun Modifier.pinOffset(mapView: MapView, cameraTick: IntState, position: GeoPoint, size: Dp): Modifier =
    this.offset {
        cameraTick.intValue
        val px = mapView.screenOffsetPx(position)
        val half = size.roundToPx() / 2
        IntOffset(px.x - half, px.y - half)
    }

/** A ringed dot pin (start/end) projected from a real map coordinate. */
@Composable
private fun MapPin(mapView: MapView, cameraTick: IntState, position: GeoPoint, color: Color, size: Dp) {
    Box(
        modifier = Modifier
            .pinOffset(mapView, cameraTick, position, size)
            .size(size)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(size - 4.dp).background(color, CircleShape))
    }
}

/**
 * A numbered, draggable via-stop pin. Dragging reprojects the pointer
 * delta back to a [GeoPoint] on every move (so the pin tracks the finger
 * live); [onDragEnd] fires once, when the finger lifts, so the caller can
 * re-route through the new position without hammering OSRM on every
 * intermediate move.
 */
@Composable
private fun ViaPin(
    number: Int,
    mapView: MapView,
    cameraTick: IntState,
    stop: ViaStop,
    onDrag: (GeoPoint) -> Unit,
    onDragEnd: () -> Unit,
) {
    val size = 24.dp
    // pointerInput(mapView) only relaunches this coroutine once mapView is
    // set (it never changes again), so the drag/drag-end callbacks must be
    // read via rememberUpdatedState rather than captured directly — same
    // reasoning as Modifier.clickable's own onClick handling.
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .pinOffset(mapView, cameraTick, stop.position, size)
            .size(size)
            .background(RoutePrimary, CircleShape)
            .pointerInput(mapView) {
                detectDragGestures(
                    onDragEnd = { currentOnDragEnd.value() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentPx = mapView.screenOffsetPx(stop.position)
                        val newPx = IntOffset(
                            (currentPx.x + dragAmount.x).roundToInt(),
                            (currentPx.y + dragAmount.y).roundToInt(),
                        )
                        val newGeoPoint = GeoPoint(mapView.projection.fromPixels(newPx.x, newPx.y))
                        currentOnDrag.value(newGeoPoint)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private val PoiType.icon
    get() = when (this) {
        PoiType.GAS -> Icons.Filled.LocalGasStation
        PoiType.CAMPING -> Icons.Filled.Terrain
        PoiType.HOTEL -> Icons.Filled.Hotel
        PoiType.ROAD_WORK -> Icons.Filled.Warning
    }

private val PoiType.tint
    get() = when (this) {
        PoiType.GAS -> RoutePrimary
        PoiType.CAMPING -> RouteCamping
        PoiType.HOTEL -> RouteHotel
        PoiType.ROAD_WORK -> RouteWarn
    }

@Composable
private fun PoiPin(mapView: MapView, cameraTick: IntState, marker: PoiMarker, onClick: () -> Unit) {
    val size = if (marker.type == PoiType.ROAD_WORK) 26.dp else 24.dp
    Box(
        modifier = Modifier
            .pinOffset(mapView, cameraTick, marker.position, size)
            .size(size)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            marker.type.icon,
            contentDescription = marker.title,
            tint = marker.type.tint,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** A directional arrow puck (Google/Apple Maps convention) instead of a plain dot, rotated to the GPS heading so it reads as "which way am I facing," not just "where am I." */
@Composable
private fun VehicleMarker(mapView: MapView, cameraTick: IntState, position: GeoPoint, bearingDegrees: Float) {
    val size = 34.dp
    Box(
        modifier = Modifier
            .pinOffset(mapView, cameraTick, position, size)
            .size(size)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Navigation,
            contentDescription = "Your position",
            tint = RoutePrimary,
            modifier = Modifier.size(size - 8.dp).rotate(bearingDegrees),
        )
    }
}

@Composable
private fun PoiCallout(
    mapView: MapView,
    cameraTick: IntState,
    marker: PoiMarker,
    maxWidth: Dp,
    maxHeight: Dp,
    onAddAsStop: () -> Unit,
    onRemoveReport: () -> Unit,
) {
    val calloutWidth = 176.dp
    val calloutModifier = Modifier.offset {
        cameraTick.intValue
        val px = mapView.screenOffsetPx(marker.position)
        val calloutWidthPx = calloutWidth.roundToPx()
        val marginPx = 8.dp.roundToPx()
        val minX = marginPx
        val maxX = (maxWidth.roundToPx() - calloutWidthPx - marginPx).coerceAtLeast(minX)
        val minY = marginPx
        val maxY = (maxHeight.roundToPx() - 140.dp.roundToPx()).coerceAtLeast(minY)
        val rawX = px.x - calloutWidthPx / 2
        val rawY = px.y + 20.dp.roundToPx()
        IntOffset(rawX.coerceIn(minX, maxX), rawY.coerceIn(minY, maxY))
    }

    Card(modifier = calloutModifier.width(calloutWidth)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(marker.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(marker.description, style = MaterialTheme.typography.bodySmall)
            when {
                marker.type == PoiType.GAS || marker.type == PoiType.CAMPING || marker.type == PoiType.HOTEL -> Pill(
                    text = "Add as stop",
                    modifier = Modifier.padding(top = 8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = RoutePrimary,
                    fillWidth = true,
                    onClick = onAddAsStop,
                )
                // Disabled along with road-work reporting (see the commented-out
                // "Report road work" Pill above) — user reports can no longer be
                // created, so this callout action is unreachable; left in place
                // for symmetry with re-enabling that feature.
                // marker.type == PoiType.ROAD_WORK && marker.isUserReport -> Pill(
                //     text = "Remove report",
                //     modifier = Modifier.padding(top = 8.dp),
                //     containerColor = MaterialTheme.colorScheme.errorContainer,
                //     contentColor = RouteEnd,
                //     fillWidth = true,
                //     onClick = onRemoveReport,
                // )
                // Official Digitraffic road work: informational only, no action.
            }
        }
    }
}

@Composable
private fun CompassButton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Explore, contentDescription = "Compass", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun LegendPopover(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendDotRow("Start", RouteStart)
            LegendDotRow("Route / via", RoutePrimary)
            LegendDotRow("End", RouteEnd)
            LegendIconRow("Gas stations", PoiType.GAS)
            LegendIconRow("Camping", PoiType.CAMPING)
            LegendIconRow("Hotels & hostels", PoiType.HOTEL)
            LegendIconRow("Road work", PoiType.ROAD_WORK)
        }
    }
}

@Composable
private fun LegendDotRow(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Matches how [PoiPin] actually renders that type — a white circle with the type's icon/tint. */
@Composable
private fun LegendIconRow(label: String, type: PoiType) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon, contentDescription = null, tint = type.tint, modifier = Modifier.size(11.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Small rounded chrome button shared by the FAB stack, legend, stop/recenter, and callouts. */
@Composable
private fun Pill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    fillWidth: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(10.dp),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.6f),
        shadowElevation = if (selected) 0.dp else 4.dp,
        border = if (selected) BorderStroke(1.5.dp, contentColor) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.spacedBy(6.dp),
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
