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
        val description: String? = null,
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

    /**
     * Both fields optional: a step can arrive with the object present but a field missing, and a
     * required field here would fail the decode for the ENTIRE route rather than that one step.
     */
    @Serializable
    data class NavigationInstruction(
        val instructions: String? = null,
        /**
         * `TURN_LEFT`, `ROUNDABOUT_RIGHT`, `DEPART`, … The authoritative, locale-independent
         * signal for which arrow to draw. Deriving it from the instruction text instead misreads
         * road names ("Turn right onto Left Cross Rd" matches "left" first) and collapses distinct
         * maneuvers ("Keep left" vs "Turn left") — and would break outright if `languageCode` ever
         * moved off English.
         */
        val maneuver: String? = null,
    )
}
