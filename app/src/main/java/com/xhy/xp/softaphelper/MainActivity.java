package com.xhy.xp.softaphelper;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ArrayList<String> pkgNameList = new ArrayList<>(
                Arrays.asList(
                        "com.android.networkstack.tethering.inprocess",
                        "com.android.networkstack.tethering",
                        "com.google.android.networkstack.tethering.inprocess",
                        "com.google.android.networkstack.tethering"
                ));

        StringBuilder sb = new StringBuilder("Installed App (if not found, select android):\n");
        for (String pkgName : pkgNameList) {
            if (isInstalled(pkgName)) {
                sb.append(pkgName);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2){
            sb.append("5G channel and bandwidth lock enabled!");
        }else{
            sb.append("Tips: 5G channel and bandwidth lock for Android 13+ only");
        }

        TextView textView = findViewById(R.id.sample_text);
        textView.setText(sb.toString());
    }

    public boolean isInstalled(String pkgName) {
        PackageManager packageManager = this.getApplicationContext().getPackageManager();
        try {
            packageManager.getApplicationInfo(pkgName, PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

}