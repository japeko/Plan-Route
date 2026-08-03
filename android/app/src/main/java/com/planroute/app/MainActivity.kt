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
 * Swedish voices. Real turn-by-turn instructions (parsed from OSRM's route
 * steps) and a proper ViewModel layer (this is still plain Activity-scoped
 * Compose state) are follow-up work — [NavigationBanner] still shows one
 * static demo instruction (in whichever of fi/en/sv the selected voice
 * uses), spoken once when navigation starts.
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

    var mode by remember { mutableStateOf(SheetMode.PLANNING) }
    var isNavigating by remember { mutableStateOf(false) }
    LaunchedEffect(isNavigating) {
        if (isNavigating) voiceController.speak(demoInstructionFor(selectedVoice?.voice?.locale?.language))
    }

    // Live position/speed while driving — ExploreMap uses both to keep the
    // vehicle centered and to pick a zoom level for the current speed (see
    // zoomForSpeedKmh). Cleared on stop so the map falls back to the
    // route's own start/geometry point when next explored.
    var liveVehiclePosition by remember { mutableStateOf<GeoPoint?>(null) }
    var currentSpeedKmh by remember { mutableStateOf(0.0) }
    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
            liveVehiclePosition = null
            currentSpeedKmh = 0.0
            return@LaunchedEffect
        }
        LocationRepository.trackLocation(context).collect { location ->
            liveVehiclePosition = GeoPoint(location.latitude, location.longitude)
            if (location.hasSpeed()) {
                currentSpeedKmh = location.speed * 3.6
            }
        }
    }

    var startAddress by remember { mutableStateOf("") }
    var endAddress by remember { mutableStateOf("") }
    var startPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var endPosition by remember { mutableStateOf<GeoPoint?>(null) }
    var nextStopId by remember { mutableStateOf(0) }
    val viaStops = remember { mutableStateListOf<ViaStop>() }
    // All three on by default — the spec's "by default gas stations within
    // 500m..." etc. means these searches run automatically once a route
    // exists, not that the user has to discover and tap the chips first.
    val selectedFilters = remember { mutableStateListOf("Gas stations", "Camping", "Hotels") }

    val plannedRoutes = remember { mutableStateListOf<PlannedRoute>() }
    var selectedRouteId by remember { mutableStateOf<Int?>(null) }
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
                val results = RoutingRepository.route(listOf(start) + viaStops.map { it.position } + listOf(end))
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

                            val results = RoutingRepository.route(listOf(startGeo) + viaGeos + endGeo)
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
                canStartNavigation = mode == SheetMode.COMPARING,
                onStartNavigation = { requestLocationPermissionThen { isNavigating = true } },
                onToggleReportRoadWork = { isReportingRoadWork = !isReportingRoadWork },
                selectedVoiceLabel = selectedVoice?.label?.let { "Voice: $it" } ?: "Voice",
                onOpenVoicePicker = { showVoicePicker = true },
                currentSpeedKmh = currentSpeedKmh,
            )

            if (isNavigating) {
                NavigationBanner(
                    instruction = demoInstructionFor(selectedVoice?.voice?.locale?.language),
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

/** Stand-in for real turn-by-turn text until OSRM route steps are parsed — see the class doc comment above. */
private const val DemoNavInstruction = "Turn left onto Ilmarinkatu"

/** Both the spoken and the on-screen instruction follow whichever voice is selected — English if none is picked yet. */
private val DemoNavInstructionsByLanguage = mapOf(
    "fi" to "Käänny vasemmalle Ilmarinkadulle",
    "sv" to "Sväng vänster in på Ilmarinkatu",
    "en" to DemoNavInstruction,
)

private fun demoInstructionFor(languageCode: String?): String =
    DemoNavInstructionsByLanguage[languageCode] ?: DemoNavInstruction

/** Turn-by-turn banner shown while navigating — arrow + instruction + distance + stop, fixed dark chrome. */
@Composable
private fun NavigationBanner(instruction: String, onStop: () -> Unit, modifier: Modifier = Modifier) {
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
            Text("↰", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RouteNavAccent)
            Text(instruction, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("350 m", color = Color(0xFFFFC9C9))
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
