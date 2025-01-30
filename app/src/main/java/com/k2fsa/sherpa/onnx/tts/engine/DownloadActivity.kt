package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityDownloadBinding

class DownloadActivity  : AppCompatActivity() {
    private var binding: ActivityDownloadBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
    }


    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    fun downloadDeutsch(view: View) {

        val model = "vits-piper-de_DE-thorsten-medium"

        Downloader.downloadModels(this, binding,model)
    }

    fun downloadEnglish(view: View) {

        val model = "vits-piper-en_US-joe-medium"

        Downloader.downloadModels(this, binding,model)
    }

    fun downloadFrench(view: View) {

        val model = "vits-piper-fr_FR-tom-medium"

        Downloader.downloadModels(this, binding,model)
    }
    fun downloadSpanish(view: View) {

        val model = "vits-piper-es_ES-davefx-medium"

        Downloader.downloadModels(this, binding,model)
    }
    fun downloadPortuguese(view: View) {

        val model = "vits-piper-pt_PT-tugao-medium"

        Downloader.downloadModels(this, binding,model)
    }
}
