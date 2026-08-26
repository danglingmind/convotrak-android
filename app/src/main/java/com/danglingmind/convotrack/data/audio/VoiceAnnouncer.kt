package com.danglingmind.convotrack.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speaks short spoken navigation announcements (e.g. "Ride updated, rerouting") via on-device
 * text-to-speech. Android port of iOS `VoiceAnnouncer.swift`.
 *
 * Uses the ASSISTANCE_NAVIGATION_GUIDANCE usage so the voice is heard over music/podcasts during a
 * ride — the Android analogue of iOS's `.playback` + `.duckOthers`.
 *
 * A leading earcon (chime) is typically played first via [SirenPlayer], so [announce] accepts a
 * small delay to let that finish; otherwise the two fight over audio focus and one of them is
 * simply lost.
 */
object VoiceAnnouncer {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    private val handler = Handler(Looper.getMainLooper())

    /** Announcements requested before the engine finished initialising. TTS init is async and can
     *  take a second on a cold start, which is exactly when the first "rerouting" cue tends to
     *  fire — dropping it would silently lose the one announcement that matters most. */
    private var pending: String? = null

    /** Called once from `ConvoTrackApp`. Safe to call again; later calls are no-ops. */
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.apply {
                language = Locale.US
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
            ready = true
            pending?.let { speakNow(it); pending = null }
        }
    }

    /**
     * Speak [text], optionally after [delayMs] (to let a leading chime finish first).
     *
     * Cancels any in-flight utterance so a burst of updates doesn't queue up stale speech — during
     * a ride, the newest instruction is the only one worth hearing.
     */
    fun announce(text: String, delayMs: Long = 0) {
        if (delayMs <= 0) {
            speakNow(text)
        } else {
            handler.postDelayed({ speakNow(text) }, delayMs)
        }
    }

    private fun speakNow(text: String) {
        val engine = tts
        if (engine == null || !ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "convotrack_nav")
    }
}
