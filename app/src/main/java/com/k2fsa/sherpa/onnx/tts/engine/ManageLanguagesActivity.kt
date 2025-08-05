package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityManageLanguagesBinding
import java.util.Locale

class ManageLanguagesActivity  : AppCompatActivity() {
    private var binding: ActivityManageLanguagesBinding? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageLanguagesBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ThemeUtil.setStatusBarAppearance(this)
        val allPiperModels: Array<String> = resources.getStringArray(R.array.piper_models)
        val allCoquiModels: Array<String> = resources.getStringArray(R.array.coqui_models)

        val db = LangDB.getInstance(this)
        val installedLanguages = db.allInstalledLanguages
        val installedLangCodes = installedLanguages.map { it.lang }

        val showPiperModels = mutableListOf<String>()
        for(model in allPiperModels){
            val twoLetterCode: String = model.split("_").get(0)
            val lang = Locale(twoLetterCode).isO3Language
            if (!installedLangCodes.contains(lang)) showPiperModels.add(model)
        }

        val showCoquiModels = mutableListOf<String>()
        for(model in allCoquiModels){
            val twoLetterCode: String = model.split("_").get(0)
            val lang = Locale(twoLetterCode).isO3Language
            if (!installedLangCodes.contains(lang)) showCoquiModels.add(model)
        }

        val piperAdapter = ArrayAdapter(this, R.layout.list_item, R.id.text_view, showPiperModels)
        val coquiAdapter = ArrayAdapter(this, R.layout.list_item, R.id.text_view, showCoquiModels)

        binding!!.piperModelList.adapter = piperAdapter
        binding!!.piperModelList.setOnItemClickListener { parent, view, position, id ->
            val model = showPiperModels.get(position)
            val twoLetterCode = model.substring(0, 2)
            val country = model.substring(3, 5)
            val lang = Locale(twoLetterCode).isO3Language
            val type = "vits-piper"
            binding!!.piperModelList.visibility = View.GONE
            binding!!.coquiModelList.visibility = View.GONE
            binding!!.buttonTestVoices.visibility = View.GONE
            binding!!.piperHeader.visibility = View.GONE
            binding!!.coquiHeader.visibility = View.GONE
            binding!!.downloadSize.setText("")
            Downloader.downloadModels(this, binding, model, lang, country, type)
        }

        binding!!.coquiModelList.adapter = coquiAdapter
        binding!!.coquiModelList.setOnItemClickListener { parent, view, position, id ->
            val model = showCoquiModels.get(position)
            val twoLetterCode = model.substring(0, 2)
            val country = ""
            val lang = Locale(twoLetterCode).isO3Language
            val type = "vits-coqui"
            binding!!.piperModelList.visibility = View.GONE
            binding!!.coquiModelList.visibility = View.GONE
            binding!!.buttonTestVoices.visibility = View.GONE
            binding!!.piperHeader.visibility = View.GONE
            binding!!.coquiHeader.visibility = View.GONE
            binding!!.downloadSize.setText("")
            Downloader.downloadModels(this, binding, model, lang, country, type)
        }

    }

    fun startMain(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }

    fun testVoices(view: View) {startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/k2-fsa/text-to-speech/")))}

}
