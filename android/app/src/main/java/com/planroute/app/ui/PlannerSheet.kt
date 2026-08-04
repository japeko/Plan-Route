package com.planroute.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planroute.app.model.GasAmenity
import com.planroute.app.model.RouteOption
import com.planroute.app.model.SheetMode
import com.planroute.app.model.ViaStop
import com.planroute.app.repository.RouteStep
import com.planroute.app.ui.theme.RouteEnd
import com.planroute.app.ui.theme.RoutePrimary
import com.planroute.app.ui.theme.RouteStart
import kotlin.math.roundToInt

val PoiFilterOptions = listOf("Gas stations", "Camping", "Hotels")

/**
 * Everything the "along the route" POI search needs from the caller —
 * bundled into one object rather than a dozen loose parameters. Distance
 * values are floats in meters to match [Slider]; each has a paired
 * `on*DistanceChange` (fires continuously while dragging, for the live
 * label) and `on*DistanceCommit` (fires once on release, which is what
 * should actually trigger a server request — see MainActivity).
 */
class PoiFilterState(
    val gasMaxDistanceMeters: Float,
    val gasAmenities: Set<GasAmenity>,
    val campingMaxDistanceMeters: Float,
    val hotelMaxDistanceMeters: Float,
    val onGasDistanceChange: (Float) -> Unit,
    val onGasDistanceCommit: () -> Unit,
    val onToggleGasAmenity: (GasAmenity) -> Unit,
    val onCampingDistanceChange: (Float) -> Unit,
    val onCampingDistanceCommit: () -> Unit,
    val onHotelDistanceChange: (Float) -> Unit,
    val onHotelDistanceCommit: () -> Unit,
)

/**
 * Everything living inside the draggable bottom sheet. Which section shows
 * depends on [mode]: the planning form (screen 02) while composing a route,
 * or the alternatives list (screen 03) once one has been planned. While
 * planning and only peeking, a one-line summary stands in for the full form
 * (screen 01) so the map stays the focus until the driver drags up.
 */
@Composable
fun PlannerSheetContent(
    mode: SheetMode,
    isPeeking: Boolean,
    startAddress: String,
    onStartAddressChange: (String) -> Unit,
    endAddress: String,
    onEndAddressChange: (String) -> Unit,
    viaStops: List<ViaStop>,
    onAddViaStop: () -> Unit,
    onRemoveViaStop: (ViaStop) -> Unit,
    onViaAddressChange: (ViaStop, String) -> Unit,
    selectedFilters: Set<String>,
    onToggleFilter: (String) -> Unit,
    filterState: PoiFilterState,
    canPlanRoute: Boolean,
    isPlanning: Boolean,
    planningError: String?,
    onPlanRoute: () -> Unit,
    onClear: () -> Unit,
    isLocatingUser: Boolean,
    onUseCurrentLocation: () -> Unit,
    routeOptions: List<RouteOption>,
    selectedRouteId: Int?,
    onSelectRoute: (Int) -> Unit,
    onBackToPlanning: () -> Unit,
    selectedRouteSteps: List<RouteStep>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetHandle()
        when {
            mode == SheetMode.COMPARING -> RouteComparisonSection(
                routeOptions = routeOptions,
                selectedRouteId = selectedRouteId,
                onSelectRoute = onSelectRoute,
                onBackToPlanning = onBackToPlanning,
                selectedRouteSteps = selectedRouteSteps,
            )
            isPeeking -> PeekSummary(startAddress)
            else -> PlanningForm(
                startAddress = startAddress,
                onStartAddressChange = onStartAddressChange,
                endAddress = endAddress,
                onEndAddressChange = onEndAddressChange,
                viaStops = viaStops,
                onAddViaStop = onAddViaStop,
                onRemoveViaStop = onRemoveViaStop,
                onViaAddressChange = onViaAddressChange,
                selectedFilters = selectedFilters,
                onToggleFilter = onToggleFilter,
                filterState = filterState,
                canPlanRoute = canPlanRoute,
                isPlanning = isPlanning,
                planningError = planningError,
                onPlanRoute = onPlanRoute,
                onClear = onClear,
                isLocatingUser = isLocatingUser,
                onUseCurrentLocation = onUseCurrentLocation,
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .size(width = 34.dp, height = 4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
private fun PeekSummary(startAddress: String) {
    Text(
        "Start",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        startAddress.ifBlank { "Not set — drag up to plan a route" },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PlanningForm(
    startAddress: String,
    onStartAddressChange: (String) -> Unit,
    endAddress: String,
    onEndAddressChange: (String) -> Unit,
    viaStops: List<ViaStop>,
    onAddViaStop: () -> Unit,
    onRemoveViaStop: (ViaStop) -> Unit,
    onViaAddressChange: (ViaStop, String) -> Unit,
    selectedFilters: Set<String>,
    onToggleFilter: (String) -> Unit,
    filterState: PoiFilterState,
    canPlanRoute: Boolean,
    isPlanning: Boolean,
    planningError: String?,
    onPlanRoute: () -> Unit,
    onClear: () -> Unit,
    isLocatingUser: Boolean,
    onUseCurrentLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = startAddress,
                onValueChange = onStartAddressChange,
                label = { Text("Start address") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                leadingIcon = { Dot(RouteStart) },
            )
            IconButton(onClick = onUseCurrentLocation, enabled = !isLocatingUser) {
                if (isLocatingUser) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Use my current location")
                }
            }
        }

        viaStops.forEach { stop ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stop.address,
                    onValueChange = { onViaAddressChange(stop, it) },
                    label = { Text("Pass by") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = { Dot(RoutePrimary) },
                )
                IconButton(onClick = { onRemoveViaStop(stop) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove stop")
                }
            }
        }

        OutlinedButton(onClick = onAddViaStop, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add stop", modifier = Modifier.padding(start = 6.dp))
        }

        OutlinedTextField(
            value = endAddress,
            onValueChange = onEndAddressChange,
            label = { Text("End address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Dot(RouteEnd) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlanRoute, enabled = canPlanRoute && !isPlanning, modifier = Modifier.weight(1f)) {
                Text(if (isPlanning) "Planning…" else "Plan route")
            }
            OutlinedButton(onClick = onClear) {
                Text("Clear")
            }
        }
        if (planningError != null) {
            Text(
                planningError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PoiFilterChips(selectedFilters = selectedFilters, onToggleFilter = onToggleFilter, filterState = filterState)
    }
}

@Composable
private fun PoiFilterChips(selectedFilters: Set<String>, onToggleFilter: (String) -> Unit, filterState: PoiFilterState) {
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(PoiFilterOptions) { label ->
                FilterChip(
                    selected = label in selectedFilters,
                    onClick = { onToggleFilter(label) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = RoutePrimary,
                    ),
                )
            }
        }

        if ("Gas stations" in selectedFilters) {
            DistanceSliderRow(
                title = "Gas stations, distance from route",
                valueMeters = filterState.gasMaxDistanceMeters,
                valueRange = 0f..5000f,
                onValueChange = filterState.onGasDistanceChange,
                onValueChangeFinished = filterState.onGasDistanceCommit,
            )
            GasAmenityChips(selected = filterState.gasAmenities, onToggle = filterState.onToggleGasAmenity)
        }
        if ("Camping" in selectedFilters) {
            DistanceSliderRow(
                title = "Camping, distance from route",
                valueMeters = filterState.campingMaxDistanceMeters,
                valueRange = 0f..50_000f,
                onValueChange = filterState.onCampingDistanceChange,
                onValueChangeFinished = filterState.onCampingDistanceCommit,
            )
        }
        if ("Hotels" in selectedFilters) {
            DistanceSliderRow(
                title = "Hotels & hostels, distance from route",
                valueMeters = filterState.hotelMaxDistanceMeters,
                valueRange = 0f..30_000f,
                onValueChange = filterState.onHotelDistanceChange,
                onValueChangeFinished = filterState.onHotelDistanceCommit,
            )
        }
    }
}

@Composable
private fun DistanceSliderRow(
    title: String,
    valueMeters: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(
            "$title: ${formatDistance(valueMeters)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = valueMeters,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun GasAmenityChips(selected: Set<GasAmenity>, onToggle: (GasAmenity) -> Unit) {
    LazyRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(GasAmenity.entries) { amenity ->
            FilterChip(
                selected = amenity in selected,
                onClick = { onToggle(amenity) },
                label = { Text(amenity.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            )
        }
    }
}

private fun formatDistance(meters: Float): String =
    if (meters >= 1000f) "%.1f km".format(meters / 1000f) else "${meters.roundToInt()} m"

@Composable
private fun RouteComparisonSection(
    routeOptions: List<RouteOption>,
    selectedRouteId: Int?,
    onSelectRoute: (Int) -> Unit,
    onBackToPlanning: () -> Unit,
    selectedRouteSteps: List<RouteStep>,
) {
    var showDirections by remember { mutableStateOf(false) }

    Text(
        "Choose a route",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routeOptions.forEach { option ->
            RouteOptionCard(
                option = option,
                selected = option.id == selectedRouteId,
                onClick = { onSelectRoute(option.id) },
            )
        }
    }
    Row(modifier = Modifier.padding(top = 4.dp)) {
        TextButton(onClick = onBackToPlanning) {
            Text("Edit stops")
        }
        TextButton(onClick = { showDirections = !showDirections }) {
            Text(if (showDirections) "Hide directions" else "Show directions")
        }
    }
    if (showDirections) {
        if (selectedRouteSteps.isEmpty()) {
            Text(
                "No directions available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(selectedRouteSteps) { index, step ->
                    Text(
                        "${index + 1}. ${step.instruction} — ${formatDistance(step.distanceMeters.toFloat())}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteOptionCard(option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        border = if (selected) BorderStroke(1.5.dp, RoutePrimary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(option.label, fontWeight = FontWeight.SemiBold)
            Text(
                "${option.distanceKm} km · ${option.durationMinutes / 60}h ${option.durationMinutes % 60}m",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}
