package com.k2fsa.sherpa.onnx.tts.engine;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import com.k2fsa.sherpa.onnx.tts.engine.databinding.ActivityDownloadBinding;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
    static final String language = "lang";
    static final String onnxModel = "model.onnx";
    static final String tokens = "tokens.txt";
    static long onnxModelDownloadSize = 0L;
    static long tokensDownloadSize = 0L;
    static boolean onnxModelFinished = false;
    static boolean tokensFinished = false;
    static boolean langFinished = false;

    public static void downloadModels(final Activity activity, ActivityDownloadBinding binding, String model) {

        String twoLetterCode = model.split("piper-")[1].substring(0, 2);
        String lang = new Locale(twoLetterCode).getISO3Language();
        String modelName = model.split("piper-")[1]+".onnx";
        String onnxModelUrl = "https://huggingface.co/csukuangfj/"+ model + "/resolve/main/" + modelName;
        String tokensUrl = "https://huggingface.co/csukuangfj/" + model + "/resolve/main/tokens.txt";

        File directory = new File(activity.getExternalFilesDir(null)+ "/modelDir/");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e("TTS Engine", "Failed to make directory: " + directory);
            return;
        }

        File langFile = new File(activity.getExternalFilesDir(null)+ "/modelDir/" + language);
        if (langFile.exists()) langFile.delete();
        langFinished = false;
        Thread threadLang = new Thread(() -> {
            try {
                langFile.createNewFile();
                FileOutputStream outStream = new FileOutputStream(langFile);
                outStream.write(lang.getBytes());
                outStream.flush();
                outStream.close();
                langFinished = true;
                activity.runOnUiThread(() -> {
                    if (tokensFinished && onnxModelFinished && langFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        threadLang.start();

        File onnxModelFile = new File(activity.getExternalFilesDir(null)+ "/modelDir/" + onnxModel);
        if (!onnxModelFile.exists()) {
            onnxModelFinished = false;
            Log.d("TTS Engine", "onnx model file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url;

                    url = new URL(onnxModelUrl);

                    Log.d("TTS Engine", "Download model");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    onnxModelFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(onnxModelFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (onnxModelFile.exists()) onnxModelDownloadSize = onnxModelFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((tokensDownloadSize + onnxModelDownloadSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!onnxModelFile.exists()) {
                        throw new IOException();
                    }

                    onnxModelFinished = true;
                    activity.runOnUiThread(() -> {
                        if (tokensFinished && onnxModelFinished && langFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                    });
                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    onnxModelFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            onnxModelFinished = true;
            activity.runOnUiThread(() -> {
                if (tokensFinished && onnxModelFinished) binding.buttonStart.setVisibility(View.VISIBLE);
            });
        }

        File tokensFile = new File(activity.getExternalFilesDir(null) + "/modelDir/" + tokens);
        if (!tokensFile.exists()) {
            tokensFinished = false;
            Log.d("TTS Engine", "tokens file does not exist");
            Thread thread = new Thread(() -> {
                try {
                    URL url = new URL(tokensUrl);
                    Log.d("TTS Engine", "Download tokens file");

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    tokensFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(tokensFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (tokensFile.exists()) tokensDownloadSize = tokensFile.length();
                        activity.runOnUiThread(() -> {
                            binding.downloadSize.setText((tokensDownloadSize + onnxModelDownloadSize)/1024/1024 + " MB");
                        });
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!tokensFile.exists()) {
                        throw new IOException();
                    }

                    tokensFinished = true;
                    activity.runOnUiThread(() -> {
                        if (tokensFinished && onnxModelFinished && langFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                    });

                } catch (IOException i) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show());
                    tokensFile.delete();
                    Log.w("TTS Engine", activity.getResources().getString(R.string.error_download), i);
                }
            });
            thread.start();
        } else {
            tokensFinished = true;
            activity.runOnUiThread(() -> {
                if (tokensFinished && onnxModelFinished) binding.buttonStart.setVisibility(View.VISIBLE);
            });
        }
    }
}