package com.danglingmind.convotrack.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Synthesizes and plays short alert tones — the Android port of iOS `SirenPlayer.swift`. No bundled
 * audio asset required; the PCM is generated on first use and cached.
 *
 * Two distinct sounds:
 *  - [Sound.EMERGENCY] — an urgent continuous siren wail sweeping 600↔1200 Hz, for `ride:emergency`.
 *  - [Sound.REGROUP]   — a bold two-tone "nee-naw" siren (two alternating steady pitches), for
 *    `ride:regroup`. Loud and attention-grabbing, but a clearly different *character* from the
 *    emergency wail (discrete alternating tones vs. a continuous glissando) so the two can't be
 *    confused.
 *
 * Plays on the ALARM usage so a ride-coordination alert is still heard when the phone is on
 * silent/vibrate — mirroring the iOS `.duckOthers` playback-category intent.
 */
object SirenPlayer {

    /** [durationMs] is how long each plays (3 repeats), so a spoken cue can start just after it ends. */
    enum class Sound(val durationMs: Long) {
        EMERGENCY(2_700),   // 3 wails of 0.9 s
        REGROUP(1_680),     // 3 cycles of two 0.28 s tones
    }

    private const val SAMPLE_RATE = 44_100

    // PCM is generated lazily and cached (the math is identical every time).
    private val samples = HashMap<Sound, ShortArray>()

    // Single-thread executor: generation + AudioTrack writes happen off the main thread, and a
    // rapid second alert is serialized after the first so they can't fight over the track.
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var track: AudioTrack? = null

    fun play(sound: Sound) {
        worker.execute {
            val pcm = samples.getOrPut(sound) { generate(sound) }
            // Tear down any in-flight alert first so the new one isn't mixed on top of it.
            track?.run { stop(); release() }

            val bytes = pcm.size * 2
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(bytes)
                .build()

            t.write(pcm, 0, pcm.size)
            t.play()
            track = t
        }
    }

    private fun generate(sound: Sound): ShortArray = when (sound) {
        Sound.EMERGENCY -> repeated(makeWail(), times = 3)
        Sound.REGROUP -> repeated(makeNeeNaw(), times = 3)
    }

    /** Concatenates [unit] back-to-back [times] times. Each unit fades to silence at both ends,
     *  so a phase reset at the seam is click-free. */
    private fun repeated(unit: ShortArray, times: Int): ShortArray {
        val out = ShortArray(unit.size * times)
        for (i in 0 until times) System.arraycopy(unit, 0, out, i * unit.size, unit.size)
        return out
    }

    /** One siren "wail": pitch sweeps up from 600→1200 Hz and back, with short fades at each end. */
    private fun makeWail(): ShortArray {
        val wailDuration = 0.9
        val frames = (SAMPLE_RATE * wailDuration).toInt()
        val out = ShortArray(frames)
        val lowFreq = 600.0
        val highFreq = 1_200.0
        val fade = 0.02
        var phase = 0.0
        for (frame in 0 until frames) {
            val t = frame / SAMPLE_RATE.toDouble()
            val sweep = (sin(2 * PI * (1.0 / wailDuration) * t - PI / 2) + 1) / 2
            val freq = lowFreq + (highFreq - lowFreq) * sweep
            phase += 2 * PI * freq / SAMPLE_RATE
            if (phase > 2 * PI) phase -= 2 * PI
            val env = min(1.0, min(t, wailDuration - t) / fade)
            out[frame] = (sin(phase) * 0.5 * env * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** One "nee-naw" cycle: a high tone then a low tone, each a steady pitch with click-free fades. */
    private fun makeNeeNaw(): ShortArray {
        val tones = doubleArrayOf(880.0, 660.0)   // A5 → E5
        val toneDuration = 0.28
        val framesPerTone = (SAMPLE_RATE * toneDuration).toInt()
        val frames = framesPerTone * tones.size
        val out = ShortArray(frames)
        val fade = 0.012
        for (frame in 0 until frames) {
            val toneIndex = min(tones.size - 1, frame / framesPerTone)
            val freq = tones[toneIndex]
            val localT = (frame % framesPerTone) / SAMPLE_RATE.toDouble()
            val env = min(1.0, min(localT, toneDuration - localT) / fade)
            out[frame] = (sin(2 * PI * freq * localT) * 0.55 * env * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
