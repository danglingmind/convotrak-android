package com.danglingmind.convotrack.data.imagecache

import android.content.Context
import org.json.JSONObject

/**
 * Remembers where each ride was going (name + coordinate), on disk, so a history row can still
 * find its cached destination photo with no network.
 *
 * It is only a fallback: whenever the ride can be fetched the fresh ride wins and rewrites this, so
 * an edit made on another phone is picked up as soon as there is a connection. Emptied with the
 * image cache on sign-out, and per ride on delete/leave/edit.
 */
object RideDestinationStore {

    data class Destination(val name: String, val lat: Double, val lng: Double)

    private const val PREFS = "ride_destinations"
    private const val LIMIT = 300

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun put(rideId: String, destination: Destination) {
        val p = prefs ?: return
        val json = JSONObject()
            .put("n", destination.name).put("lat", destination.lat).put("lng", destination.lng)
        val edit = p.edit().putString(rideId, json.toString())
        // Keep the store bounded: drop arbitrary old entries past the limit.
        val extra = p.all.size + 1 - LIMIT
        if (extra > 0) p.all.keys.filter { it != rideId }.take(extra).forEach { edit.remove(it) }
        edit.apply()
    }

    @Synchronized
    fun get(rideId: String): Destination? {
        val raw = prefs?.getString(rideId, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Destination(json.getString("n"), json.getDouble("lat"), json.getDouble("lng"))
        }.getOrNull()
    }

    @Synchronized
    fun forget(rideId: String) { prefs?.edit()?.remove(rideId)?.apply() }

    @Synchronized
    fun clearAll() { prefs?.edit()?.clear()?.apply() }
}
