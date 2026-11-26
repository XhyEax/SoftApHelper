package com.xhy.xp.softaphelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class ConfigContract {

    public static final String AUTHORITY = "com.xhy.xp.softaphelper.settings";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/config");

    public static final String PREF_FILE = "settings";
    public static final String KEY_BSSID = "bssid";
    public static final String KEY_ENABLED = "bssid_enabled";
    public static final String KEY_RANDOM_MAC_EACH_TIME = "random_mac_each_time";
    public static final String KEY_IP = "ip";
    public static final String KEY_ETHERNET_IP = "ethernet_ip";
    public static final String KEY_5G_ENABLED = "lock_5g_enabled";
    public static final String KEY_5G_CHANNELS = "lock_5g_channels";
    public static final String KEY_5G_BANDWIDTH = "lock_5g_bandwidth";
    public static final String KEY_BLOCK_IPV6 = "block_ipv6_enabled";
    public static final String KEY_EFFECTIVE_IP = "effective_ip";
    public static final String DEFAULT_BSSID = "aa:bb:cc:dd:ee:ff";
    public static final String DEFAULT_IP = "192.168.1.1";
    public static final String DEFAULT_ETHERNET_IP = "192.168.45.1";
    public static final String DEFAULT_5G_CHANNELS = "149,153,157,161,165";
    public static final int DEFAULT_5G_BANDWIDTH = 320;

    public static final String COL_BSSID = "bssid";
    public static final String COL_ENABLED = "enabled";
    public static final String COL_RANDOM_MAC_EACH_TIME = "random_mac_each_time";
    public static final String COL_IP = "ip";
    public static final String COL_ETHERNET_IP = "ethernet_ip";
    public static final String COL_5G_ENABLED = "lock_5g_enabled";
    public static final String COL_5G_CHANNELS = "lock_5g_channels";
    public static final String COL_5G_BANDWIDTH = "lock_5g_bandwidth";
    public static final String COL_BLOCK_IPV6 = "block_ipv6_enabled";
    public static final String COL_EFFECTIVE_IP = "effective_ip";

    private ConfigContract() {
    }

    public static SharedPreferences getPrefs(Context context) {
        Context deviceContext = context.createDeviceProtectedStorageContext();
        SharedPreferences prefs = deviceContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (prefs.getAll().isEmpty()) {
            migrateFromCredentialStorage(context, prefs);
        }
        return prefs;
    }

    private static void migrateFromCredentialStorage(Context context, SharedPreferences devicePrefs) {
        try {
            SharedPreferences credentialPrefs = context.getSharedPreferences(
                    PREF_FILE, Context.MODE_PRIVATE);
            if (credentialPrefs.getAll().isEmpty()) {
                return;
            }
            SharedPreferences.Editor editor = devicePrefs.edit();
            if (credentialPrefs.contains(KEY_BSSID)) {
                editor.putString(KEY_BSSID, credentialPrefs.getString(KEY_BSSID, DEFAULT_BSSID));
            }
            if (credentialPrefs.contains(KEY_ENABLED)) {
                editor.putBoolean(KEY_ENABLED, credentialPrefs.getBoolean(KEY_ENABLED, true));
            }
            if (credentialPrefs.contains(KEY_IP)) {
                editor.putString(KEY_IP, credentialPrefs.getString(KEY_IP, DEFAULT_IP));
            }
            if (credentialPrefs.contains(KEY_5G_ENABLED)) {
                editor.putBoolean(KEY_5G_ENABLED, credentialPrefs.getBoolean(KEY_5G_ENABLED, true));
            }
            if (credentialPrefs.contains(KEY_5G_CHANNELS)) {
                editor.putString(KEY_5G_CHANNELS,
                        credentialPrefs.getString(KEY_5G_CHANNELS, DEFAULT_5G_CHANNELS));
            }
            if (credentialPrefs.contains(KEY_5G_BANDWIDTH)) {
                editor.putInt(KEY_5G_BANDWIDTH,
                        credentialPrefs.getInt(KEY_5G_BANDWIDTH, DEFAULT_5G_BANDWIDTH));
            }
            if (credentialPrefs.contains(KEY_BLOCK_IPV6)) {
                editor.putBoolean(KEY_BLOCK_IPV6, credentialPrefs.getBoolean(KEY_BLOCK_IPV6, false));
            }
            editor.commit();
        } catch (Throwable ignored) {
            // credential-encrypted storage is not readable before first unlock
        }
    }
}
