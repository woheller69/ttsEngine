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
            String language = readLanguageFromFile(context);
            File modelDirFolder = new File(sdcardDataFolder.getAbsolutePath(),"modelDir");
            File langFolder = new File(sdcardDataFolder.getAbsolutePath(),language);
            modelDirFolder.renameTo(langFolder);
            PreferenceHelper preferenceHelper = new PreferenceHelper(context);
            preferenceHelper.setCurrentLanguage(language);
            LangDB langDB = LangDB.getInstance(context);
            langDB.addLanguage("???",language, "", preferenceHelper.getSid(), preferenceHelper.getSpeed(), "vits-piper");
        }
    }

    public static String readLanguageFromFile(Context context){
        StringBuilder text = new StringBuilder();
        File sdcardDataFolder = context.getExternalFilesDir(null);
        File langFile = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/lang");
        if (!langFile.exists()) return "eng";
        try {
            BufferedReader br = new BufferedReader(new FileReader(langFile));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return text.toString().trim();
    }
}
