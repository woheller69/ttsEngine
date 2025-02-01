@file:OptIn(ExperimentalMaterial3Api::class)

package com.k2fsa.sherpa.onnx.tts.engine

import PreferenceHelper
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.TimeSource

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {
    // TODO(fangjun): Save settings in ttsViewModel
    private val ttsViewModel: TtsViewModel by viewModels()

    private var mediaPlayer: MediaPlayer? = null

    // see
    // https://developer.android.com/reference/kotlin/android/media/AudioTrack
    private lateinit var track: AudioTrack

    private var stopped: Boolean = false

    private var samplesChannel = Channel<FloatArray>()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Utils.checkModel(this)){
            Log.i(TAG, "Start to initialize TTS")
            TtsEngine.createTts(this)
            Log.i(TAG, "Finish initializing TTS")

            Log.i(TAG, "Start to initialize AudioTrack")
            initAudioTrack()
            Log.i(TAG, "Finish initializing AudioTrack")
        } else {
            val intent = Intent(this, DownloadActivity::class.java)
            startActivity(intent)
            finish()
       }

        val preferenceHelper = PreferenceHelper(this)
        setContent {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Scaffold(topBar = {
                        TopAppBar(title = { Text("SherpaTTS") })
                    }) {
                        Box(modifier = Modifier.padding(it)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Column {
                                    Text(getString(R.string.speed)+ " " + String.format("%.1f", TtsEngine.speed))
                                    Slider(
                                        value = TtsEngine.speedState.value,
                                        onValueChange = {
                                            TtsEngine.speed = it
                                            preferenceHelper.setSpeed(it)
                                        },
                                        valueRange = 0.2F..3.0F,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                                thumbColor = colorResource(R.color.primaryDark),
                                                activeTrackColor = colorResource(R.color.primaryDark)
                                        )
                                    )
                                }

                                val testTextContent = getSampleText(TtsEngine.lang ?: "")

                                var testText by remember { mutableStateOf(testTextContent) }
                                var startEnabled by remember { mutableStateOf(true) }
                                var playEnabled by remember { mutableStateOf(false) }
                                var rtfText by remember {
                                    mutableStateOf("")
                                }
                                val scrollState = rememberScrollState(0)

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
                                                label = { Text(getString(R.string.speaker_id) + " " +"(0-${numSpeakers - 1})") },
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
                                                            preferenceHelper.setSid(speakerId)
                                                            expanded = false
                                                            stopped = true
                                                            playEnabled = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
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

                                Row {
                                    Button(
                                        enabled = startEnabled,
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            Log.i(TAG, "Clicked, text: $testText")
                                            if (testText.isBlank() || testText.isEmpty()) {
                                                Toast.makeText(
                                                    applicationContext,
                                                    getString(R.string.input),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                startEnabled = false
                                                playEnabled = false
                                                stopped = false

                                                track.pause()
                                                track.flush()
                                                track.play()
                                                rtfText = ""
                                                Log.i(TAG, "Started with text $testText")

                                                samplesChannel = Channel<FloatArray>()

                                                CoroutineScope(Dispatchers.IO).launch {
                                                    for (samples in samplesChannel) {
                                                        track.write(
                                                            samples,
                                                            0,
                                                            samples.size,
                                                            AudioTrack.WRITE_BLOCKING
                                                        )
                                                        if (stopped) {
                                                            break
                                                        }
                                                    }
                                                }

                                                CoroutineScope(Dispatchers.Default).launch {
                                                    val timeSource = TimeSource.Monotonic
                                                    val startTime = timeSource.markNow()

                                                    val audio =
                                                        TtsEngine.tts!!.generateWithCallback(
                                                            text = testText,
                                                            sid = TtsEngine.speakerId,
                                                            speed = TtsEngine.speed,
                                                            callback = ::callback,
                                                        )

                                                    val elapsed =
                                                        startTime.elapsedNow().inWholeMilliseconds.toFloat() / 1000;
                                                    val audioDuration =
                                                        audio.samples.size / TtsEngine.tts!!.sampleRate()
                                                            .toFloat()
                                                    val RTF = String.format(
                                                        "Number of threads: %d\nElapsed: %.3f s\nAudio duration: %.3f s\nRTF: %.3f/%.3f = %.3f",
                                                        TtsEngine.tts!!.config.model.numThreads,
                                                        audioDuration,
                                                        elapsed,
                                                        elapsed,
                                                        audioDuration,
                                                        elapsed / audioDuration
                                                    )
                                                    samplesChannel.close()

                                                    val filename =
                                                        application.filesDir.absolutePath + "/generated.wav"


                                                    val ok =
                                                        audio.samples.isNotEmpty() && audio.save(
                                                            filename
                                                        )

                                                    if (ok) {
                                                        withContext(Dispatchers.Main) {
                                                            startEnabled = true
                                                            playEnabled = true
                                                            rtfText = RTF
                                                        }
                                                    }
                                                }.start()
                                            }
                                        }) {
                                        Text(getString(R.string.start))
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        enabled = playEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            stopped = true
                                            track.pause()
                                            track.flush()
                                            onClickPlay()
                                        }) {
                                        Text(getString(R.string.play))
                                    }

                                    Button(
                                        modifier = Modifier.padding(5.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colorResource(R.color.primaryDark),
                                            contentColor = colorResource(R.color.white)
                                        ),
                                        onClick = {
                                            onClickStop()
                                            startEnabled = true
                                        }) {
                                        Text(getString(R.string.stop))
                                    }
                                }
                                if (rtfText.isNotEmpty()) {
                                    Row {
                                        Text(rtfText)
                                    }
                                }
                            }
                        }
                    }
                }

        }
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(
            this,
            "https://github.com/woheller69/ttsengine"
        )
    }

    override fun onDestroy() {
        stopMediaPlayer()
        super.onDestroy()
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun onClickPlay() {
        val filename = application.filesDir.absolutePath + "/generated.wav"
        stopMediaPlayer()
        mediaPlayer = MediaPlayer.create(
            applicationContext,
            Uri.fromFile(File(filename))
        )
        mediaPlayer?.start()
    }

    private fun onClickStop() {
        stopped = true
        track.pause()
        track.flush()

        stopMediaPlayer()
    }

    // this function is called from C++
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                samplesChannel.send(samplesCopy)
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
