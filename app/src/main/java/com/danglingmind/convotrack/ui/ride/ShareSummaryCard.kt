package com.danglingmind.convotrack.ui.ride

import com.danglingmind.convotrack.ui.designsystem.DsColor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.compose.ui.graphics.toArgb
import com.danglingmind.convotrack.data.maps.StaticMapsService
import com.danglingmind.convotrack.data.net.RideSummary
import com.danglingmind.convotrack.ui.common.RideDuration
import com.danglingmind.convotrack.ui.common.encodeQrBitmap
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * The shareable ride-summary poster. Android port of iOS `RideSummaryShareCard.swift`.
 *
 * Drawn straight onto a [Canvas] rather than by rendering a composable to a bitmap. The card is a
 * fixed-size image with no interaction, no theming and no reflow — composing it would mean standing
 * up an off-screen composition purely to rasterise it once, and would make its layout depend on the
 * device's font scale and display metrics. A poster that changes shape with the viewer's accessibility
 * settings is a bug, not a feature.
 */
object ShareSummaryCard {

    // Portrait poster at a fixed pixel size, so it looks identical wherever it is shared.
    private const val W = 1080
    private const val H = 1920

    private fun paint(
        color: Int,
        sizePx: Float,
        bold: Boolean = false,
        mono: Boolean = false,
        letterSpacingEm: Float = 0f,
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx
        letterSpacing = letterSpacingEm
        typeface = Typeface.create(
            if (mono) Typeface.MONOSPACE else Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    /**
     * Renders the card. The map hero and the QR are both optional: a ride whose route failed to
     * render still produces a perfectly good card, so neither is allowed to fail the whole share.
     */
    suspend fun render(
        rideTitle: String,
        summary: RideSummary,
        routeCoordinates: List<LatLng>,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(DsColor.bgBase.toArgb())

        val lime = DsColor.accent.toArgb()
        val onSurface = DsColor.textPrimary.toArgb()
        val muted = DsColor.textSecondary.toArgb()

        // --- Map hero, top-anchored and faded into the card background ---------------------
        val heroH = 820f
        // Google renders its logo and "Map data ©" into the bottom strip of the returned image,
        // and the Static Maps terms require that to stay legible. The fade therefore STOPS above
        // it: an earlier version ran the gradient to the bottom of the hero and buried the
        // attribution at ~93% opacity, which is a licensing problem, not a cosmetic one.
        val attributionStrip = 74f
        // Everything below the map starts here. Google's logo is drawn INTO the bottom-left of the
        // returned image and we cannot move it, so our own content has to clear it — the headline
        // distance previously sat right on top of it, which is the same legibility problem as
        // fading it out.
        val contentTop = heroH + 100f
        val map = runCatching { StaticMapsService.snapshot(routeCoordinates) }.getOrNull()
        if (map != null) {
            val src = Rect(0, 0, map.width, map.height)
            val dst = RectF(0f, 0f, W.toFloat(), heroH)
            c.drawBitmap(map, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            val fadeTop = heroH * 0.5f
            val fadeBottom = heroH - attributionStrip
            val fade = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, fadeTop, 0f, fadeBottom,
                    intArrayOf(0x00060C12, DsColor.bgBase.toArgb()),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(0f, fadeTop, W.toFloat(), fadeBottom, fade)
        }

        // --- Brand ------------------------------------------------------------------------
        val brand = paint(lime, 46f, bold = true, letterSpacingEm = 0.18f)
        c.drawText("CONVOTRACK", 72f, 130f, brand)

        // --- Headline distance ------------------------------------------------------------
        val distanceKm = String.format(Locale.US, "%.1f", summary.distanceMeters / 1000)
        val bigNumber = paint(lime, 190f, bold = true)
        c.drawText(distanceKm, 72f, contentTop + 40f, bigNumber)
        val kmPaint = paint(muted, 52f, bold = true, mono = true, letterSpacingEm = 0.1f)
        c.drawText("KM", 72f + bigNumber.measureText(distanceKm) + 24f, contentTop + 40f, kmPaint)

        // --- Title + date -----------------------------------------------------------------
        val titlePaint = paint(onSurface, 62f, bold = true)
        c.drawText(ellipsise(rideTitle.uppercase(), titlePaint, W - 144f), 72f, contentTop + 140f, titlePaint)

        // 30sp with tighter tracking: at 36 the three-part line ran past the card and the rider
        // count — the part a reader actually cares about — was the bit that got ellipsised away.
        val metaPaint = paint(muted, 30f, mono = true, letterSpacingEm = 0.04f)
        val dateText = formatShareDate(summary.createdAt)
        val riders = "${summary.participants.size} RIDER${if (summary.participants.size == 1) "" else "S"}"
        val metaText = listOf("RIDE COMPLETED", dateText, riders).filter { it.isNotEmpty() }.joinToString(" · ")
        c.drawText(ellipsise(metaText, metaPaint, W - 144f), 72f, contentTop + 205f, metaPaint)

        // --- Metric row -------------------------------------------------------------------
        val duration = RideDuration.stat(summary.durationSeconds)
        val metrics = listOf(
            Triple("DURATION", duration.first, duration.second),
            Triple("AVG SPEED", summary.avgSpeedKmh?.let { String.format(Locale.US, "%.0f", it) } ?: "--", "KM/H"),
            Triple("MAX SPLIT", String.format(Locale.US, "%.1f", summary.maxGroupSplitMeters / 1000), "KM"),
        )
        val unitPaint = paint(muted, 30f, bold = true, mono = true, letterSpacingEm = 0.1f)
        val labelPaint = paint(muted, 30f, bold = true, mono = true, letterSpacingEm = 0.14f)
        val colW = (W - 144f) / 3f
        metrics.forEachIndexed { i, (label, value, unit) ->
            val x = 72f + colW * i
            c.drawText(ellipsise(label, labelPaint, colW - 16f), x, contentTop + 320f, labelPaint)

            // Shrink the value until IT AND ITS UNIT fit the column. A duration like "164:28" is
            // half again as wide as "76.4", and at a fixed size it ran straight over the next
            // column's value — two numbers overlapping, neither readable.
            val valuePaint = paint(onSurface, 76f, bold = true)
            val unitW = if (unit.isEmpty()) 0f else unitPaint.measureText(unit) + 14f
            while (valuePaint.textSize > 34f &&
                valuePaint.measureText(value) + unitW > colW - 16f
            ) {
                valuePaint.textSize -= 4f
            }
            c.drawText(value, x, contentTop + 410f, valuePaint)
            if (unit.isNotEmpty()) {
                c.drawText(unit, x + valuePaint.measureText(value) + 14f, contentTop + 410f, unitPaint)
            }
        }

        // --- Awards -----------------------------------------------------------------------
        var y = contentTop + 520f
        summary.participants.take(3).forEach { p ->
            val name = p.name.ifBlank { "Rider" }
            val title = com.danglingmind.convotrack.data.net.displayTitle(p.rideTitle)
            val namePaint = paint(onSurface, 40f, bold = true)
            val titlePaint2 = paint(lime, 30f, bold = true, mono = true, letterSpacingEm = 0.1f)
            val titleX = W - 72f - (if (title.isEmpty()) 0f else titlePaint2.measureText(title))
            c.drawText(ellipsise(name, namePaint, titleX - 96f), 72f, y, namePaint)
            if (title.isNotEmpty()) c.drawText(title, titleX, y, titlePaint2)
            y += 64f
        }

        // --- QR, bottom-right -------------------------------------------------------------
        val qr = runCatching { encodeQrBitmap("https://convotrack.in", 220) }.getOrNull()
        if (qr != null) {
            val qrSize = 220f
            val qx = W - 72f - qrSize
            val qy = H - 72f - qrSize
            c.drawRoundRect(RectF(qx - 16f, qy - 16f, qx + qrSize + 16f, qy + qrSize + 16f), 20f, 20f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
            c.drawBitmap(qr, null, RectF(qx, qy, qx + qrSize, qy + qrSize), Paint(Paint.FILTER_BITMAP_FLAG))
            // Label to the LEFT of the code, right-aligned to it. Above the code it collided with
            // a third award row, which runs right up to the same right edge.
            val scan = paint(muted, 28f, bold = true, mono = true, letterSpacingEm = 0.12f)
            val labelRight = qx - 16f - 28f
            c.drawText("SCAN TO", labelRight - scan.measureText("SCAN TO"), qy + qrSize / 2 - 8f, scan)
            c.drawText("DOWNLOAD", labelRight - scan.measureText("DOWNLOAD"), qy + qrSize / 2 + 30f, scan)
        }

        bmp
    }

    /**
     * Bumped when the poster's layout changes, so cards cached by an older build are re-rendered
     * instead of being shared in the old design.
     */
    private const val CARD_VERSION = 2

    /** How many rides' cards to keep. Each is ~0.5–1 MB; older ones are dropped first. */
    private const val MAX_CACHED_CARDS = 20

    /**
     * Cached cards live in the one directory the FileProvider exposes (`res/xml/file_paths.xml`), so
     * a cached card can be shared as-is. `cacheDir` is right for them: the OS may evict it under
     * storage pressure, and a card is always cheap to render again.
     */
    private fun cardDir(context: Context) = File(context.cacheDir, "shared").apply { mkdirs() }

    /**
     * One file per ride. A finished ride's summary can't change (completed rides aren't editable),
     * so the card rendered once is the card forever — and because the name carries the ride id, two
     * different rides never share a content:// URI, which is what stopped a receiver that cached by
     * URI from re-sending the previous ride's card.
     */
    private fun cardFile(context: Context, rideId: String) =
        File(cardDir(context), "ride-summary-v$CARD_VERSION-${rideId.filter { it.isLetterOrDigit() || it == '-' }}.png")

    /** The previously generated card for [rideId], or null if it hasn't been made (or was evicted). */
    suspend fun cachedCard(context: Context, rideId: String): File? = withContext(Dispatchers.IO) {
        cardFile(context, rideId).takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * Writes [bitmap] as [rideId]'s card. Written to a temp file and renamed so an interrupted write
     * never leaves a truncated PNG that a later share would pick up as valid.
     */
    suspend fun saveCard(context: Context, rideId: String, bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val target = cardFile(context, rideId)
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        pruneCache(target.parentFile!!)
        target
    }

    /** Keeps the newest [MAX_CACHED_CARDS] cards and drops leftovers from older builds. */
    private fun pruneCache(dir: File) {
        val files = dir.listFiles().orEmpty()
        files.filter { !it.name.startsWith("ride-summary-v$CARD_VERSION-") || it.name.endsWith(".tmp") }
            .forEach { it.delete() }
        files.filter { it.exists() && it.name.endsWith(".png") }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CACHED_CARDS)
            .forEach { it.delete() }
    }

    /** Hands a saved card to the share sheet. */
    suspend fun share(context: Context, card: File, rideTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", card)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "$rideTitle — ridden with ConvoTrack")
            // Without this the receiving app cannot read the file: a content:// URI from our
            // FileProvider is useless to them unless the grant travels with the intent.
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        withContext(Dispatchers.Main) {
            context.startActivity(
                Intent.createChooser(intent, "Share ride summary")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // The chooser shows a preview of the image only if it can read it too.
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private fun ellipsise(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    /** ISO8601 → "28 AUG 2026". Empty when unparseable, so the meta line just omits it. */
    private fun formatShareDate(iso: String): String {
        if (iso.isBlank()) return ""
        val millis = runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            .format(Date(millis))
            .uppercase(Locale.getDefault())
    }
}
