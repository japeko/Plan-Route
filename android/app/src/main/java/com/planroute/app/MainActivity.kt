package com.planroute.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.planroute.app.model.GasAmenity
import com.planroute.app.model.PoiMarker
import com.planroute.app.model.PoiType
import com.planroute.app.model.SheetMode
import com.planroute.app.model.ViaStop
import com.planroute.app.repository.GeocodingRepository
import com.planroute.app.repository.LocationRepository
import com.planroute.app.repository.PlannedRoute
import com.planroute.app.repository.PoiRepository
import com.planroute.app.repository.PoiSearchSettings
import com.planroute.app.repository.RouteSimulator
import com.planroute.app.repository.RoutingRepository
import com.planroute.app.ui.DemoGeography
import com.planroute.app.ui.ExploreMap
import com.planroute.app.ui.PlannerSheetContent
import com.planroute.app.ui.PoiFilterState
import com.planroute.app.ui.theme.PlanRouteTheme
import com.planroute.app.ui.theme.RouteInk
import com.planroute.app.ui.theme.RouteNavAccent
import com.planroute.app.voice.NavigationVoiceController
import com.planroute.app.voice.NavigationVoiceOption
import com.planroute.app.voice.TargetVoiceLanguages
import com.planroute.app.voice.aheadAnnouncement
import com.planroute.app.voice.arrowGlyphFor
import com.planroute.app.voice.localizedInstruction
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanRouteTheme {
                PlanRouteApp()
            }
        }
    }
}

/**
 * Top-level screen shell, following the five-screen flow from the Android
 * layout proposal: a full-screen map behind a draggable bottom sheet that
 * peeks (Explore), expands into the planning form (Plan), swaps to route
 * alternatives once planned (Compare), and gives way entirely to turn
 * banner + vehicle chrome while driving (Navigate) — with POI/road-work
 * markers tappable into an "add as stop" / "remove report" callout (Tap a
 * marker) throughout.
 *
 * The map surface is real (osmdroid + OSM/Mapnik tiles), and so are the
 * services behind it — [com.planroute.app.repository.GeocodingRepository]
 * (Nominatim), [com.planroute.app.repository.RoutingRepository] (OSRM),
 * and [com.planroute.app.repository.RoadworkRepository] (Digitraffic) all
 * talk to those open data services directly from the client, no
 * packages/server hop. Spoken guidance ([NavigationVoiceController]) wraps
 * Android's on-device TextToSpeech engine, limited to Finnish/English/
 * Swedish voices. Turn-by-turn instructions are parsed from OSRM's route
 * steps and matched against the live GPS position as it's tracked (see the
 * `updateNavigationProgress` local function below): [NavigationBanner]
 * shows and speaks each upcoming maneuver as the vehicle nears it, in
 * whichever of fi/en/sv the selected voice uses. A proper ViewModel layer
 * (this is still plain Activity-scoped Compose state) remains follow-up
 * work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanRouteApp() {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val voiceController = remember { NavigationVoiceController(context) }
    DisposableEffect(Unit) { onDispose { voiceController.shutdown() } }
    var selectedVoice by remember { mutableStateOf<NavigationVoiceOption?>(null) }
    var showVoicePicker by remember { mutableStateOf(false) }
    // Auto-pick a voice once the TTS engine is ready, so navigation always
    // has one selected without the user having to open the picker first.
    // Finnish is preferred; falls back to whatever's first if no Finnish
    // voice is installed.
    LaunchedEffect(voiceController.isReady) {
        if (voiceController.isReady && selectedVoice == null) {
            val voices = voiceController.availableVoices()
            val voice = voices.firstOrNull { it.voice.locale.language in setOf("fi", "fin") } ?: voices.firstOrNull()
            if (voice != null) {
                selectedVoice = voice
                voiceController.selectVoice(voice)
            }
        }
    }

    var mode by remember { mutableStateOf(SheetMode.PLANNING) }
    var isNavigating by remember { mutableStateOf(false) }
    // Keeps the screen on for the duration of a trip — a phone that dims
    // or locks mid-navigation is worse than useless. Cleared automatically
    // once navigation stops (or the screen would stay forced-on forever).
    val view = LocalView.current
    DisposableEffect(isNavigating) {
        view.keepScreenOn = isNavigating
        onDispose { view.keepScreenOn = false }
    }
    // Debug-only: drives isNavigating off RouteSimulator's fabricated
    // fixes instead of real GPS — see the LaunchedEffect(isNavigating)
    // below, which branches on this to pick the location source.
    var isSimulatingDrive by remember { mutableStateOf(false) }
    // Debug-only: when the simulated drive is running, also has it drift
    // off the route for a stretch — for exercising the off-route
    // detour-back feature without an actual wrong turn.
    var isSimulatingOffRoute by remember { mutableStateOf(false) }

    // Live position/speed while driving — ExploreMap uses both to keep the
    // vehicle centered and to pick a zoom level for the current speed (see
    // zoomForSpeedKmh). Cleared on stop so the map falls back to the
    // route's own start/geometry point when next explored.
    var liveVehiclePosition by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmh by remember { mutableStateOf(0.0) }
    var vehicleBearingDegrees by remember { mutableStateOf(0f) }

    // Progress through the selected route's turn-by-turn steps, advanced as
    // the vehicle travels along the route (see updateNavigationProgress
    // inside the LaunchedEffect(isNavigating) below, which reads
    // plannedRoutes/selectedRouteId). Drives both NavigationBanner's
    // text/distance and when spoken announcements fire.
    var currentStepIndex by remember { mutableStateOf(0) }
    var preAnnouncedStepIndex by remember { mutableStateOf(-1) }
    var announcedStepIndex by remember { mutableStateOf(-1) }
    var navInstruction by remember { mutableStateOf("") }
    var navDistanceMeters by remember { mutableStateOf<Int?>(null) }
    var navManeuverModifier by remember { mutableStateOf<String?>(null) }
    // The route back onto the plan once the driver strays off it — see the
    // off-route detection inside LaunchedEffect(isNavigating) below. Drawn
    // by ExploreMap alongside the planned route, not instead of it; empty
    // means "on route" or "not navigating."
    var detourGeometry by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    var startPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var endPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var nextStopId by remember { mutableStateOf(0) }
    val viaStops = remember { mutableStateListOf<ViaStop>() }
    // Gas stations on by default; camping and hotels are opt-in via the
    // filter chips rather than shown automatically.
    val selectedFilters = remember { mutableStateListOf("Gas stations") }

    val plannedRoutes = remember { mutableStateListOf<PlannedRoute>() }
    var selectedRouteId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
            liveVehiclePosition = null
            currentSpeedKmh = 0.0
            isSimulatingDrive = false
            isSimulatingOffRoute = false
            detourGeometry = emptyList()
            return@LaunchedEffect
        }
        val route = plannedRoutes.firstOrNull { it.option.id == selectedRouteId }
        val steps = route?.steps ?: emptyList()
        val geometry = route?.geometry ?: emptyList()

        // Distance traveled *along the route's own path* rather than raw
        // straight-line distance to each maneuver's coordinate — matching
        // against a fixed radius around that raw point turned out fragile:
        // depending on exactly where OSRM places the maneuver coordinate
        // relative to the road, the closest approach could land either
        // outside the arrival radius (the step then never counted as
        // reached, leaving the banner frozen on a turn already made) or
        // well before the vehicle had actually completed the turn
        // (advancing to the next instruction too early). Progress along
        // the path is monotonic, so neither failure mode can happen: it's
        // always eventually >= any threshold ahead of it, and never
        // "arrives" before actually passing that point on the road.
        val cumulative = DoubleArray(geometry.size)
        for (i in 1 until geometry.size) {
            cumulative[i] = cumulative[i - 1] + geometry[i - 1].distanceToAsDouble(geometry[i])
        }
        fun distanceAlongRoute(point: GeoPoint): Double {
            if (geometry.isEmpty()) return 0.0
            val nearestIndex = geometry.indices.minByOrNull { point.distanceToAsDouble(geometry[it]) } ?: 0
            return cumulative[nearestIndex]
        }
        // The final ("arrive") step's threshold is forced to the route's
        // exact total length rather than nearest-vertex-matched like every
        // other step: OSRM's route geometry always terminates precisely at
        // the destination coordinate that was routed to, which is a firmer
        // ground truth than the arrive maneuver's own location field —
        // nearest-vertex matching that field landed short by 50-100m
        // against a specific street-address destination in testing,
        // announcing "arrived" while still that far away.
        val stepThresholds = steps.mapIndexed { index, step ->
            if (index == steps.lastIndex) cumulative.lastOrNull() ?: 0.0 else distanceAlongRoute(step.location)
        }

        // Step 0 is "depart" — its maneuver location is the starting point
        // itself, which the vehicle is already at, so start at step 1 to
        // avoid an immediate spurious "arrival" the moment nav begins.
        currentStepIndex = if (steps.size > 1) 1 else 0
        preAnnouncedStepIndex = -1
        announcedStepIndex = -1
        navInstruction = steps.getOrNull(currentStepIndex)
            ?.localizedInstruction(selectedVoice?.voice?.locale?.language) ?: ""
        navManeuverModifier = steps.getOrNull(currentStepIndex)?.maneuverModifier
        navDistanceMeters = null

        fun updateNavigationProgress(position: GeoPoint) {
            if (steps.isEmpty() || currentStepIndex >= steps.size) return
            val languageCode = selectedVoice?.voice?.locale?.language
            val progress = distanceAlongRoute(position)

            // A loop, not a single `if`: a slow update tick (or a cluster
            // of very short steps, e.g. inside a roundabout) could mean
            // progress jumps past more than one step's threshold at once.
            while (currentStepIndex < steps.lastIndex && progress >= stepThresholds[currentStepIndex]) {
                currentStepIndex += 1
                preAnnouncedStepIndex = -1
            }

            val step = steps[currentStepIndex]
            val distance = (stepThresholds[currentStepIndex] - progress).coerceAtLeast(0.0).roundToInt()
            navInstruction = step.localizedInstruction(languageCode)
            navManeuverModifier = step.maneuverModifier
            navDistanceMeters = distance

            when {
                distance <= NavArriveThresholdMeters -> {
                    if (announcedStepIndex != currentStepIndex) {
                        voiceController.speak(navInstruction)
                        announcedStepIndex = currentStepIndex
                    }
                }
                distance <= NavPreAnnounceThresholdMeters -> {
                    if (preAnnouncedStepIndex != currentStepIndex) {
                        voiceController.speak(aheadAnnouncement(navInstruction, distance, languageCode))
                        preAnnouncedStepIndex = currentStepIndex
                    }
                }
            }
        }

        // If the vehicle strays more than OffRouteThresholdMeters from the
        // planned route's own path, compute a route from here back onto
        // it — rejoining ahead of current progress, never behind it, so
        // the detour doesn't send the driver backward — and show that as
        // a second polyline (see ExploreMap's detourGeometry param). The
        // planned route itself is never touched: turn-by-turn progress
        // above keeps comparing against it exactly as before, and once
        // the driver is back within range the detour line is cleared.
        suspend fun updateDetour(position: GeoPoint) {
            if (geometry.isEmpty()) return
            val offRouteDistance = geometry.minOf { position.distanceToAsDouble(it) }
            if (offRouteDistance <= OffRouteThresholdMeters) {
                if (detourGeometry.isNotEmpty()) detourGeometry = emptyList()
                return
            }
            if (detourGeometry.isNotEmpty()) return

            val progress = distanceAlongRoute(position)
            val rejoinPoint = geometry.indices
                .filter { cumulative[it] >= progress }
                .minByOrNull { position.distanceToAsDouble(geometry[it]) }
                ?.let { geometry[it] }
                ?: geometry.last()
            detourGeometry = RoutingRepository.route(listOf(position, rejoinPoint)).firstOrNull()?.geometry ?: emptyList()
        }

        val locations = if (isSimulatingDrive && route != null) {
            RouteSimulator.simulateDrive(route, simulateOffRoute = isSimulatingOffRoute)
        } else {
            LocationRepository.trackLocation(context)
        }
        locations.collect { location ->
            val position = GeoPoint(location.latitude, location.longitude)
            liveVehiclePosition = position
            if (location.hasSpeed()) {
                currentSpeedKmh = location.speed * 3.6
            }
            if (location.hasBearing()) {
                vehicleBearingDegrees = location.bearing
            }
            updateNavigationProgress(position)
            updateDetour(position)
        }
    }

    var isPlanning by remember { mutableStateOf(false) }
    var planningError by remember { mutableStateOf<String?>(null) }

    var nextMarkerId by remember { mutableStateOf(0) }
    val poiMarkers = remember { mutableStateListOf<PoiMarker>() }
    var isReportingRoadWork by remember { mutableStateOf(false) }

    // "Along the route" POIs from this project's own server (see .env /
    // PoiRepository) — gas stations, camping areas, hotels/hostels. Distances
    // are the defaults from the spec; sliders in the sheet let the user widen
    // or narrow each independently.
    var gasMaxDistanceMeters by remember { mutableStateOf(500f) }
    // Gasoline/electric are OR'd server-side (either is fine), but checking
    // "Restaurant" maps to onlyWithRestaurant, an AND filter that excludes
    // every station without one — most don't have one, so defaulting it
    // checked made "Gas stations" show almost nothing by default. Only the
    // fuel-type amenities are on by default; restaurant stays opt-in.
    val gasAmenities = remember { mutableStateListOf(GasAmenity.GASOLINE, GasAmenity.ELECTRIC) }
    var campingMaxDistanceMeters by remember { mutableStateOf(10_000f) }
    var hotelMaxDistanceMeters by remember { mutableStateOf(5_000f) }
    val serverPois = remember { mutableStateListOf<PoiMarker>() }

    val refreshServerPois: () -> Unit = {
        val route = plannedRoutes.firstOrNull { it.option.id == selectedRouteId }
        if (route == null) {
            serverPois.clear()
        } else {
            scope.launch {
                val results = PoiRepository.searchAlongRoute(
                    routeGeometry = route.geometry,
                    settings = PoiSearchSettings(
                        showGasStations = "Gas stations" in selectedFilters,
                        gasMaxDistanceMeters = gasMaxDistanceMeters.toDouble(),
                        gasAmenities = gasAmenities.toSet(),
                        showRestaurants = "Restaurants" in selectedFilters,
                        showCamping = "Camping" in selectedFilters,
                        campingMaxDistanceMeters = campingMaxDistanceMeters.toDouble(),
                        showAccommodation = "Hotels" in selectedFilters,
                        accommodationMaxDistanceMeters = hotelMaxDistanceMeters.toDouble(),
                    ),
                )
                serverPois.clear()
                serverPois.addAll(results)
            }
        }
    }
    val filterState = PoiFilterState(
        gasMaxDistanceMeters = gasMaxDistanceMeters,
        gasAmenities = gasAmenities.toSet(),
        campingMaxDistanceMeters = campingMaxDistanceMeters,
        hotelMaxDistanceMeters = hotelMaxDistanceMeters,
        onGasDistanceChange = { gasMaxDistanceMeters = it },
        onGasDistanceCommit = refreshServerPois,
        onToggleGasAmenity = { amenity ->
            if (amenity in gasAmenities) gasAmenities.remove(amenity) else gasAmenities.add(amenity)
            refreshServerPois()
        },
        onCampingDistanceChange = { campingMaxDistanceMeters = it },
        onCampingDistanceCommit = refreshServerPois,
        onHotelDistanceChange = { hotelMaxDistanceMeters = it },
        onHotelDistanceCommit = refreshServerPois,
    )

    var isLocatingUser by remember { mutableStateOf(false) }
    val locateUser: () -> Unit = {
        scope.launch {
            isLocatingUser = true
            planningError = null
            val point = LocationRepository.currentLocation(context)
            if (point == null) {
                planningError = "Could not get your location"
                isLocatingUser = false
            } else {
                startAddress = GeocodingRepository.reverseGeocode(point)
                    ?: "%.5f, %.5f".format(point.latitude, point.longitude)
                isLocatingUser = false
            }
        }
    }
    // Shared by both "use my current location" (one-shot) and "start
    // navigation" (needs continuous tracking) — whichever asked gets
    // resumed once the permission result comes back.
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            pendingLocationAction?.invoke()
        } else {
            planningError = "Location permission denied"
        }
        pendingLocationAction = null
    }
    val requestLocationPermissionThen: (() -> Unit) -> Unit = { action ->
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }
    val onUseCurrentLocation: () -> Unit = { requestLocationPermissionThen(locateUser) }

    // Dragging a via pin (see ExploreMap.onDragViaStopEnd) re-routes through
    // its new position once the finger lifts. A failed/empty result is
    // ignored silently — the previously planned route just stays put — since
    // this is a background convenience update, not a user-initiated action
    // with its own error affordance like "Plan route" has.
    val rerouteViaCurrentPositions: () -> Unit = {
        val start = startPosition
        val end = endPosition
        if (start != null && end != null) {
            scope.launch {
                val results = RoutingRepository.route(
                    listOf(start) + viaStops.map { it.position } + listOf(end),
                    destinationLabel = endAddress,
                )
                if (results.isNotEmpty()) {
                    plannedRoutes.clear()
                    plannedRoutes.addAll(results)
                    selectedRouteId = results.first().option.id
                    refreshServerPois()
                }
            }
        }
    }

    val isPeeking = scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = when {
            isNavigating -> 0.dp
            mode == SheetMode.COMPARING -> 150.dp
            else -> 110.dp
        },
        sheetContent = {
            if (!isNavigating) {
                PlannerSheetContent(
                    mode = mode,
                    isPeeking = isPeeking,
                    startAddress = startAddress,
                    onStartAddressChange = { startAddress = it },
                    endAddress = endAddress,
                    onEndAddressChange = { endAddress = it },
                    viaStops = viaStops,
                    onAddViaStop = {
                        val stop = ViaStop(
                            id = nextStopId,
                            initialPosition = GeoPoint(
                                DemoGeography.MapCenter.latitude + 0.15 * (viaStops.size % 4),
                                DemoGeography.MapCenter.longitude,
                            ),
                        )
                        viaStops.add(stop)
                        nextStopId += 1
                    },
                    onRemoveViaStop = { viaStops.remove(it) },
                    onViaAddressChange = { stop, value -> stop.address = value },
                    selectedFilters = selectedFilters.toSet(),
                    onToggleFilter = { label ->
                        if (label in selectedFilters) selectedFilters.remove(label) else selectedFilters.add(label)
                        refreshServerPois()
                    },
                    filterState = filterState,
                    canPlanRoute = startAddress.isNotBlank() && endAddress.isNotBlank(),
                    isPlanning = isPlanning,
                    planningError = planningError,
                    onPlanRoute = {
                        scope.launch {
                            isPlanning = true
                            planningError = null

                            val startGeo = GeocodingRepository.search(startAddress)
                            if (startGeo == null) {
                                planningError = "Could not find \"$startAddress\""
                                isPlanning = false
                                return@launch
                            }
                            val endGeo = GeocodingRepository.search(endAddress)
                            if (endGeo == null) {
                                planningError = "Could not find \"$endAddress\""
                                isPlanning = false
                                return@launch
                            }
                            val viaGeos = viaStops.map { stop ->
                                if (stop.address.isNotBlank()) {
                                    GeocodingRepository.search(stop.address)?.also { stop.position = it }
                                        ?: stop.position
                                } else {
                                    stop.position
                                }
                            }

                            val results = RoutingRepository.route(
                                listOf(startGeo) + viaGeos + endGeo,
                                destinationLabel = endAddress,
                            )
                            if (results.isEmpty()) {
                                planningError = "Could not calculate a route between those stops"
                                isPlanning = false
                                return@launch
                            }

                            startPosition = startGeo
                            endPosition = endGeo
                            plannedRoutes.clear()
                            plannedRoutes.addAll(results)
                            selectedRouteId = results.first().option.id
                            mode = SheetMode.COMPARING
                            isPlanning = false
                            refreshServerPois()
                            scaffoldState.bottomSheetState.partialExpand()
                        }
                    },
                    onClear = {
                        startAddress = ""
                        endAddress = ""
                        startPosition = null
                        endPosition = null
                        viaStops.clear()
                        selectedFilters.clear()
                        plannedRoutes.clear()
                        selectedRouteId = null
                        planningError = null
                        serverPois.clear()
                        mode = SheetMode.PLANNING
                    },
                    isLocatingUser = isLocatingUser,
                    onUseCurrentLocation = onUseCurrentLocation,
                    routeOptions = plannedRoutes.map { it.option },
                    selectedRouteId = selectedRouteId,
                    onSelectRoute = { selectedRouteId = it; refreshServerPois() },
                    onBackToPlanning = { mode = SheetMode.PLANNING },
                    selectedRouteSteps = plannedRoutes.firstOrNull { it.option.id == selectedRouteId }?.steps
                        ?: emptyList(),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ExploreMap(
                modifier = Modifier.fillMaxSize(),
                showRoutePins = mode == SheetMode.COMPARING && !isNavigating,
                isNavigating = isNavigating,
                viaStops = viaStops,
                onDragViaStop = { stop, newPosition -> stop.position = newPosition },
                onDragViaStopEnd = rerouteViaCurrentPositions,
                startPosition = startPosition,
                endPosition = endPosition,
                vehiclePosition = liveVehiclePosition
                    ?: plannedRoutes.firstOrNull { it.option.id == selectedRouteId }?.geometry?.firstOrNull()
                    ?: startPosition
                    ?: DemoGeography.FallbackVehicle,
                poiMarkers = poiMarkers,
                serverPois = serverPois,
                onAddPoiAsStop = { marker ->
                    viaStops.add(ViaStop(id = nextStopId, address = marker.title, initialPosition = marker.position))
                    nextStopId += 1
                    rerouteViaCurrentPositions()
                },
                onRemoveRoadWorkReport = { marker -> poiMarkers.remove(marker) },
                isReportingRoadWork = isReportingRoadWork,
                onMapTapWhileReporting = { tappedPosition ->
                    val marker = PoiMarker(
                        id = nextMarkerId,
                        type = PoiType.ROAD_WORK,
                        title = "Road work",
                        description = "User-reported · just now",
                        position = tappedPosition,
                        isUserReport = true,
                    )
                    nextMarkerId += 1
                    poiMarkers.add(marker)
                    isReportingRoadWork = false
                    marker.id
                },
                plannedRoutes = plannedRoutes,
                selectedRouteId = selectedRouteId,
                detourGeometry = detourGeometry,
                canStartNavigation = mode == SheetMode.COMPARING,
                onStartNavigation = { requestLocationPermissionThen { isNavigating = true } },
                onSimulateDrive = { isSimulatingDrive = true; isNavigating = true },
                onSimulateOffRoute = { isSimulatingDrive = true; isSimulatingOffRoute = true; isNavigating = true },
                onToggleReportRoadWork = { isReportingRoadWork = !isReportingRoadWork },
                selectedVoiceLabel = selectedVoice?.label?.let { "Voice: $it" } ?: "Voice",
                onOpenVoicePicker = { showVoicePicker = true },
                currentSpeedKmh = currentSpeedKmh,
                vehicleBearingDegrees = vehicleBearingDegrees,
            )

            if (isNavigating) {
                NavigationBanner(
                    instruction = navInstruction.ifBlank { "Continue" },
                    arrow = arrowGlyphFor(navManeuverModifier),
                    distanceMeters = navDistanceMeters,
                    onStop = { isNavigating = false },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
    }

    if (showVoicePicker) {
        VoicePickerDialog(
            voices = voiceController.availableVoices(),
            selected = selectedVoice,
            onSelect = { option ->
                selectedVoice = option
                voiceController.selectVoice(option)
                showVoicePicker = false
            },
            onDismiss = { showVoicePicker = false },
        )
    }
}

/** A step counts as "reached" (spoken, then advance to the next one) once the vehicle is this close to its maneuver location. */
private const val NavArriveThresholdMeters = 40

/** An "in X m, do the next thing" lead announcement fires once the vehicle is this close to the upcoming maneuver. */
private const val NavPreAnnounceThresholdMeters = 200

/** How far the vehicle can stray from the planned route's own path before it counts as "lost" and a detour back to it is computed. */
private const val OffRouteThresholdMeters = 60.0

private fun formatNavDistance(meters: Int): String =
    if (meters >= 1000) "%.1f km".format(meters / 1000f) else "$meters m"

/** Turn-by-turn banner shown while navigating — arrow + instruction + distance + stop, fixed dark chrome. */
@Composable
private fun NavigationBanner(instruction: String, arrow: String, distanceMeters: Int?, onStop: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = RouteInk,
        contentColor = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(arrow, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RouteNavAccent)
            Text(instruction, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(distanceMeters?.let { formatNavDistance(it) } ?: "", color = Color(0xFFFFC9C9))
            // A colored circular button reads unmistakably as a control
            // against the dark banner — a plain white icon-only IconButton
            // here was too easy to miss at a glance while driving.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(RouteNavAccent, CircleShape)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Stop navigation", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun VoicePickerDialog(
    voices: List<NavigationVoiceOption>,
    selected: NavigationVoiceOption?,
    onSelect: (NavigationVoiceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val missingLanguages = TargetVoiceLanguages.filter { (code, _) -> voices.none { option -> option.voice.locale.language == code } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Navigation voice") },
        text = {
            Column {
                if (voices.isEmpty()) {
                    Text("No Finnish, English, or Swedish voices are installed on this device.")
                } else {
                    voices.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(option) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = { onSelect(option) })
                            Spacer(Modifier.width(4.dp))
                            Text(option.label)
                        }
                    }
                }
                if (missingLanguages.isNotEmpty()) {
                    Text(
                        "Missing: ${missingLanguages.joinToString(", ") { it.second }}. Your " +
                            "device's text-to-speech engine may need those languages downloaded.",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                            } catch (e: ActivityNotFoundException) {
                                // No TTS engine on this device exposes a language installer — nothing more we can do.
                            }
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text("Install more languages")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
