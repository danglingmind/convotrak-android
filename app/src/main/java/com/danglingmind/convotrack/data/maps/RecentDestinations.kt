package com.danglingmind.convotrack.data.maps

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

/**
 * Destinations the rider planned rides to, newest first (iOS RecentDestinations). Kept on this
 * device only, the way Google Maps keeps recent searches, and never sent anywhere — no coordinates
 * or place names leave the phone through analytics either (`docs/analytics.md`).
 */
object RecentDestinations {
    private const val PREFS = "planner"
    private const val KEY = "recentDestinations"
    const val LIMIT = 8

    fun load(context: Context): List<PlaceResult> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PlaceResult(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    latLng = LatLng(o.getDouble("lat"), o.getDouble("lng")),
                    address = o.optString("address").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Moves [place] to the front, dropping an older copy of it and anything past [LIMIT]. */
    fun remember(context: Context, place: PlaceResult) {
        val places = (listOf(place) + load(context).filter { it.id != place.id }).take(LIMIT)
        val array = JSONArray()
        places.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("lat", p.latLng.latitude)
                put("lng", p.latLng.longitude)
                p.address?.let { put("address", it) }
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
