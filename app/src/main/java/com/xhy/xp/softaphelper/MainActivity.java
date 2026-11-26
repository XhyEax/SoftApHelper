package com.xhy.xp.softaphelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final Pattern MAC_PATTERN =
            Pattern.compile("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$");

    private static final int[] BANDWIDTH_VALUES = {20, 40, 80, 160, 320};
    private static final String[] BANDWIDTH_LABELS = {"20 MHz", "40 MHz", "80 MHz", "160 MHz", "320 MHz"};
    private static final String[] IP_PRESETS_1 = {"192.168.1.1", "192.168.43.1"};
    private static final String[] IP_PRESETS_2 = {"172.20.10.1", "172.31.10.1"};

    private EditText bssidInput;
    private EditText ipInput;
    private TextView ipLabel;
    private Spinner ipSpinner1;
    private Spinner ipSpinner2;
    private RadioButton radioWifi;
    private RadioButton radioEthernet;
    private String currentWifiIp;
    private String currentEthernetIp;
    private Switch enableSwitch;
    private Switch randomMacSwitch;
    private Switch enable5gSwitch;
    private EditText channels5gInput;
    private Spinner bandwidth5gSpinner;
    private Switch blockIpv6Switch;
    private TextView statusView;
    private SharedPreferences prefs;
    private final ContentObserver configObserver =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateStatus();
                }
            };
    private boolean observerRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        setContentView(R.layout.activity_main);

        prefs = ConfigContract.getPrefs(this);
        bssidInput = findViewById(R.id.bssid_input);
        ipInput = findViewById(R.id.ip_input);
        ipLabel = findViewById(R.id.ip_label);
        ipSpinner1 = findViewById(R.id.ip_spinner_1);
        ipSpinner2 = findViewById(R.id.ip_spinner_2);
        radioWifi = findViewById(R.id.hotspot_wifi);
        radioEthernet = findViewById(R.id.hotspot_ethernet);
        enableSwitch = findViewById(R.id.enable_bssid);
        randomMacSwitch = findViewById(R.id.random_mac_each_time);
        enable5gSwitch = findViewById(R.id.enable_5g);
        channels5gInput = findViewById(R.id.channels_5g_input);
        bandwidth5gSpinner = findViewById(R.id.bandwidth_5g_spinner);
        blockIpv6Switch = findViewById(R.id.block_ipv6);
        statusView = findViewById(R.id.bssid_status);

        ArrayAdapter<String> bandwidthAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, BANDWIDTH_LABELS);
        bandwidthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bandwidth5gSpinner.setAdapter(bandwidthAdapter);

        bssidInput.setText(prefs.getString(ConfigContract.KEY_BSSID, ConfigContract.DEFAULT_BSSID));
        currentWifiIp = prefs.getString(ConfigContract.KEY_IP, ConfigContract.DEFAULT_IP);
        currentEthernetIp = prefs.getString(
                ConfigContract.KEY_ETHERNET_IP, ConfigContract.DEFAULT_ETHERNET_IP);
        ipInput.setText(currentWifiIp);
        setupIpSpinner(ipSpinner1, IP_PRESETS_1, currentWifiIp);
        setupIpSpinner(ipSpinner2, IP_PRESETS_2, currentWifiIp);
        ((RadioGroup) findViewById(R.id.hotspot_type_group))
                .setOnCheckedChangeListener((group, checkedId) -> applyTypeToUi());
        enableSwitch.setChecked(prefs.getBoolean(ConfigContract.KEY_ENABLED, true));
        randomMacSwitch.setChecked(enableSwitch.isChecked()
                && prefs.getBoolean(ConfigContract.KEY_RANDOM_MAC_EACH_TIME, false));
        randomMacSwitch.setEnabled(enableSwitch.isChecked());
        enableSwitch.setOnCheckedChangeListener((v, checked) -> {
            randomMacSwitch.setEnabled(checked);
            if (!checked) {
                randomMacSwitch.setChecked(false);
            }
        });
        enable5gSwitch.setChecked(prefs.getBoolean(ConfigContract.KEY_5G_ENABLED, true));
        channels5gInput.setText(prefs.getString(
                ConfigContract.KEY_5G_CHANNELS, ConfigContract.DEFAULT_5G_CHANNELS));
        bandwidth5gSpinner.setSelection(bandwidthIndex(prefs.getInt(
                ConfigContract.KEY_5G_BANDWIDTH, ConfigContract.DEFAULT_5G_BANDWIDTH)));
        blockIpv6Switch.setChecked(prefs.getBoolean(ConfigContract.KEY_BLOCK_IPV6, false));

        findViewById(R.id.random_bssid).setOnClickListener(
                v -> bssidInput.setText(randomBssid(bssidInput.getText().toString().trim())));
        findViewById(R.id.channels_preset_1).setOnClickListener(
                v -> channels5gInput.setText(R.string.lock5g_channels_preset_1));
        findViewById(R.id.channels_preset_2).setOnClickListener(
                v -> channels5gInput.setText(R.string.lock5g_channels_preset_2));
        findViewById(R.id.save_bssid).setOnClickListener(v -> saveConfig());
        findViewById(R.id.open_hotspot_settings).setOnClickListener(v -> openHotspotSettings());
        updateStatus();
    }

    private void openHotspotSettings() {
        try {
            startActivity(new Intent("com.android.settings.WIFI_TETHER_SETTINGS"));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent("android.settings.TETHER_SETTINGS"));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, R.string.no_settings_page, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            updateStatus();
            if (!observerRegistered) {
                getContentResolver().registerContentObserver(
                        ConfigContract.CONTENT_URI, false, configObserver);
                observerRegistered = true;
            }
        }
    }

    @Override
    protected void onPause() {
        if (observerRegistered) {
            getContentResolver().unregisterContentObserver(configObserver);
            observerRegistered = false;
        }
        super.onPause();
    }

    private void setupIpSpinner(Spinner spinner, String[] presets, String currentIp) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        selectSpinnerPreset(spinner, presets, currentIp);
        // ignore the initial selection callbacks; only apply after the user touches the spinner
        final boolean[] touched = {false};
        spinner.setOnTouchListener((v, event) -> {
            touched[0] = true;
            return false;
        });
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (touched[0]) {
                    ipInput.setText(presets[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void selectSpinnerPreset(Spinner spinner, String[] presets, String ip) {
        int selection = 0;
        for (int i = 0; i < presets.length; i++) {
            if (presets[i].equals(ip)) {
                selection = i;
                break;
            }
        }
        spinner.setSelection(selection, false);
    }

    private void applyTypeToUi() {
        String ip = radioEthernet.isChecked() ? currentEthernetIp : currentWifiIp;
        ipInput.setText(ip);
        selectSpinnerPreset(ipSpinner1, IP_PRESETS_1, ip);
        selectSpinnerPreset(ipSpinner2, IP_PRESETS_2, ip);
    }

    private void saveConfig() {
        String raw = bssidInput.getText().toString().trim();
        if (!MAC_PATTERN.matcher(raw).matches()) {
            Toast.makeText(this, R.string.invalid_bssid, Toast.LENGTH_SHORT).show();
            return;
        }
        String bssid = raw.toLowerCase(Locale.ROOT);
        if (!isUnicastMac(bssid)) {
            Toast.makeText(this, R.string.multicast_bssid, Toast.LENGTH_SHORT).show();
            return;
        }
        String ip = ipInput.getText().toString().trim();
        if (!isValidIp(ip)) {
            Toast.makeText(this, R.string.invalid_ip, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<Integer> channels = parse5gChannels(channels5gInput.getText().toString());
        if (channels == null) {
            Toast.makeText(this, R.string.invalid_5g_channels, Toast.LENGTH_SHORT).show();
            return;
        }
        int bandwidth = BANDWIDTH_VALUES[bandwidth5gSpinner.getSelectedItemPosition()];
        String channelsText = joinChannels(channels);
        prefs.edit()
                .putString(ConfigContract.KEY_BSSID, bssid)
                .putBoolean(ConfigContract.KEY_ENABLED, enableSwitch.isChecked())
                .putBoolean(ConfigContract.KEY_RANDOM_MAC_EACH_TIME,
                        randomMacSwitch.isChecked())
                .putString(radioEthernet.isChecked()
                        ? ConfigContract.KEY_ETHERNET_IP : ConfigContract.KEY_IP, ip)
                .putBoolean(ConfigContract.KEY_5G_ENABLED, enable5gSwitch.isChecked())
                .putString(ConfigContract.KEY_5G_CHANNELS, channelsText)
                .putInt(ConfigContract.KEY_5G_BANDWIDTH, bandwidth)
                .putBoolean(ConfigContract.KEY_BLOCK_IPV6, blockIpv6Switch.isChecked())
                .apply();
        if (radioEthernet.isChecked()) {
            currentEthernetIp = ip;
        } else {
            currentWifiIp = ip;
        }
        bssidInput.setText(bssid);
        channels5gInput.setText(channelsText);
        Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        String bssid = prefs.getString(ConfigContract.KEY_BSSID, ConfigContract.DEFAULT_BSSID);
        String ip = prefs.getString(ConfigContract.KEY_IP, ConfigContract.DEFAULT_IP);
        String ethernetIp = prefs.getString(
                ConfigContract.KEY_ETHERNET_IP, ConfigContract.DEFAULT_ETHERNET_IP);
        boolean enabled = prefs.getBoolean(ConfigContract.KEY_ENABLED, true);
        boolean randomMac = enabled
                && prefs.getBoolean(ConfigContract.KEY_RANDOM_MAC_EACH_TIME, false);
        boolean lock5g = prefs.getBoolean(ConfigContract.KEY_5G_ENABLED, true);
        String channels = prefs.getString(
                ConfigContract.KEY_5G_CHANNELS, ConfigContract.DEFAULT_5G_CHANNELS);
        int bandwidth = prefs.getInt(
                ConfigContract.KEY_5G_BANDWIDTH, ConfigContract.DEFAULT_5G_BANDWIDTH);
        boolean blockIpv6 = prefs.getBoolean(ConfigContract.KEY_BLOCK_IPV6, false);

        String bssidState = !enabled ? getString(R.string.status_disabled)
                : randomMac ? getString(R.string.status_enabled_random)
                : getString(R.string.status_enabled, bssid);
        String ipState = getString(R.string.status_ips, ip, ethernetIp);
        String lock5gState = lock5g
                ? getString(R.string.status_5g_enabled, channels, bandwidth)
                : getString(R.string.status_5g_disabled);
        String ipv6State = blockIpv6
                ? getString(R.string.status_ipv6_blocked)
                : getString(R.string.status_ipv6_allowed);
        String statusText = bssidState + "\n" + ipState + "\n" + lock5gState + "\n" + ipv6State;
        String effectiveIp = prefs.getString(ConfigContract.KEY_EFFECTIVE_IP, "");
        ipLabel.setText(TextUtils.isEmpty(effectiveIp)
                ? getString(R.string.ip_label)
                : getString(R.string.ip_label_effective, effectiveIp));
        statusView.setText(getString(R.string.status_format, statusText));
    }

    private static boolean isUnicastMac(String mac) {
        // multicast/broadcast bit or all-zero address are not usable as BSSID
        return !isMulticastOrBroadcast(mac);
    }

    private static boolean isMulticastOrBroadcast(String mac) {
        if ("ff:ff:ff:ff:ff:ff".equalsIgnoreCase(mac)) {
            return true;
        }
        int first = Integer.parseInt(mac.substring(0, 2), 16);
        return (first & 0x01) != 0 || first == 0;
    }

    private static boolean isValidIp(String ip) {
        int[] octets = parseIpv4(ip);
        if (octets == null) {
            return false;
        }
        // /24 network and broadcast addresses cannot be used as host address
        if (octets[3] == 0 || octets[3] == 255) {
            return false;
        }
        // RFC1918 private ranges only
        return octets[0] == 10
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }

    private static int[] parseIpv4(String ip) {
        if (TextUtils.isEmpty(ip)) {
            return null;
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            for (int j = 0; j < part.length(); j++) {
                if (!Character.isDigit(part.charAt(j))) {
                    return null;
                }
            }
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }

    private static Set<Integer> parse5gChannels(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return null;
        }
        TreeSet<Integer> channels = new TreeSet<>();
        for (String token : raw.trim().split("[,，\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.length() > 3) {
                return null;
            }
            for (int i = 0; i < token.length(); i++) {
                if (!Character.isDigit(token.charAt(i))) {
                    return null;
                }
            }
            int channel = Integer.parseInt(token);
            if (!is5gChannel(channel)) {
                return null;
            }
            channels.add(channel);
        }
        return channels.isEmpty() ? null : channels;
    }

    private static boolean is5gChannel(int channel) {
        if (channel >= 36 && channel <= 64) {
            return (channel - 36) % 4 == 0;
        }
        if (channel >= 100 && channel <= 144) {
            return (channel - 100) % 4 == 0;
        }
        if (channel >= 149 && channel <= 177) {
            return (channel - 149) % 4 == 0;
        }
        return false;
    }

    private static int bandwidthIndex(int mhz) {
        for (int i = 0; i < BANDWIDTH_VALUES.length; i++) {
            if (BANDWIDTH_VALUES[i] == mhz) {
                return i;
            }
        }
        return BANDWIDTH_VALUES.length - 1;
    }

    private static String joinChannels(Set<Integer> channels) {
        StringBuilder builder = new StringBuilder();
        for (Integer channel : channels) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(channel);
        }
        return builder.toString();
    }

    private static String randomBssid(String current) {
        Random random = new Random();
        String bssid;
        do {
            byte[] mac = new byte[6];
            random.nextBytes(mac);
            // unicast + locally administered
            mac[0] = (byte) ((mac[0] & 0xFC) | 0x02);
            bssid = String.format(Locale.ROOT, "%02x:%02x:%02x:%02x:%02x:%02x",
                    mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
        } while (isMulticastOrBroadcast(bssid) || bssid.equalsIgnoreCase(current));
        return bssid;
    }
}