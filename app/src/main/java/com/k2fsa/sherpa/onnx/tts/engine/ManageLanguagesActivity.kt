package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityManageLocationsBinding
import java.util.Locale

class ManageLanguagesActivity  : AppCompatActivity() {
    private var binding: ActivityManageLocationsBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageLocationsBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        val allModels: Array<String> = resources.getStringArray(R.array.models)

        val db = LangDB.getInstance(this)
        val installedLanguages = db.allInstalledLanguages
        val installedLangCodes = java.util.ArrayList<String>()
        for (language in installedLanguages) {
            installedLangCodes.add(language.lang)
        }

        val showModels = mutableListOf<String>()
        for(model in allModels){
            val twoLetterCode: String = model.split("_").get(0)
            val lang = Locale(twoLetterCode).isO3Language
            if (!installedLangCodes.contains(lang)) showModels.add(model)
        }

        val mAdapter = ArrayAdapter(this, R.layout.list_item, R.id.text_view, showModels)

        binding!!.modelList.adapter = mAdapter
        binding!!.modelList.setOnItemClickListener { parent, view, position, id ->
            val model = "vits-piper-" + showModels.get(position)
            binding!!.modelList.visibility = View.GONE
            binding!!.buttonTestVoices.visibility = View.GONE
            Downloader.downloadModels(this, binding, model)
        }

    }

    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    fun testVoices(view: View) {startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://rhasspy.github.io/piper-samples/")))}

}
