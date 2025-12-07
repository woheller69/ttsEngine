package com.k2fsa.sherpa.onnx.tts.engine;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

//This class can be removed in future if migration from old structure is no longer needed, e.g. in 2026
public class Migrate {

    //Move model from old modelDir to new language based folder structure
    public static void renameModelFolder(Context context){
        File sdcardDataFolder = context.getExternalFilesDir(null);
        File lang = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/lang");
        if (lang.exists()) { //move to new directory structure
            String[] model = readLanguageFromFile(context);
            File modelDirFolder = new File(sdcardDataFolder.getAbsolutePath(),"modelDir");
            File langFolder = new File(sdcardDataFolder.getAbsolutePath(),model[0]);
            modelDirFolder.renameTo(langFolder);
            PreferenceHelper preferenceHelper = new PreferenceHelper(context);
            preferenceHelper.setCurrentLanguage(model[0]);
            LangDB langDB = LangDB.getInstance(context);
            langDB.addLanguage(model[1], model[0], "", 0, 1.0f, 1.0f, "vits-piper");
        }
    }

    public static String[] readLanguageFromFile(Context context) {
        File sdcardDataFolder = context.getExternalFilesDir(null);
        File langFile = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/lang");

        // Default fallback values
        String languageCode = "eng";
        String languageName = "???";

        try (BufferedReader br = new BufferedReader(new FileReader(langFile))) {
            String line1 = br.readLine(); // language code
            String line2 = br.readLine(); // language name

            if (line1 != null) {
                languageCode = line1.trim();
            }
            if (line2 != null) {
                languageName = line2.trim();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String[]{languageCode, languageName};
    }
}
