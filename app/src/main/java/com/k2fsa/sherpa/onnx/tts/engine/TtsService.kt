package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.util.Log


class TtsService : TextToSpeechService() {
    override fun onCreate() {
        Log.i(TAG, "onCreate tts service")
        super.onCreate()

        // see https://github.com/Miserlou/Android-SDK-Samples/blob/master/TtsEngine/src/com/example/android/ttsengine/RobotSpeakTtsService.java#L68
        onLoadLanguage(TtsEngine.lang, "", "")
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy tts service")
        super.onDestroy()
    }

    // https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeechService#onislanguageavailable
    override fun onIsLanguageAvailable(_lang: String?, _country: String?, _variant: String?): Int {
        val lang = _lang ?: ""

        if (TtsEngine.getAvailableLanguages(this).contains(lang)) {
            return TextToSpeech.LANG_AVAILABLE
        }

        return TextToSpeech.LANG_NOT_SUPPORTED
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
        return if (preferenceHelper.getCurrentLanguage().equals("")){  //Download model first if no model is installed
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
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
        if (TtsEngine.tts == null || request == null || callback == null) {
            return
        }
        val language = request.language
        val country = request.country
        val variant = request.variant
        var pitch = 100f

        val preferenceHelper = PreferenceHelper(this)
        val volume = preferenceHelper.getVolume()

        if (preferenceHelper.applySystemSpeed()){
            pitch = request.pitch * 1.0f
            TtsEngine.speed = request.speechRate / pitch  //divide by pitch to compensate for pitch adjustment performed in ttsCallback
        }         // request.speechRate: System does not memorize different speeds for different languages

        val text = request.charSequenceText.toString()

        val ret = onIsLanguageAvailable(language, country, variant)
        if (ret == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback.error()
            return
        }

        val tts = TtsEngine.tts!!

        callback.start(tts.sampleRate(), AudioFormat.ENCODING_PCM_16BIT, 1)

        if (text.isBlank() || text.isEmpty()) {
            callback.done()
            return
        }

        val ttsCallback: (FloatArray) -> Int = fun(floatSamples): Int {
            val samples: ByteArray

            if (pitch != 100f){  //if not default pitch, play samples faster or slower. Speed has already been compensated before generation, see above
                val speedFactor = pitch / 100f
                val newSampleCount = (floatSamples.size / speedFactor).toInt()
                val newSamples = FloatArray(newSampleCount)

                for (i in 0 until newSampleCount) {
                    newSamples[i] = floatSamples[(i * speedFactor).toInt()] * volume
                }
                // Convert the modified FloatArray to ByteArray
                samples = floatArrayToByteArray(newSamples)
            } else {
                // Convert FloatArray to ByteArray
                for (i in floatSamples.indices) {
                    floatSamples[i] *= volume
                }
                samples = floatArrayToByteArray(floatSamples)
            }

            val maxBufferSize: Int = callback.maxBufferSize
            var offset = 0
            while (offset < samples.size) {
                val bytesToWrite = Math.min(maxBufferSize, samples.size - offset)
                callback.audioAvailable(samples, offset, bytesToWrite)
                offset += bytesToWrite
            }

            // 1 means to continue
            // 0 means to stop
            return 1
        }

        //Log.i(TAG, "text: $text")
        tts.generateWithCallback(
            text = text,
            sid = TtsEngine.speakerId,
            speed = TtsEngine.speed,
            callback = ttsCallback,
        )

        callback.done()
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
