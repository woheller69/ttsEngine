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
        val modelUrl = "https://huggingface.co/csukuangfj/vits-piper-de_DE-thorsten-medium/resolve/main/de_DE-thorsten-medium.onnx"
        val tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-de_DE-thorsten-medium/resolve/main/tokens.txt"
        val lang ="deu"
        Downloader.downloadModels(this,binding,modelUrl,tokensUrl,lang)
    }

    fun downloadEnglish(view: View) {
        val modelUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-joe-medium/resolve/main/en_US-joe-medium.onnx"
        val tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-en_US-joe-medium/resolve/main/tokens.txt"
        val lang ="eng"
        Downloader.downloadModels(this,binding,modelUrl,tokensUrl,lang)
    }

    fun downloadFrench(view: View) {
        val modelUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-tom-medium/resolve/main/fr_FR-tom-medium.onnx"
        val tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-fr_FR-tom-medium/resolve/main/tokens.txt"
        val lang ="fra"
        Downloader.downloadModels(this,binding,modelUrl,tokensUrl,lang)
    }
    fun downloadSpanish(view: View) {
        val modelUrl = "https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium/resolve/main/es_ES-davefx-medium.onnx"
        val tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-es_ES-davefx-medium/resolve/main/tokens.txt"
        val lang ="spa"
        Downloader.downloadModels(this,binding,modelUrl,tokensUrl,lang)
    }
    fun downloadPortuguese(view: View) {
        val modelUrl = "https://huggingface.co/csukuangfj/vits-piper-pt_PT-tugao-medium/resolve/main/pt_PT-tugao-medium.onnx"
        val tokensUrl = "https://huggingface.co/csukuangfj/vits-piper-pt_PT-tugao-medium/resolve/main/tokens.txt"
        val lang ="por"
        Downloader.downloadModels(this,binding,modelUrl,tokensUrl,lang)
    }
}
