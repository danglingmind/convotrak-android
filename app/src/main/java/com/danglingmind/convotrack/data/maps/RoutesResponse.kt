package com.danglingmind.convotrack.data.maps

import kotlinx.serialization.Serializable

// Routes API v2 response subset (matches the X-Goog-FieldMask requested in DirectionsService).
@Serializable
data class RoutesResponse(
    val routes: List<Route> = emptyList(),
) {
    @Serializable
    data class Route(
        val legs: List<Leg> = emptyList(),
        // Whole-route fields — populated only for the alternatives field mask (no per-leg data).
        val polyline: RoutePolyline? = null,
        val distanceMeters: Int? = null,
        val duration: String? = null,
        val routeLabels: List<String> = emptyList(),
    )

    @Serializable
    data class RoutePolyline(val encodedPolyline: String)

    @Serializable
    data class Leg(
        val distanceMeters: Int = 0,
        val duration: String = "0s",
        val steps: List<Step> = emptyList(),
    )

    @Serializable
    data class Step(
        val distanceMeters: Int? = null,
        val endLocation: EndLocation? = null,
        val navigationInstruction: NavigationInstruction? = null,
        val polyline: StepPolyline,
    )

    @Serializable
    data class StepPolyline(val encodedPolyline: String)

    @Serializable
    data class EndLocation(val latLng: LatLngNode)

    @Serializable
    data class LatLngNode(val latitude: Double, val longitude: Double)

    @Serializable
    data class NavigationInstruction(val instructions: String)
}
