package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TtsEngine {
    private val ttsCache = mutableMapOf<String, OfflineTts>()
    var tts: OfflineTts? = null

    // https://en.wikipedia.org/wiki/ISO_639-3
    var lang: String? = ""
    var country: String? = ""

    var volume: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speed: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speakerId: MutableState<Int> = mutableIntStateOf(0)

    private var modelName: String = "model.onnx"
    private var acousticModelName: String? = null // for matcha tts
    private var vocoder: String? = null // for matcha tts
    private var voices: String? = null // for kokoro
    private var ruleFsts: String? = null
    private var ruleFars: String? = null
    private var lexicon: String? = null
    private var dataDir: String = "espeak-ng-data"
    private var dictDir: String? = null

    fun getAvailableLanguages(context: Context): ArrayList<String> {
        val langCodes = java.util.ArrayList<String>()
        val db = LangDB.getInstance(context)
        val allLanguages = db.allInstalledLanguages
        for (language in allLanguages) {
            langCodes.add(language.lang)
        }
        return langCodes
    }

    fun createTts(context: Context, language: String) {
        if (tts == null || lang != language) {
            if (ttsCache.containsKey(language)) {
                Log.i(TAG, "From TTS cache: " + language)
                tts = ttsCache[language]
                loadLanguageSettings(context, language)
            } else {
                initTts(context, language)
            }
        }
    }

    private fun loadLanguageSettings(context: Context, language: String) {
        val db = LangDB.getInstance(context)
        val currentLanguage = db.allInstalledLanguages.first { it.lang == language }
        this.lang = language
        this.country = currentLanguage.country
        this.speed.value = currentLanguage.speed
        this.speakerId.value = currentLanguage.sid
        this.volume.value = currentLanguage.volume
        PreferenceHelper(context).setCurrentLanguage(language)
    }

    fun removeLanguageFromCache(language: String) {
        ttsCache.remove(language)
        Log.i(TAG, "Removed TTS cache for: $language")
        Log.i(TAG, "TTS cache size:"+ ttsCache.size)
    }

    private fun initTts(context: Context, lang: String) {
        Log.i(TAG, "Add to TTS cache: " + lang)

        loadLanguageSettings(context, lang)

        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath

        val modelDir = "$externalFilesDir/$lang$country"

        var newDataDir = ""
        if (dataDir != null) {
            newDataDir = copyDataDir(context, dataDir!!)
        }

        if (dictDir != null) {
            val newDir = copyDataDir(context, dictDir!!)
            dictDir = "$newDir/$dictDir"
            ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
        }

        val config = getOfflineTtsConfig(
            modelDir = modelDir!!,
            modelName = modelName ?: "",
            acousticModelName = acousticModelName ?: "",
            vocoder = vocoder ?: "",
            voices = voices ?: "",
            lexicon = lexicon ?: "",
            dataDir = newDataDir ?: "",
            dictDir = dictDir ?: "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: ""
        )

        val configDebugOff = config.copy(  // create a new instance with debug switched off
            model = config.model.copy(debug = false)
        )

        tts = OfflineTts(assetManager = null, config = configDebugOff)
        ttsCache[lang] = tts!!
        Log.i(TAG, "TTS cache size:"+ ttsCache.size)
    }

    private fun copyDataDir(context: Context, dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        if (!PreferenceHelper(context).isInitFinished()){  //only copy at first startup
            copyAssets(context, dataDir)
            PreferenceHelper(context).setInitFinished()
        }
        val newDataDir = context.getExternalFilesDir(null)!!.absolutePath + "/" + dataDir
        Log.i(TAG, "newDataDir: $newDataDir")
        return newDataDir
    }

    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets!!.isEmpty()) {
                copyFile(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets.iterator()) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy $path. $ex")
        }
    }

    private fun copyFile(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null)!!.absolutePath + "/" + filename
            val file = File(newFilename)
            if (!file.exists()) {
                val ostream = FileOutputStream(newFilename)
                val buffer = ByteArray(1024)
                var read = 0
                while (read != -1) {
                    ostream.write(buffer, 0, read)
                    read = istream.read(buffer)
                }
                istream.close()
                ostream.flush()
                ostream.close()
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy $filename, $ex")
        }
    }
}
