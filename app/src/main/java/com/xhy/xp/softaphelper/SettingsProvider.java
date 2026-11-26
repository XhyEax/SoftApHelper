package com.xhy.xp.softaphelper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class SettingsProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        SharedPreferences prefs = ConfigContract.getPrefs(context);
        String bssid = prefs.getString(ConfigContract.KEY_BSSID, ConfigContract.DEFAULT_BSSID);
        boolean enabled = prefs.getBoolean(ConfigContract.KEY_ENABLED, true);
        boolean randomMacEachTime = prefs.getBoolean(ConfigContract.KEY_RANDOM_MAC_EACH_TIME, false);
        String ip = prefs.getString(ConfigContract.KEY_IP, ConfigContract.DEFAULT_IP);
        String ethernetIp = prefs.getString(ConfigContract.KEY_ETHERNET_IP,
                ConfigContract.DEFAULT_ETHERNET_IP);
        boolean lock5gEnabled = prefs.getBoolean(ConfigContract.KEY_5G_ENABLED, true);
        String lock5gChannels = prefs.getString(
                ConfigContract.KEY_5G_CHANNELS, ConfigContract.DEFAULT_5G_CHANNELS);
        int lock5gBandwidth = prefs.getInt(
                ConfigContract.KEY_5G_BANDWIDTH, ConfigContract.DEFAULT_5G_BANDWIDTH);
        boolean blockIpv6Enabled = prefs.getBoolean(ConfigContract.KEY_BLOCK_IPV6, false);

        MatrixCursor cursor = new MatrixCursor(new String[]{
                ConfigContract.COL_BSSID,
                ConfigContract.COL_ENABLED,
                ConfigContract.COL_IP,
                ConfigContract.COL_ETHERNET_IP,
                ConfigContract.COL_5G_ENABLED,
                ConfigContract.COL_5G_CHANNELS,
                ConfigContract.COL_5G_BANDWIDTH,
                ConfigContract.COL_BLOCK_IPV6,
                ConfigContract.COL_RANDOM_MAC_EACH_TIME}, 1);
        cursor.addRow(new Object[]{
                bssid, enabled ? 1 : 0, ip, ethernetIp,
                lock5gEnabled ? 1 : 0, lock5gChannels, lock5gBandwidth,
                blockIpv6Enabled ? 1 : 0, randomMacEachTime ? 1 : 0});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (values == null) {
            return 0;
        }
        String effectiveIp = values.getAsString(ConfigContract.COL_EFFECTIVE_IP);
        if (effectiveIp == null) {
            return 0;
        }
        ConfigContract.getPrefs(getContext()).edit()
                .putString(ConfigContract.KEY_EFFECTIVE_IP, effectiveIp)
                .apply();
        getContext().getContentResolver().notifyChange(uri, null);
        return 1;
    }
}
