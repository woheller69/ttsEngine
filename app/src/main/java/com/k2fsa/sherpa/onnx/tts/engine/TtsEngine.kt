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
    var tts: OfflineTts? = null

    // https://en.wikipedia.org/wiki/ISO_639-3
    var lang: String? = ""
    var country: String? = ""

    val volumeState: MutableState<Float> = mutableFloatStateOf(1.0F)
    val speedState: MutableState<Float> = mutableFloatStateOf(1.0F)
    val speakerIdState: MutableState<Int> = mutableIntStateOf(0)

    var volume: Float
        get() = volumeState.value
        set(value) {
            volumeState.value = value
        }

    var speed: Float
        get() = speedState.value
        set(value) {
            speedState.value = value
        }

    var speakerId: Int
        get() = speakerIdState.value
        set(value) {
            speakerIdState.value = value
        }

    private var modelDir: String? = null
    private var modelName: String? = null
    private var acousticModelName: String? = null // for matcha tts
    private var vocoder: String? = null // for matcha tts
    private var voices: String? = null // for kokoro
    private var ruleFsts: String? = null
    private var ruleFars: String? = null
    private var lexicon: String? = null
    private var dataDir: String? = null
    private var dictDir: String? = null
    private var assets: AssetManager? = null

    init {
        modelName = "model.onnx"
        modelDir = "modelDir"
        ruleFsts = null
        ruleFars = null
        lexicon = null
        dataDir = "espeak-ng-data"
        dictDir = null
        lang = ""
    }

    fun getAvailableLanguages(context: Context): ArrayList<String> {
        val langCodes = java.util.ArrayList<String>()
        val db = LangDB.getInstance(context)
        val languages = db.allInstalledLanguages
        for (language in languages) {
            langCodes.add(language.lang)
        }
        return langCodes
    }

    fun createTts(context: Context, language: String) {
        if (tts == null || lang != language) {
            initTts(context, language)
        }
    }

    private fun initTts(context: Context, language: String) {
        Log.i(TAG, "Init Next-gen Kaldi TTS: " + language)
        lang = language
        PreferenceHelper(context).setCurrentLanguage(lang!!)
        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath

        val db = LangDB.getInstance(context)
        val languages = db.allInstalledLanguages
        val language = languages.first{it.lang == lang}
        speed = language.speed
        speakerId = language.sid
        country = language.country
        volume = language.volume


        modelDir = "$externalFilesDir/$lang$country"

        assets = context.assets

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
