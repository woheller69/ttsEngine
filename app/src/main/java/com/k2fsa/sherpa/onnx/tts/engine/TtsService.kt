package com.k2fsa.sherpa.onnx.tts.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import com.k2fsa.sherpa.onnx.GenerationConfig
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log
import java.io.ByteArrayOutputStream


class TtsService : TextToSpeechService() {

    // How much audio to accumulate before calling callback.start(), so the
    // system AudioTrack isn't left draining an empty buffer while the model
    // computes the first chunk. Without this, the first inference call
    // (slower than steady-state, since onnxruntime warms up its execution
    // graph on first use) races the AudioTrack and causes an audible
    // crackle/underrun at the start of every utterance. See issue #90 for
    // the same root cause manifesting as a clipped first word instead.
    private val PRE_ROLL_MS = 300

    // Member variables to hold state for the callback
    private var currentPitch = 100f
    private var currentSynthesisCallback: SynthesisCallback? = null
    private var preRollBuffer: ByteArrayOutputStream? = null
    private var preRollThresholdBytes = 0

    override fun onCreate() {
        Log.i(TAG, "onCreate tts service")
        super.onCreate()
        val preferenceHelper = PreferenceHelper(this)
        val language = preferenceHelper.getCurrentLanguage()
        // see https://github.com/Miserlou/Android-SDK-Samples/blob/master/TtsEngine/src/com/example/android/ttsengine/RobotSpeakTtsService.java#L68
        onLoadLanguage(language, "", "")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy tts service")
        super.onDestroy()
    }

    // https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService#onislanguageavailable
    override fun onIsLanguageAvailable(_lang: String?, _country: String?, _variant: String?): Int {
        val lang = _lang ?: ""
        return if (TtsEngine.getAvailableLanguages(this).contains(lang)) {
            TextToSpeech.LANG_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onGetLanguage(): Array<String> {  //returns language currently being used
        return arrayOf(TtsEngine.lang!!, "", "")
    }

    // https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService#onLoadLanguage(kotlin.String,%20kotlin.String,%20kotlin.String)
    override fun onLoadLanguage(_lang: String?, _country: String?, _variant: String?): Int {
        Log.i(TAG, "onLoadLanguage: $_lang, $_country")
        val lang = _lang ?: ""
        Migrate.renameModelFolder(this)   //Rename model folder if "old" structure
        val preferenceHelper = PreferenceHelper(this)
        return if (preferenceHelper.getCurrentLanguage().equals("")) {
            TextToSpeech.LANG_MISSING_DATA
        } else {
            if (TtsEngine.getAvailableLanguages(this).contains(lang)) {
                Log.i(TAG, "creating tts, lang :$lang")
                TtsEngine.createTts(application, lang)
                TextToSpeech.LANG_AVAILABLE
            } else {
                Log.i(TAG, "lang $lang not supported, tts engine lang: ${TtsEngine.lang}")
                TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    override fun onStop() {}

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        Log.i(TAG, "onSynthesizeText")
        if (TtsEngine.tts == null || request == null || callback == null) {
            return
        }
        val language = request.language
        val country = request.country
        val variant = request.variant
        var pitch = 100f

        val ret = onIsLanguageAvailable(language, country, variant)
        if (ret == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback.error()
            return
        } else {
            TtsEngine.createTts(application, language)
        }

        val preferenceHelper = PreferenceHelper(this)

        if (preferenceHelper.applySystemSpeed()) {
            pitch = request.pitch * 1.0f
            TtsEngine.speed.value = request.speechRate / pitch  //divide by pitch to compensate for pitch adjustment performed in ttsCallback
        }         // request.speechRate: System does not memorize different speeds for different languages

        var text = request.charSequenceText.toString()

        if (preferenceHelper.getStripSSML()) text = TtsEngine.stripSsmlTags(text)



        val tts = TtsEngine.tts!!
        val sampleRate = tts.sampleRate()

        if (text.isBlank() || text.isEmpty()) {
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        // Store state in member variables so the function reference can access them
        currentPitch = pitch
        currentSynthesisCallback = callback
        preRollBuffer = ByteArrayOutputStream()
        preRollThresholdBytes = sampleRate * 2 * PRE_ROLL_MS / 1000  // 16-bit mono PCM

        // FIX: Use a function reference (::ttsCallback) instead of an inline lambda.
        // This forces the Kotlin compiler to generate the correct JNI signature: ([F)Ljava/lang/Integer;
        tts.generateWithConfigAndCallback(
            text = text,
            config = GenerationConfig(sid = TtsEngine.speakerId.value, speed = TtsEngine.speed.value),
            callback = ::ttsCallback,
        )

        // Short utterances may finish generating before the pre-roll threshold is
        // ever reached; flush whatever was buffered so playback still starts.
        flushPreRoll(callback, sampleRate)

        callback.done()

        // Clear state after synthesis is complete
        currentSynthesisCallback = null
    }

    // This MUST be a member function so we can use the ::ttsCallback reference
    private fun ttsCallback(floatSamples: FloatArray): Int {
        val cb = currentSynthesisCallback ?: return 0
        val pitch = currentPitch

        val samples: ByteArray

        if (pitch != 100f) {   //if not default pitch, play samples faster or slower. Speed has already been compensated before generation, see above
            val speedFactor = pitch / 100f
            val newSampleCount = (floatSamples.size / speedFactor).toInt()
            val newSamples = FloatArray(newSampleCount)

            for (i in 0 until newSampleCount) {
                newSamples[i] = floatSamples[(i * speedFactor).toInt()] * TtsEngine.volume.value
            }
            // Convert the modified FloatArray to ByteArray
            samples = floatArrayToByteArray(newSamples)
        } else {
            // The floatSamples array is a fresh instance created by JNI for this callback,
            // so modifying it in place is safe and avoids an extra allocation.
            // Convert FloatArray to ByteArray
            for (i in floatSamples.indices) {
                floatSamples[i] *= TtsEngine.volume.value
            }
            samples = floatArrayToByteArray(floatSamples)
        }

        val buffer = preRollBuffer
        if (buffer != null) {
            buffer.write(samples)
            if (buffer.size() >= preRollThresholdBytes) {
                flushPreRoll(cb, TtsEngine.tts!!.sampleRate())
            }
        } else {
            writeAudio(cb, samples)
        }

        // 1 means to continue
        // 0 means to stop
        return 1
    }

    // Calls callback.start() (if not already called for this utterance) and flushes
    // whatever has been buffered so far. After this, ttsCallback streams directly.
    private fun flushPreRoll(cb: SynthesisCallback, sampleRate: Int) {
        val buffer = preRollBuffer ?: return
        preRollBuffer = null
        cb.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
        writeAudio(cb, buffer.toByteArray())
    }

    private fun writeAudio(cb: SynthesisCallback, samples: ByteArray) {
        val maxBufferSize: Int = cb.maxBufferSize
        var offset = 0
        while (offset < samples.size) {
            val bytesToWrite = Math.min(maxBufferSize, samples.size - offset)
            cb.audioAvailable(samples, offset, bytesToWrite)
            offset += bytesToWrite
        }
    }

    private fun floatArrayToByteArray(audio: FloatArray): ByteArray {
        // byteArray is actually a ShortArray
        val byteArray = ByteArray(audio.size * 2)
        for (i in audio.indices) {
            val sample = (audio[i] * 32767).toInt()
            byteArray[2 * i] = sample.toByte()
            byteArray[2 * i + 1] = (sample shr 8).toByte()
        }
        return byteArray
    }
}
