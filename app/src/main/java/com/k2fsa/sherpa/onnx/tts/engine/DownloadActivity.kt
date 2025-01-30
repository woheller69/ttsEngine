package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityDownloadBinding

class DownloadActivity  : AppCompatActivity() {
    private var binding: ActivityDownloadBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        val mItems: Array<String> = resources.getStringArray(R.array.models)
        val mAdapter = ArrayAdapter(this, R.layout.list_item, R.id.text_view, mItems)

        binding!!.modelList.adapter = mAdapter
        binding!!.modelList.setOnItemClickListener { parent, view, position, id ->
            val model = "vits-piper-" + mItems.get(position)
            binding!!.modelList.visibility = View.GONE
            Downloader.downloadModels(this, binding, model)
        }

    }

    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

}
