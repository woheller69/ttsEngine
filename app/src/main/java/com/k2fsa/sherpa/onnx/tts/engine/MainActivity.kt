@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {
    // TODO(fangjun): Save settings in ttsViewModel
    private val ttsViewModel: TtsViewModel by viewModels()

    private lateinit var track: AudioTrack

    private var stopped: Boolean = false

    private var samplesChannel = Channel<FloatArray>()
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var langDB: LangDB
    private var volume: Float = 1.0f

    override fun onPause() {
        super.onPause()
        samplesChannel.close()
    }

    override fun onResume() {
        //Reset speed in case it has been changed by TtsService
        val db = LangDB.getInstance(this)
        val languages = db.allInstalledLanguages
        val language = languages.first{it.lang == TtsEngine.lang}
        TtsEngine.speed = language.speed
        super.onResume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceHelper = PreferenceHelper(this)
        langDB = LangDB.getInstance(this)
        Migrate.renameModelFolder(this)   //Rename model folder if "old" structure
        if (!preferenceHelper.getCurrentLanguage().equals("")){
            TtsEngine.createTts(this, preferenceHelper.getCurrentLanguage()!!)
            initAudioTrack()
            setupDisplay(langDB, preferenceHelper)
            if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(
                this,
                "https://github.com/woheller69/ttsengine"
            )
        } else {
            val intent = Intent(this, ManageLanguagesActivity::class.java)
            startActivity(intent)
            finish()
       }
    }

    private fun restart(){
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    private fun setupDisplay(
        langDB: LangDB,
        preferenceHelper: PreferenceHelper
    ) {
        setContent {
            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            intent = Intent()
                            intent.setAction("com.android.settings.TTS_SETTINGS")
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            this.startActivity(intent)
                            finish()
                        }
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "TTS Settings")
                    }
                }
            ) { padding ->
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Scaffold(topBar = {
                        TopAppBar(title = { Text("SherpaTTS") },
                            actions = {
                                IconButton(
                                    onClick = {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/ttsengine")))
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = colorResource(R.color.primaryDark))
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = "Info")
                                }
                            })
                    }) {
                        Box(modifier = Modifier.padding(it)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    getString(R.string.speed) + " " + String.format(
                                        "%.1f",
                                        TtsEngine.speed
                                    )
                                )
                                Slider(
                                    value = TtsEngine.speedState.value,
                                    onValueChange = {
                                        TtsEngine.speed = it
                                    },
                                    onValueChangeFinished = {
                                        langDB.updateLang(
                                            TtsEngine.lang,
                                            TtsEngine.speakerId,
                                            TtsEngine.speed
                                        )
                                    },
                                    valueRange = 0.2F..3.0F,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorResource(R.color.primaryDark),
                                        activeTrackColor = colorResource(R.color.primaryDark)
                                    )
                                )

                                var applySystemSpeed by remember { mutableStateOf(preferenceHelper.applySystemSpeed()) }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = applySystemSpeed,
                                        onCheckedChange = { isChecked ->
                                            preferenceHelper.setApplySystemSpeed(isChecked)
                                            applySystemSpeed = isChecked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = colorResource(R.color.primaryDark)
                                        )
                                    )
                                    Text(
                                        getString(R.string.apply_system_speed)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val testTextContent = getSampleText(TtsEngine.lang ?: "")

                                var testText by remember { mutableStateOf(testTextContent) }
                                val scrollState = rememberScrollState(0)


                                val numLanguages = langDB.allInstalledLanguages.size

                                val languages = langDB.allInstalledLanguages
                                var selectedLang =
                                    languages.indexOfFirst { it.lang == preferenceHelper.getCurrentLanguage()!! }
                                var expanded by remember { mutableStateOf(false) }
                                val langList = (0 until numLanguages).toList()
                                val keyboardController = LocalSoftwareKeyboardController.current

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = it }
                                    ) {
                                        var displayText = languages[selectedLang].lang
                                        if (languages[selectedLang].name.isNotEmpty()) displayText = "$displayText (${languages[selectedLang].name})"
                                        OutlinedTextField(
                                            value = displayText,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text(getString(R.string.language_id))},
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        expanded = true
                                                        keyboardController?.hide()
                                                    }
                                                },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = "Dropdown"
                                                )
                                            }
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            langList.forEach { langId ->
                                                var dropdownText = languages[langId].lang
                                                if (languages[langId].name.isNotEmpty()) dropdownText = "$dropdownText (${languages[langId].name})"
                                                DropdownMenuItem(
                                                    text = { Text(dropdownText)},
                                                    onClick = {
                                                        selectedLang = langId
                                                        preferenceHelper.setCurrentLanguage(
                                                            languages[langId].lang
                                                        )
                                                        expanded = false
                                                        restart()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }


                                val numSpeakers = TtsEngine.tts!!.numSpeakers()
                                if (numSpeakers > 1) {
                                    var expanded by remember { mutableStateOf(false) }
                                    val speakerList = (0 until numSpeakers).toList()
                                    var selectedSpeaker by remember { mutableStateOf(TtsEngine.speakerId) }
                                    val keyboardController = LocalSoftwareKeyboardController.current

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = it }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedSpeaker.toString(),
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text(getString(R.string.speaker_id) + " " + "(0-${numSpeakers - 1})") },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .menuAnchor()
                                                    .onFocusChanged { focusState ->
                                                        if (focusState.isFocused) {
                                                            expanded = true
                                                            keyboardController?.hide()
                                                        }
                                                    },
                                                trailingIcon = {
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                                }
                                            )
                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                speakerList.forEach { speakerId ->
                                                    DropdownMenuItem(
                                                        text = { Text(speakerId.toString()) },
                                                        onClick = {
                                                            selectedSpeaker = speakerId
                                                            TtsEngine.speakerId = speakerId
                                                            langDB.updateLang(
                                                                TtsEngine.lang,
                                                                TtsEngine.speakerId,
                                                                TtsEngine.speed
                                                            )
                                                            expanded = false
                                                            stopped = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row {
                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            val intent = Intent(applicationContext, ManageLanguagesActivity::class.java)
                                            startActivity(intent)
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_add_24dp),
                                            contentDescription = stringResource(id = R.string.add_language)
                                        )
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            deleteLang(preferenceHelper.getCurrentLanguage())
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_delete_24dp),
                                            contentDescription = stringResource(id = R.string.delete_language)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = testText,
                                    onValueChange = { testText = it },
                                    label = { Text(getString(R.string.input)) },
                                    maxLines = 10,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .verticalScroll(scrollState)
                                        .wrapContentHeight(),
                                    singleLine = false
                                )

                                volume = preferenceHelper.getVolume()
                                var displayVol by remember { mutableStateOf(preferenceHelper.getVolume()) }
                                Text(
                                    getString(R.string.volume) + " " + String.format(
                                        "%.1f",
                                        displayVol
                                    )
                                )

                                Slider(
                                    value = displayVol,
                                    onValueChange = {
                                        displayVol = it
                                        volume = it
                                    },
                                    onValueChangeFinished = {
                                        preferenceHelper.setVolume(volume)
                                    },
                                    valueRange = 0.2F..5.0F,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colorResource(R.color.primaryDark),
                                        activeTrackColor = colorResource(R.color.primaryDark)
                                    )
                                )

                                Row {
                                    Button(
                                        enabled = true,
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            if (testText.isBlank() || testText.isEmpty()) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    getString(R.string.input),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                stopped = false

                                                track.pause()
                                                track.flush()
                                                track.play()

                                                samplesChannel = Channel<FloatArray>()

                                                CoroutineScope(Dispatchers.IO).launch {
                                                    for (samples in samplesChannel) {
                                                        for (i in samples.indices) {
                                                            samples[i] *= volume
                                                        }
                                                        track.write(
                                                            samples,
                                                            0,
                                                            samples.size,
                                                            AudioTrack.WRITE_BLOCKING
                                                        )
                                                    }
                                                }

                                                CoroutineScope(Dispatchers.Default).launch {
                                                    TtsEngine.tts!!.generateWithCallback(
                                                        text = testText,
                                                        sid = TtsEngine.speakerId,
                                                        speed = TtsEngine.speed,
                                                        callback = ::callback,
                                                    )
                                                }.start()
                                            }
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_play_24dp),
                                            contentDescription = stringResource(id = R.string.play)
                                        )
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            stopped = true
                                            track.pause()
                                            track.flush()
                                        }) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_stop_24dp),
                                            contentDescription = stringResource(id = R.string.stop)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun deleteLang(currentLanguage: String?) {
        TtsEngine.tts = null //reset TtsEngine to make sure a new voice is loaded at next start
        val country: String
        val languages = langDB.allInstalledLanguages
        val language = languages.first{it.lang == currentLanguage}
        country = language.country

        val subdirectoryName = currentLanguage + country
        val subdirectory = File(getExternalFilesDir(null), subdirectoryName)

        if (subdirectory.exists() && subdirectory.isDirectory) {
            val files = subdirectory.listFiles()

            files?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }

            subdirectory.delete()
            langDB.removeLang(currentLanguage)
            if (langDB.allInstalledLanguages.isEmpty()) preferenceHelper.setCurrentLanguage("")
            else preferenceHelper.setCurrentLanguage(langDB.allInstalledLanguages[0].lang)
        }
        restart()
    }

    override fun onDestroy() {
        if (this::track.isInitialized) track.release()
        super.onDestroy()
    }

    // this function is called from C++
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
               if (!samplesChannel.isClosedForSend) samplesChannel.send(samplesCopy)
            }
            return 1
        } else {
            track.stop()
            Log.i(TAG, " return 0")
            return 0
        }
    }

    private fun initAudioTrack() {
        val sampleRate = TtsEngine.tts!!.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Log.i(TAG, "sampleRate: $sampleRate, buffLength: $bufLength")

        val attr = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(
            attr, format, bufLength, AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.play()
    }
}
