package com.planroute.app.ui.theme

import androidx.compose.ui.graphics.Color

// Matches the existing web client's palette (see packages/client's
// component styles) so the two feel like the same product.
val RoutePrimary = Color(0xFF1C7ED6)
val RouteStart = Color(0xFF2F9E44)
val RouteEnd = Color(0xFFE03131)
val RouteSurface = Color(0xFFF8F9FA)
val RouteOnSurfaceMuted = Color(0xFF495057)
val RouteNavAccent = Color(0xFFE64980)
val RouteWarn = Color(0xFFFAB005)

// Fixed (theme-independent) dark chrome for the navigation banner — real
// turn-by-turn UI stays high-contrast regardless of the app's light/dark
// theme, same as Google Maps' own nav chrome.
val RouteInk = Color(0xFF14202B)

/** Unselected route-alternative line color (matches the web client's minor-road gray). */
val RouteAltLine = Color(0xFFB8C1C8)

// POI pin colors — gas/camping/hotel each get their own so they read apart
// on the map at a glance; same Open Color shade level (7) as the rest of
// the palette above for consistency. Gas reuses RoutePrimary's blue (a
// common fuel-pump convention); camping/hotel get colors of their own.
val RouteCamping = Color(0xFF0C8599)
val RouteHotel = Color(0xFF7048E8)
val RouteRestaurant = Color(0xFFE8590C)

/** The "back to the planned route" detour line drawn when the driver strays off it — deliberately distinct from every route/POI color above so it can't be mistaken for the selected route itself. */
val RouteDetour = Color(0xFF9C36B5)
