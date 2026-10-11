package com.danglingmind.convotrack.data.maps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.danglingmind.convotrack.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders the label-free, poster-style route map used by the ride-summary share card. Android port
 * of iOS `GoogleStaticMapsService.swift`.
 *
 * Every map label is turned off and the basemap is flattened to near-black, so the only thing with
 * any brightness on the image is the route itself — the card is a poster, not a navigation aid.
 */
object StaticMapsService {

    private val client = OkHttpClient()

    /** Static Maps caps each dimension at 640, so the hero renders at 390×420 and `scale=2` gives
     *  it a 780×840 pixel buffer — enough to stay sharp on any phone the card is viewed on. */
    suspend fun snapshot(
        routeCoordinates: List<LatLng>,
        widthDp: Int = 390,
        heightDp: Int = 420,
        scale: Int = 2,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (routeCoordinates.isEmpty()) return@withContext null

        // The Static Maps `path` parameter goes in a URL, and a long ride's polyline is tens of
        // thousands of points — far past any practical URL length. Thinning it costs nothing at
        // this size: the whole route is a few hundred pixels wide on the card.
        val path = thin(routeCoordinates, maxPoints = 300)
        val encoded = DirectionsService.encodePolyline(path)

        val url = "https://maps.googleapis.com/maps/api/staticmap".toHttpUrl().newBuilder()
            .addQueryParameter("size", "${widthDp}x$heightDp")
            .addQueryParameter("scale", "$scale")
            .addQueryParameter("maptype", "roadmap")
            // Dark Glass basemap (the in-app map style), no labels — the poster look.
            .addQueryParameter("style", "feature:all|element:labels|visibility:off")
            .addQueryParameter("style", "feature:all|element:geometry|color:0x11191d")
            .addQueryParameter("style", "feature:road|element:geometry|color:0x252e33")
            .addQueryParameter("style", "feature:road.highway|element:geometry|color:0x38434a")
            .addQueryParameter("style", "feature:water|element:geometry|color:0x0b1418")
            .addQueryParameter("style", "feature:landscape|element:geometry|color:0x13201b")
            .addQueryParameter("style", "feature:poi|visibility:off")
            .addQueryParameter("style", "feature:transit|visibility:off")
            // Route: bold accent (ice cyan, DsColor.accent), the hero of the card. With a `path` and no explicit center/zoom,
            // Static Maps fits the path for us — which is exactly what we want here.
            .addQueryParameter("path", "color:0x3de8ffff|weight:5|enc:$encoded")
            .addQueryParameter("key", BuildConfig.MAPS_API_KEY)
            .build()

        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
        }.getOrNull()
    }

    /** Evenly samples [points] down to at most [maxPoints], always keeping the ends. */
    private fun thin(points: List<LatLng>, maxPoints: Int): List<LatLng> {
        if (points.size <= maxPoints) return points
        val stride = (points.size + maxPoints - 1) / maxPoints
        val out = ArrayList<LatLng>(maxPoints + 1)
        var i = 0
        while (i < points.size) { out.add(points[i]); i += stride }
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    /** Web Mercator world position, for projecting a coordinate onto the returned image. */
    fun worldPoint(coord: LatLng, worldSize: Double): Pair<Double, Double> {
        val x = (coord.longitude + 180) / 360 * worldSize
        val sinLat = min(max(sin(coord.latitude * PI / 180), -0.9999), 0.9999)
        val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * worldSize
        return x to y
    }
}
