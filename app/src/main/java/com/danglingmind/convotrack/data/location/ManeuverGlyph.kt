package com.danglingmind.convotrack.data.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Draws the notification's large maneuver icon: a dark arrow on a lime disc, rotated for the turn
 * type. Deliberately generic (straight / left / right / slight / u-turn / arrive) — the Routes API
 * has ~15 maneuver strings and a notification thumbnail cannot show the difference anyway.
 */
object ManeuverGlyph {
    private const val SIZE = 128
    private val cache = HashMap<Int, Bitmap>()

    /** Degrees clockwise from "up"; null = arrive (destination dot). */
    private fun angleFor(maneuver: String): Int? = when (maneuver) {
        "TURN_LEFT", "FORK_LEFT", "ROUNDABOUT_LEFT" -> -90
        "TURN_SHARP_LEFT" -> -125
        "TURN_SLIGHT_LEFT", "RAMP_LEFT" -> -45
        "TURN_RIGHT", "FORK_RIGHT", "ROUNDABOUT_RIGHT" -> 90
        "TURN_SHARP_RIGHT" -> 125
        "TURN_SLIGHT_RIGHT", "RAMP_RIGHT" -> 45
        "UTURN_LEFT", "UTURN_RIGHT" -> 180
        "ARRIVE" -> null
        else -> 0
    }

    @Synchronized
    fun bitmap(maneuver: String): Bitmap {
        val angle = angleFor(maneuver)
        val key = angle ?: Int.MIN_VALUE
        return cache.getOrPut(key) { draw(angle) }
    }

    private fun draw(angle: Int?): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CAF300") }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#171E00") }
        c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, disc)
        if (angle == null) {
            c.drawCircle(SIZE / 2f, SIZE / 2f, SIZE * 0.2f, ink)
            return bmp
        }
        c.save()
        c.rotate(angle.toFloat(), SIZE / 2f, SIZE / 2f)
        val cx = SIZE / 2f
        val arrow = Path().apply {
            moveTo(cx, SIZE * 0.18f)                 // tip
            lineTo(cx + SIZE * 0.24f, SIZE * 0.46f)
            lineTo(cx + SIZE * 0.09f, SIZE * 0.46f)
            lineTo(cx + SIZE * 0.09f, SIZE * 0.82f)
            lineTo(cx - SIZE * 0.09f, SIZE * 0.82f)
            lineTo(cx - SIZE * 0.09f, SIZE * 0.46f)
            lineTo(cx - SIZE * 0.24f, SIZE * 0.46f)
            close()
        }
        c.drawPath(arrow, ink)
        c.restore()
        return bmp
    }
}
