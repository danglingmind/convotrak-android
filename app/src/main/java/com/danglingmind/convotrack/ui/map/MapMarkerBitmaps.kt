package com.danglingmind.convotrack.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.danglingmind.convotrack.ui.designsystem.DsColor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Marker artwork, baked to bitmaps — the Android counterpart of the `renderMarker` family in iOS
 * `GoogleMapView.swift`.
 *
 * These are drawn rather than composed because a `MarkerComposable` is re-measured and re-rastered
 * by the maps-compose layer on every recomposition, which during navigation is once per GPS fix
 * per marker. Baking once per *appearance change* (see the style key each marker is cached under)
 * keeps a convoy's worth of markers off the main thread's critical path.
 *
 * Sizes are in pixels and callers pass a density scale, so the art is crisp on any screen.
 */
object MapMarkerBitmaps {

    /** Rider circle diameter, in dp. Kept small so a rider sitting on a sharp turn doesn't hide
     *  the route curve underneath. */
    fun riderCircleDp(isSelected: Boolean): Float = if (isSelected) 40f else 32f

    /** Smallest map label, in sp. Labels are baked into bitmaps, so the system font scale has to
     *  be passed in and applied by hand (capped by the caller). */
    const val LABEL_SP = 12f

    /** Height of the name band drawn above the rider circle, in dp. The circle's centre — the
     *  marker's anchor — therefore sits at `riderNameBandDp + size/2`. Grows with the font scale
     *  so a larger label still fits its plate. */
    fun riderNameBandDp(fontScale: Float): Float = maxOf(20f, LABEL_SP * fontScale * 1.25f + 7f)

    private fun riderDistBandDp(fontScale: Float): Float = maxOf(16f, LABEL_SP * fontScale * 1.25f + 3f)

    private fun paint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun textPaint(sizePx: Float, color: Int, bold: Boolean, mono: Boolean = false): Paint =
        paint().apply {
            textSize = sizePx
            this.color = color
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            if (mono) fontFeatureSettings = "tnum"
        }

    // MARK: - User chevron

    /**
     * Chevron footprint (pointing up, before rotation), in dp. Google's navigation puck is a touch
     * taller than it is wide; the corner radii and canvas derive from this, so the shape stays
     * proportional at any size.
     */
    private const val CHEVRON_W_DP = 34f
    private const val CHEVRON_H_DP = 38f

    /**
     * Google Maps' navigation chevron: a rounded arrowhead whose tail is notched inward, so the
     * silhouette reads as *direction* rather than as a plain triangle — drawn pointing due north.
     *
     * The heading is deliberately NOT baked in. The marker is placed `flat = true` with
     * `rotation = heading`, so the map itself converts north-relative rotation into screen space:
     * correct at any camera bearing, mid-animation, and while free-looking. It also foreshortens
     * under the navigation tilt, like Google's own puck. Baking the rotation instead would only be
     * right on a north-up map — and during navigation the camera is heading-up, so the on-screen
     * error would equal the camera bearing (correct heading north, 90° off heading east, exactly
     * backwards heading south).
     *
     * A constant bitmap also means this is baked once for the whole ride rather than on every fix.
     */
    fun userChevron(scale: Float): Bitmap {
        val w = CHEVRON_W_DP * scale
        val h = CHEVRON_H_DP * scale
        val stroke = h * 0.075f

        // Square canvas centred on the chevron: the marker rotates about its centre anchor, so a
        // generous square keeps the art at the centre of rotation at every heading.
        val reach = sqrt((w / 2) * (w / 2) + (h / 2) * (h / 2))
        val canvasSize = Math.ceil((reach * 2 + stroke + 12 * scale).toDouble()).toInt()
        val bmp = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(canvasSize / 2f, canvasSize / 2f)

        val path = chevronPath(w, h)

        // Fill, lifted off the map by the same soft shadow Google uses.
        val fill = paint().apply {
            color = DsColor.accent.toArgb()
            setShadowLayer(4f * scale, 0f, 1.5f * scale, 0x8C000000.toInt())
        }
        c.drawPath(path, fill)

        // White keyline — separates the marker from the lime route it rides on.
        val keyline = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.WHITE
        }
        c.drawPath(path, keyline)
        return bmp
    }

    /**
     * Rounded polygon around four points — tip, two tail corners, and the tail notch — centred on
     * the origin and pointing along -y.
     *
     * Each corner is a quadratic through the vertex, entered and left `radius` short of it. Radii
     * are fractions of the height, so scaling the chevron doesn't change how rounded it reads.
     */
    private fun chevronPath(w: Float, h: Float): Path {
        val tip = floatArrayOf(0f, -h / 2)
        val right = floatArrayOf(w / 2, h / 2)
        val notch = floatArrayOf(0f, h / 2 - h * 0.27f)   // tail cut-in
        val left = floatArrayOf(-w / 2, h / 2)

        val tipR = h * 0.096f
        val tailR = h * 0.119f
        val notchR = h * 0.111f

        // Corners in traversal order, each as (incoming vertex, corner, outgoing vertex, radius).
        val corners = listOf(
            arrayOf(tip, right, notch, tailR),
            arrayOf(right, notch, left, notchR),
            arrayOf(notch, left, tip, tailR),
            arrayOf(left, tip, right, tipR),
        )

        val path = Path()
        // Start mid-edge (tip → right) so the first corner arc has a full incoming edge to round
        // against; close() rejoins along that same edge.
        path.moveTo((tip[0] + right[0]) / 2, (tip[1] + right[1]) / 2)
        for (corner in corners) {
            @Suppress("UNCHECKED_CAST")
            val from = corner[0] as FloatArray
            val at = corner[1] as FloatArray
            val to = corner[2] as FloatArray
            val radius = corner[3] as Float
            roundedCorner(path, from, at, to, radius)
        }
        path.close()
        return path
    }

    private fun roundedCorner(path: Path, from: FloatArray, at: FloatArray, to: FloatArray, radius: Float) {
        val inDx = at[0] - from[0]; val inDy = at[1] - from[1]
        val inLen = max(1e-3f, sqrt(inDx * inDx + inDy * inDy))
        val outDx = to[0] - at[0]; val outDy = to[1] - at[1]
        val outLen = max(1e-3f, sqrt(outDx * outDx + outDy * outDy))
        val r = minOf(radius, inLen / 2, outLen / 2)

        path.lineTo(at[0] - inDx / inLen * r, at[1] - inDy / inLen * r)
        path.quadTo(at[0], at[1], at[0] + outDx / outLen * r, at[1] + outDy / outLen * r)
    }

    // MARK: - Rider marker

    /** Where the rider marker's anchor sits, as a 0..1 fraction of the baked bitmap's height. */
    fun riderAnchorY(isSelected: Boolean, hasDistance: Boolean, fontScale: Float = 1f): Float {
        val size = riderCircleDp(isSelected)
        val nameBand = riderNameBandDp(fontScale)
        val distBand = if (hasDistance) riderDistBandDp(fontScale) else 0f
        val total = nameBand + size + distBand
        return (nameBand + size / 2) / total
    }

    /**
     * A rider marker: a bordered circle (avatar or initial) **centred on the coordinate** — no tip,
     * no rank badge — with the first name above and the distance from the current user below.
     *
     * Centring rather than bottom-anchoring, and dropping the pointer tip and rank badge, is what
     * keeps the marker's footprint small enough not to occlude the route curve it sits on.
     *
     * [isOffline] dims the entire marker, mirroring the greyed leaderboard row so the map and the
     * list always agree on who is present.
     */
    fun riderMarker(
        scale: Float,
        name: String,
        avatar: Bitmap?,
        isMe: Boolean,
        isSelected: Boolean,
        isOffline: Boolean,
        distanceText: String?,
        fontScale: Float = 1f,
    ): Bitmap {
        val size = riderCircleDp(isSelected) * scale
        val nameBandH = riderNameBandDp(fontScale) * scale
        val distBandH = if (distanceText != null) riderDistBandDp(fontScale) * scale else 0f
        val labelPx = LABEL_SP * fontScale * scale
        val lime = DsColor.accent.toArgb()

        val namePaint = textPaint(labelPx, android.graphics.Color.WHITE, bold = true)
        val nameText = name.substringBefore(" ").ifEmpty { name }
        val nameW = namePaint.measureText(nameText)

        val distPaint = textPaint(labelPx, lime, bold = true, mono = true)
        val distW = distanceText?.let { distPaint.measureText(it) } ?: 0f

        val canvasW = maxOf(size + 8 * scale, nameW + 8 * scale, distW + 8 * scale)
        val totalH = nameBandH + size + distBandH
        val bmp = Bitmap.createBitmap(
            Math.ceil(canvasW.toDouble()).toInt(),
            Math.ceil(totalH.toDouble()).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        val cx = canvasW / 2
        val circle = RectF(cx - size / 2, nameBandH, cx + size / 2, nameBandH + size)

        val alpha = if (isOffline) 128 else 255

        if (avatar != null) {
            // Aspect-fill the photo into the circle.
            val shaderPaint = paint().apply {
                val s = max(size / avatar.width, size / avatar.height)
                val matrix = Matrix().apply {
                    setScale(s, s)
                    postTranslate(
                        circle.centerX() - avatar.width * s / 2,
                        circle.centerY() - avatar.height * s / 2,
                    )
                }
                shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setLocalMatrix(matrix)
                }
                this.alpha = alpha
            }
            c.drawOval(circle, shaderPaint)
        } else {
            // Letter fallback while the photo loads or when there's no avatar URL.
            c.drawOval(circle, paint().apply { color = DsColor.avatar.toArgb(); this.alpha = alpha })
            val initialPaint = textPaint(
                size * 0.32f,
                if (isMe) lime else android.graphics.Color.WHITE,
                bold = true,
            ).apply { this.alpha = alpha }
            val initial = name.take(1).uppercase()
            val tw = initialPaint.measureText(initial)
            val fm = initialPaint.fontMetrics
            c.drawText(initial, circle.centerX() - tw / 2, circle.centerY() - (fm.ascent + fm.descent) / 2, initialPaint)
        }

        // Border ring — route lime, thinned to match the smaller circle.
        c.drawOval(
            RectF(circle).apply { inset(1.25f * scale, 1.25f * scale) },
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = (if (isSelected) 1.75f else 1.25f) * scale
                color = lime
                this.alpha = alpha
            },
        )

        // Name label above the circle, on a soft plate so it reads over any map tile.
        drawLabel(c, nameText, namePaint, cx, nameBandH / 2, scale, alpha)

        // Distance label below the circle.
        if (distanceText != null) {
            drawLabel(c, distanceText, distPaint, cx, nameBandH + size + distBandH / 2, scale, alpha)
        }
        return bmp
    }

    private fun drawLabel(c: Canvas, text: String, p: Paint, cx: Float, cy: Float, scale: Float, alpha: Int) {
        val w = p.measureText(text)
        val fm = p.fontMetrics
        val h = fm.descent - fm.ascent
        val plate = RectF(cx - w / 2 - 4 * scale, cy - h / 2 - 2 * scale, cx + w / 2 + 4 * scale, cy + h / 2 + 2 * scale)
        c.drawRoundRect(
            plate, 6f * scale, 6f * scale,
            paint().apply { color = DsColor.mapPlate.toArgb(); this.alpha = (alpha * 0.86f).toInt() },
        )
        val tp = Paint(p).apply { this.alpha = alpha }
        c.drawText(text, cx - w / 2, cy - (fm.ascent + fm.descent) / 2, tp)
    }

    // MARK: - Stop pins (start / waypoint / destination / regroup / emergency)

    /**
     * A bottom-anchored stop pin: a filled circle with a glyph, a pointer tip, and a wrapped name
     * plate underneath.
     *
     * Names WRAP to a second line rather than truncate — a place name is the whole point of the
     * pin, and "Kempegowda International Airport Beng…" is not something a rider can act on. The
     * plate's width is capped so a long name can't sprawl across the screen.
     */
    fun stopPin(
        scale: Float,
        glyph: String,
        accent: Int,
        circleDp: Float,
        label: String?,
        labelMaxWidthDp: Float = 128f,
        filled: Boolean = true,
        fontScale: Float = 1f,
    ): Bitmap {
        val circle = circleDp * scale
        val tipH = 6f * scale
        val tipW = 10f * scale

        val labelPaint = textPaint(LABEL_SP * fontScale * scale, android.graphics.Color.WHITE, bold = true)
        val maxLabelW = labelMaxWidthDp * scale
        // Blank label = icon only (a stop already passed), so no plate is drawn.
        val lines = label?.takeIf { it.isNotBlank() }?.let { wrapText(it, labelPaint, maxLabelW, maxLines = 2) } ?: emptyList()
        val lineH = labelPaint.fontMetrics.let { it.descent - it.ascent }
        val labelW = lines.maxOfOrNull { labelPaint.measureText(it) } ?: 0f
        val plateW = if (lines.isEmpty()) 0f else labelW + 16 * scale
        val plateH = if (lines.isEmpty()) 0f else lineH * lines.size + 6 * scale

        val canvasW = maxOf(circle, plateW) + 4 * scale
        val canvasH = circle + tipH + (if (lines.isEmpty()) 0f else plateH + 2 * scale)
        val bmp = Bitmap.createBitmap(
            Math.ceil(canvasW.toDouble()).toInt(),
            Math.ceil(canvasH.toDouble()).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val c = Canvas(bmp)
        val cx = canvasW / 2

        val head = RectF(cx - circle / 2, 0f, cx + circle / 2, circle)
        if (filled) {
            c.drawOval(head, paint().apply { color = accent })
        } else {
            c.drawOval(head, paint().apply { color = DsColor.raised.toArgb() })
            c.drawOval(
                RectF(head).apply { inset(1.25f * scale, 1.25f * scale) },
                paint().apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * scale; color = accent },
            )
        }

        val glyphPaint = textPaint(
            circle * 0.45f,
            if (filled) DsColor.onAccent.toArgb() else accent,
            bold = true,
        )
        val gw = glyphPaint.measureText(glyph)
        val gfm = glyphPaint.fontMetrics
        c.drawText(glyph, cx - gw / 2, head.centerY() - (gfm.ascent + gfm.descent) / 2, glyphPaint)

        // Pointer tip, so the pin visibly lands on its coordinate.
        c.drawPath(
            Path().apply {
                moveTo(cx - tipW / 2, circle - 1 * scale)
                lineTo(cx + tipW / 2, circle - 1 * scale)
                lineTo(cx, circle + tipH)
                close()
            },
            paint().apply { color = accent },
        )

        if (lines.isNotEmpty()) {
            val plateTop = circle + tipH + 2 * scale
            c.drawRoundRect(
                RectF(cx - plateW / 2, plateTop, cx + plateW / 2, plateTop + plateH),
                8f * scale, 8f * scale,
                paint().apply { color = DsColor.mapPlate.toArgb() },
            )
            var y = plateTop + 3 * scale - labelPaint.fontMetrics.ascent
            for (line in lines) {
                val lw = labelPaint.measureText(line)
                c.drawText(line, cx - lw / 2, y, labelPaint)
                y += lineH
            }
        }
        return bmp
    }

    /** A small lime dot with a white keyline — the route's end caps. */
    fun endCap(scale: Float, sizeDp: Float, opacity: Float): Bitmap {
        val size = sizeDp * scale
        val bmp = Bitmap.createBitmap(size.toInt().coerceAtLeast(1), size.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        c.drawOval(
            RectF(0f, 0f, size, size),
            paint().apply { color = DsColor.accent.toArgb(); this.alpha = alpha },
        )
        c.drawOval(
            RectF(0.75f * scale, 0.75f * scale, size - 0.75f * scale, size - 0.75f * scale),
            paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
                color = android.graphics.Color.WHITE
                this.alpha = (alpha * 0.9f).toInt()
            },
        )
        return bmp
    }

    /** A filled dot with a dark keyline, for the rider's position before planning or a dropped pin. */
    fun dot(scale: Float, sizeDp: Float, color: Int): Bitmap {
        val size = (sizeDp * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val stroke = 2.5f * scale
        c.drawOval(RectF(0f, 0f, size.toFloat(), size.toFloat()), paint().apply { this.color = DsColor.sheet.toArgb() })
        c.drawOval(RectF(stroke, stroke, size - stroke, size - stroke), paint().apply { this.color = color })
        return bmp
    }

    /**
     * Time bubble for a route alternative: a pill with a tail pointing at the route. Lime for the
     * selected route, raised surface for the others — the same split as the lines (iOS
     * RouteBubbleArt). Anchor at (0.5, 1), the tail's tip.
     */
    fun routeBubble(scale: Float, fontScale: Float, text: String, selected: Boolean): Bitmap {
        val textSize = (LABEL_SP + 1f) * fontScale * scale
        val tp = textPaint(textSize, if (selected) DsColor.onAccent.toArgb() else DsColor.textLabel.toArgb(), bold = true, mono = true)
        val padH = 10f * scale
        val padV = 6f * scale
        val tail = 6f * scale
        // Transparent margin so the bubble is a comfortable target.
        val margin = 4f * scale
        val textW = tp.measureText(text)
        val fm = tp.fontMetrics
        val pillW = textW + padH * 2
        val pillH = (fm.descent - fm.ascent) + padV * 2
        val w = (pillW + margin * 2).toInt()
        val h = (pillH + tail).toInt() + 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fill = paint().apply { color = if (selected) DsColor.accent.toArgb() else DsColor.raised.toArgb() }
        val rect = RectF(margin, 0f, margin + pillW, pillH)
        val radius = 9f * scale
        c.drawRoundRect(rect, radius, radius, fill)
        if (!selected) {
            c.drawRoundRect(
                RectF(rect.left + 0.5f * scale, rect.top + 0.5f * scale, rect.right - 0.5f * scale, rect.bottom - 0.5f * scale),
                radius, radius,
                paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f * scale; color = DsColor.borderStrong.toArgb() },
            )
        }
        val cx = w / 2f
        c.drawPath(Path().apply {
            moveTo(cx - 5f * scale, pillH - 1f)
            lineTo(cx + 5f * scale, pillH - 1f)
            lineTo(cx, pillH + tail)
            close()
        }, fill)
        c.drawText(text, margin + padH, padV - fm.ascent, tp)
        return bmp
    }

    /** Greedy word wrap to at most [maxLines]; the final line is ellipsised if it still overflows. */
    private fun wrapText(text: String, p: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (p.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (p.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
                if (lines.size == maxLines) break
            }
        }
        if (lines.size < maxLines && current.isNotEmpty()) lines += current.toString()
        if (lines.size > maxLines) return lines.take(maxLines)
        // Ellipsise the last line if the text didn't fit in the allowed lines.
        val consumed = lines.joinToString(" ")
        if (consumed.length < text.length && lines.isNotEmpty()) {
            var last = lines.last()
            while (last.isNotEmpty() && p.measureText("$last…") > maxWidth) last = last.dropLast(1)
            lines[lines.size - 1] = "$last…"
        }
        return lines
    }
}
