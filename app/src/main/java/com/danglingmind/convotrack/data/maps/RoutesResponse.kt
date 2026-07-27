package com.danglingmind.convotrack.data.maps

import kotlinx.serialization.Serializable

// Routes API v2 response subset (matches the X-Goog-FieldMask requested in DirectionsService).
@Serializable
data class RoutesResponse(
    val routes: List<Route> = emptyList(),
) {
    @Serializable
    data class Route(val legs: List<Leg> = emptyList())

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
