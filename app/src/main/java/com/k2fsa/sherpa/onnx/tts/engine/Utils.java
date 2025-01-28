package com.k2fsa.sherpa.onnx.tts.engine;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class Utils {
    public static boolean checkModel(Context context){
        File sdcardDataFolder = context.getExternalFilesDir(null);
        File model = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/model.onnx");
        File tokens = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/tokens.txt");
        File lang = new File(sdcardDataFolder.getAbsolutePath(), "modelDir/lang");
        return model.exists() && tokens.exists() && lang.exists();
    }

    public static String readLanguage(Context context){
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
