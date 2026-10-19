package com.danglingmind.convotrack.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How urgent an utterance is. A higher priority interrupts a lower one; a lower one waits its turn
 * behind a higher one instead of cutting it off.
 */
enum class VoicePriority {
    /** Turn-by-turn prompts. Replaced by the next prompt, since only the newest is worth hearing. */
    TURN,
    /** Regroup, split and reroute announcements. */
    ALERT,
    /** Emergencies. The only speech that ignores the mute switch. */
    EMERGENCY,
}

/**
 * Speaks short spoken navigation announcements via on-device text-to-speech. Android port of iOS
 * `VoiceAnnouncer.swift`.
 *
 * Uses the ASSISTANCE_NAVIGATION_GUIDANCE usage and a transient-may-duck audio focus request, so
 * music dips under the voice and recovers when it finishes — the analogue of iOS's `.playback` +
 * `.duckOthers`. Muting silences everything except emergencies; the choice is persisted.
 *
 * A leading earcon (chime) is typically played first via [SirenPlayer], so [announce] accepts a
 * small delay to let that finish; otherwise the two fight over audio focus and one is lost.
 */
object VoiceAnnouncer {

    private const val PREFS = "nav_voice"
    private const val KEY_MUTED = "muted"

    private var tts: TextToSpeech? = null
    private var audio: AudioManager? = null
    private var prefs: android.content.SharedPreferences? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile
    private var ready = false

    private val handler = Handler(Looper.getMainLooper())

    private val _muted = MutableStateFlow(false)
    /** Whether voice guidance is muted. Emergencies are spoken regardless. */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** Priority of what is speaking now, by utterance id. */
    private val priorities = HashMap<String, VoicePriority>()
    @Volatile private var speakingPriority: VoicePriority? = null
    private var utteranceCounter = 0

    /** Announcements requested before the engine finished initialising. TTS init is async and can
     *  take a second on a cold start, which is exactly when the first cue tends to fire. */
    private var pending: Pair<String, VoicePriority>? = null

    /** Called once from `ConvoTrackApp`. Safe to call again; later calls are no-ops. */
    fun init(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _muted.value = prefs?.getBoolean(KEY_MUTED, false) ?: false

        tts = TextToSpeech(app) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.apply {
                language = Locale.US
                setAudioAttributes(guidanceAttributes)
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        speakingPriority = priorities[utteranceId]
                    }
                    override fun onDone(utteranceId: String) = finished(utteranceId)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) = finished(utteranceId)
                    override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
                })
            }
            ready = true
            pending?.let { (text, priority) -> speakNow(text, priority); pending = null }
        }
    }

    fun setMuted(value: Boolean) {
        _muted.value = value
        prefs?.edit()?.putBoolean(KEY_MUTED, value)?.apply()
        if (value) stop()
    }

    /**
     * Speak [text], optionally after [delayMs] (to let a leading chime finish first).
     */
    fun announce(text: String, delayMs: Long = 0, priority: VoicePriority = VoicePriority.ALERT) {
        if (delayMs <= 0) speakNow(text, priority)
        else handler.postDelayed({ speakNow(text, priority) }, delayMs)
    }

    /** Cuts off whatever is being said, e.g. when voice is muted or navigation ends. */
    fun stop() {
        tts?.stop()
        pending = null
    }

    private fun speakNow(text: String, priority: VoicePriority) {
        if (_muted.value && priority != VoicePriority.EMERGENCY) return
        val engine = tts
        if (engine == null || !ready) {
            pending = text to priority
            return
        }
        // Equal or lower priority than what is speaking: a newer prompt replaces a stale one; a
        // lower one never cuts off a higher one, it queues behind it.
        val current = speakingPriority
        val mode = if (engine.isSpeaking && current != null && current > priority) {
            TextToSpeech.QUEUE_ADD
        } else {
            TextToSpeech.QUEUE_FLUSH
        }
        requestFocus()
        val id = "convotrack_nav_${utteranceCounter++}"
        priorities[id] = priority
        engine.speak(text, mode, null, id)
    }

    private fun finished(utteranceId: String) {
        priorities.remove(utteranceId)
        speakingPriority = null
        // A flushed prompt is usually replaced at once; give focus back only when nothing is left.
        if (tts?.isSpeaking != true) abandonFocus()
    }

    private val guidanceAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun requestFocus() {
        val manager = audio ?: return
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(guidanceAttributes)
            .build()
            .also { focusRequest = it }
        manager.requestAudioFocus(request)
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        audio?.abandonAudioFocusRequest(request)
    }
}
