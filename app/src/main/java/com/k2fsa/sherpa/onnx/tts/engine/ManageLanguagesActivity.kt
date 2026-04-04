package com.k2fsa.sherpa.onnx.tts.engine

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityManageLanguagesBinding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ManageLanguagesActivity  : AppCompatActivity() {
    private var binding: ActivityManageLanguagesBinding? = null

    // URIs for selected files
    private var modelFileUri: Uri? = null
    private var tokensFileUri: Uri? = null
    
    // Store lang_code for later use
    private var langCodeForInstallation: String = ""

    private var langCode: String = ""
    private var modelName: String = ""

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
    
    fun installFromSD(view: View) {
        sdInstall()
    }
    fun sdInstall() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_install_custom_model, null)

        val langInput = dialogView.findViewById<EditText>(R.id.editTextLangCode)
        val modelInput = dialogView.findViewById<EditText>(R.id.editTextModelName)
        val selectModelBtn = dialogView.findViewById<Button>(R.id.buttonSelectModel)
        val selectTokensBtn = dialogView.findViewById<Button>(R.id.buttonSelectTokens)
        val installBtn = dialogView.findViewById<Button>(R.id.buttonInstall)

        // Initialize button text/visibility 
        selectModelBtn.setOnClickListener {
            modelPickerLauncher.launch(arrayOf("application/octet-stream"))
        }

        selectTokensBtn.setOnClickListener {
            tokensPickerLauncher.launch(arrayOf("text/plain"))
        }

        installBtn.setOnClickListener {
            langCode = langInput.text.toString().trim()
            modelName = modelInput.text.toString().trim()

            // Validate
            if (langCode.length != 3) {
                Toast.makeText(this, R.string.language_code_error, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (modelName.isEmpty()) {
                Toast.makeText(this, getString(R.string.enter_model_name), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (modelFileUri == null) {
                Toast.makeText(this, getString(R.string.select_model_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (tokensFileUri == null) {
                Toast.makeText(this, getString(R.string.select_tokens_file), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check duplicates
            val db = LangDB.getInstance(this)
            if (db.allInstalledLanguages.any { it.lang == langCode }) {
                Toast.makeText(this, R.string.language_already_installed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Proceed with install
            installCustomModel(langCode, modelFileUri!!, tokensFileUri!!)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.install_from_sd))
            .setView(dialogView)
            .setNegativeButton(getString(android.R.string.cancel)) { dialogInterface, _ -> dialogInterface.dismiss() }
            .create()

        dialog.show()
    }

    private fun installCustomModel(langCode: String, modelUri: Uri, tokensUri: Uri) {
        // Create directory for the language (country code is empty as requested)
        val directory = File(this.getExternalFilesDir(null), "/$langCode/")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Copy model.onnx file
        val modelDest = File(directory, Downloader.onnxModel)
        try {
            copyFile(modelUri, modelDest)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Copy tokens.txt file
        val tokensDest = File(directory, Downloader.tokens)
        try {
            copyFile(tokensUri, tokensDest)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_copying_files, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add to database as vits-piper model with empty country code
        val db = LangDB.getInstance(this)
        db.addLanguage(modelName, langCode, "", 0, 1.0f, 1.0f, "vits-piper")
        
        // Show success message
        Toast.makeText(this, "+ \"$langCode\" = \"$modelName\" ", Toast.LENGTH_SHORT).show()
        val preferenceHelper = PreferenceHelper(this)
        preferenceHelper.setCurrentLanguage(langCode)
        // Reset file URIs and lang code for next use
        modelFileUri = null
        tokensFileUri = null
        langCodeForInstallation = ""
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
    }
    
    private fun copyFile(sourceUri: Uri, destFile: File) {
        this.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    // Register for file pickers 
    private val modelPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            modelFileUri = uri
        }
    }

    private val tokensPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            tokensFileUri = uri
        }
    }
}
