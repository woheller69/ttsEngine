package com.k2fsa.sherpa.onnx.tts.engine;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class LangDB extends SQLiteOpenHelper {

    // Database name and table columns
    private static final String DB_NAME = "Languages.db";
    private static final int DATABASE_VERSION = 2;
    public static final String TABLE_NAME = "Languages";
    private static final String COLUMN_ID = "ID";
    private static final String COLUMN_NAME = "ModelName";
    private static final String COLUMN_LANG = "Language";
    private static final String COLUMN_COUNTRY = "Country";
    private static final String COLUMN_SID = "SpeakerID";
    private static final String COLUMN_SPEED = "Speed";
    private static final String COLUMN_TYPE = "ModelType";
    private static final String COLUMN_VOLUME = "Volume";
    private static LangDB instance = null;
    private final Context mContext;

    public LangDB(Context context) {
        super(context, DB_NAME, null, DATABASE_VERSION);
        mContext = context.getApplicationContext();  //mContext = context creates memory leak
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the table for bird observations with all columns and their data types.
        String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS "+TABLE_NAME+" (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_NAME + " TEXT," +
                COLUMN_LANG + " TEXT," +
                COLUMN_COUNTRY + " TEXT," +
                COLUMN_SID + " INTEGER," +
                COLUMN_SPEED + " FLOAT," +
                COLUMN_TYPE + " TEXT," +
                COLUMN_VOLUME + " FLOAT);";
        db.execSQL(CREATE_TABLE);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        PreferenceHelper preferenceHelper = new PreferenceHelper(mContext);
        switch(oldVersion) {
            case 1:
                db.execSQL("ALTER TABLE "+TABLE_NAME+" ADD COLUMN "+ COLUMN_VOLUME +" REAL DEFAULT " + preferenceHelper.getVolume());
                // we want both updates, so no break statement here...
        }
    }
    
    public synchronized void addLanguage(String name, String lang, String country, int sid, float speed, float volume, String type) {
        // Insert a new row into the table with all columns and their values from parameters.
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NAME, name);
        cv.put(COLUMN_LANG, lang);
        cv.put(COLUMN_COUNTRY, country);
        cv.put(COLUMN_SID, sid);
        cv.put(COLUMN_SPEED, speed);
        cv.put(COLUMN_TYPE, type);
        cv.put(COLUMN_VOLUME, volume);
        
        db.insert(TABLE_NAME, null, cv); // Insert the row into the table with all columns and their values from parameters.
    }
    
    public synchronized void clearAllEntries() {
        SQLiteDatabase db = getWritableDatabase();
        String CLEAR_TABLE = "DELETE FROM "+ TABLE_NAME;
        
        db.execSQL(CLEAR_TABLE); // Delete all rows in the table, effectively clearing it out.
    }
    
    public synchronized List<Language> getAllInstalledLanguages() {
        SQLiteDatabase db = this.getReadableDatabase();
        String SELECT_ALL = "SELECT * FROM "+ TABLE_NAME;
        Cursor cursor = db.rawQuery(SELECT_ALL, null); // Execute the query to select all rows from the table and store them in a cursor object for further processing.

        List<Language> languages = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                Language language = new Language();
                language.setId(cursor.getInt(0));
                language.setName(cursor.getString(1));
                language.setLang(cursor.getString(2));
                language.setCountry(cursor.getString(3));
                language.setSid(cursor.getInt(4));
                language.setSpeed(cursor.getFloat(5));
                language.setType(cursor.getString(6));
                language.setVolume(cursor.getFloat(7));
                languages.add(language);

            } while (cursor.moveToNext());
        }
        cursor.close();
        return languages;
    }

    public synchronized void updateLang(String lang, int sid, float speed, float volume) {
        SQLiteDatabase database = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(COLUMN_SID, sid);
        values.put(COLUMN_SPEED, speed);
        values.put(COLUMN_VOLUME, volume);
        database.update(TABLE_NAME, values, COLUMN_LANG + " = ?", new String[]{lang});
        database.close();
    }

    public static LangDB getInstance(Context context) {
        if (instance == null && context != null) {
            instance = new LangDB(context.getApplicationContext());
        }
        return instance;
    }

    public void removeLang(String language) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, COLUMN_LANG + " = ?",
                new String[]{language});
        db.close();
    }
}
