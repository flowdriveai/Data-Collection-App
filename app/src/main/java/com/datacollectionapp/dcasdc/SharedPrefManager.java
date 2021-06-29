package com.datacollectionapp.dcasdc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefManager {
    private static final String KEY_INIT = "init";
    private static volatile SharedPrefManager mInstance = null;
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public void setKeyInit(boolean b){
        editor.putBoolean(KEY_INIT, b);
        editor.apply();
    }

    public boolean getKeyInit(){
        return sharedPreferences.getBoolean(KEY_INIT,true);
    }

    @SuppressLint("CommitPrefEdits")
    SharedPrefManager(Context context) {
        String SHARED_PREF_NAME = "DCA-SDC";
        sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public static SharedPrefManager getInstance(Context context) {
        if (mInstance == null) {
            synchronized (SharedPrefManager.class) {
                if (mInstance == null) {
                    mInstance = new SharedPrefManager(context);
                }
            }
        }
        return mInstance;
    }
}
