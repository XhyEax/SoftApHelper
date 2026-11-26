package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.MacAddress;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    public static final String TAG = "SoftApHelper";

    private static final String className_P = "com.android.server.connectivity.tethering.TetherInterfaceStateMachine";
    private static final String className_Q = "android.net.ip.IpServer";

    private static final String methodName_P_Q = "getRandomWifiIPv4Address";
    private static final String methodName_R = "requestIpv4Address";

    private static final String callerMethodName_Q = "configureIPv4";

    private static final String WIFI_HOST_IFACE_ADDR = "192.168.1.1";

    // TetheringType
    public static final int TETHERING_INVALID = -1;
    public static final int TETHERING_WIFI = 0;
    public static final int TETHERING_USB = 1;
    public static final int TETHERING_BLUETOOTH = 2;
    public static final int TETHERING_WIFI_P2P = 3;
    public static final int TETHERING_NCM = 4;
    public static final int TETHERING_ETHERNET = 5;
    public static final int TETHERING_WIGIG = 6;

    private static final String WIFI_HOST_IFACE_ADDRESS = WIFI_HOST_IFACE_ADDR + "/24";
    private static final String USB_HOST_IFACE_ADDRESS = "192.168.42.1/24";
    private static final String BT_HOST_IFACE_ADDRESS = "192.168.44.1/24";
    private static final String P2P_HOST_IFACE_ADDRESS = "192.168.49.1/24";
    private static final String ETHERNET_HOST_IFACE_ADDRESS = "192.168.45.1/24";

    private static HashMap<Integer, String> AddressMap = new HashMap<>();


    public static final int BAND_5GHZ = 1 << 1;

    // SoftApInfo channel width encoding
    private static final int WIDTH_20MHZ = 2;
    private static final int WIDTH_40MHZ = 3;
    private static final int WIDTH_80MHZ = 4;
    private static final int WIDTH_160MHZ = 6;
    private static final int WIDTH_320MHZ = 11;

    static {
        AddressMap.put(TETHERING_WIFI, WIFI_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_USB, USB_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_BLUETOOTH, BT_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_WIFI_P2P, P2P_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_ETHERNET, ETHERNET_HOST_IFACE_ADDRESS);
    }

    private static volatile Context sContext;
    private static boolean sIpv6BlockHookInstalled;
    private static volatile String sLastEffectiveIp;

    private static Context getContext() {
        Context context = sContext;
        if (context != null) {
            return context;
        }
        try {
            context = (Context) XposedHelpers.callStaticMethod(
                    Class.forName("android.app.AppGlobals"), "getInitialApplication");
        } catch (Throwable ignored) {
        }
        if (context == null) {
            try {
                Object activityThread = XposedHelpers.callStaticMethod(
                        Class.forName("android.app.ActivityThread"), "currentActivityThread");
                if (activityThread != null) {
                    context = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
                }
            } catch (Throwable ignored) {
            }
        }
        if (context != null) {
            sContext = context;
        }
        return context;
    }

    private static final class Config {
        final String bssid;
        final boolean bssidEnabled;
        final boolean randomMacEachTime;
        final String ip;
        final String ethernetIp;
        final boolean lock5gEnabled;
        final String lock5gChannels;
        final int lock5gBandwidth;
        final boolean blockIpv6Enabled;

        Config(String bssid, boolean bssidEnabled, boolean randomMacEachTime, String ip,
               String ethernetIp, boolean lock5gEnabled, String lock5gChannels,
               int lock5gBandwidth, boolean blockIpv6Enabled) {
            this.bssid = bssid;
            this.bssidEnabled = bssidEnabled;
            this.randomMacEachTime = randomMacEachTime;
            this.ip = ip;
            this.ethernetIp = ethernetIp;
            this.lock5gEnabled = lock5gEnabled;
            this.lock5gChannels = lock5gChannels;
            this.lock5gBandwidth = lock5gBandwidth;
            this.blockIpv6Enabled = blockIpv6Enabled;
        }
    }

    private static boolean isValidIpv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int j = 0; j < part.length(); j++) {
                if (!Character.isDigit(part.charAt(j))) {
                    return false;
                }
            }
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) {
                return false;
            }
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

    private static int bandwidthToWidth(int mhz) {
        switch (mhz) {
            case 20:
                return WIDTH_20MHZ;
            case 40:
                return WIDTH_40MHZ;
            case 80:
                return WIDTH_80MHZ;
            case 160:
                return WIDTH_160MHZ;
            case 320:
                return WIDTH_320MHZ;
            default:
                return -1;
        }
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

    private static Set<Integer> parse5gChannels(String raw) {
        Set<Integer> channels = new HashSet<>();
        if (raw == null || raw.isEmpty()) {
            return channels;
        }
        for (String token : raw.trim().split("[,，\\s]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.length() > 3) {
                return new HashSet<>();
            }
            for (int i = 0; i < token.length(); i++) {
                if (!Character.isDigit(token.charAt(i))) {
                    return new HashSet<>();
                }
            }
            int channel = Integer.parseInt(token);
            if (!is5gChannel(channel)) {
                return new HashSet<>();
            }
            channels.add(channel);
        }
        return channels;
    }

    private Config readConfig() {
        Context context = getContext();
        if (context == null) {
            XposedBridge.log("[" + TAG + "] [Warning]: no context, config unavailable.");
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                ConfigContract.CONTENT_URI,
                new String[]{
                        ConfigContract.COL_BSSID,
                        ConfigContract.COL_ENABLED,
                        ConfigContract.COL_IP,
                        ConfigContract.COL_ETHERNET_IP,
                        ConfigContract.COL_5G_ENABLED,
                        ConfigContract.COL_5G_CHANNELS,
                        ConfigContract.COL_5G_BANDWIDTH,
                        ConfigContract.COL_BLOCK_IPV6,
                        ConfigContract.COL_RANDOM_MAC_EACH_TIME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return new Config(cursor.getString(0), cursor.getInt(1) != 0,
                        cursor.getInt(8) != 0, cursor.getString(2), cursor.getString(3),
                        cursor.getInt(4) != 0, cursor.getString(5), cursor.getInt(6),
                        cursor.getInt(7) != 0);
            }
        } catch (Throwable throwable) {
            XposedBridge.log("[" + TAG + "] [Warning]: read config failed: " + throwable);
        }
        return null;
    }

    private String getConflictReason(Class<?> klass, Object thiz, IpPrefix prefix) throws Exception {
        Field field_mPrivateAddressCoordinator = ReflectUtils.findField(klass, "mPrivateAddressCoordinator");
        if(field_mPrivateAddressCoordinator == null){
            // Android 15+: coordinator is no longer reachable from IpServer, scan local interfaces instead
            return findLocalPrefixConflict(prefix, findIfaceName(klass, thiz));
        }
        Object mPrivateAddressCoordinator = field_mPrivateAddressCoordinator.get(thiz);
        Class<?> privateAddressCoordinator = mPrivateAddressCoordinator.getClass();
        // Android 12+
        Method m_getConflictPrefix = ReflectUtils.findMethod(privateAddressCoordinator, "getConflictPrefix");
        if (m_getConflictPrefix != null) {
            Object conflict = m_getConflictPrefix.invoke(mPrivateAddressCoordinator, prefix);
            return conflict != null ? "conflicts with " + conflict : null;
        }

        // Android 11
        Method m_isDownstreamPrefixInUse = ReflectUtils.findMethod(privateAddressCoordinator, "isDownstreamPrefixInUse");
        Method m_isConflictWithUpstream = ReflectUtils.findMethod(privateAddressCoordinator, "isConflictWithUpstream");
        if (m_isDownstreamPrefixInUse != null && m_isConflictWithUpstream != null) {
            if ((boolean) m_isDownstreamPrefixInUse.invoke(mPrivateAddressCoordinator, prefix)) {
                return "conflicts with downstream prefix";
            }
            if ((boolean) m_isConflictWithUpstream.invoke(mPrivateAddressCoordinator, prefix)) {
                return "conflicts with upstream";
            }
            return null;
        }

        XposedBridge.log("[" + TAG + "] [Error]: [isConflictPrefix] method not found.");
        return null;
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
        } while (bssid.equalsIgnoreCase(current));
        return bssid;
    }

    private static String findIfaceName(Class<?> klass, Object thiz) {        try {
            Field field = ReflectUtils.findField(klass, "mIfaceName");
            if (field != null) {
                Object value = field.get(thiz);
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void reportEffectiveIp(final String ifaceName) {
        String ip = null;
        for (int attempt = 0; attempt < 10 && ip == null; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            try {
                NetworkInterface nic = NetworkInterface.getByName(ifaceName);
                if (nic == null) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : nic.getInterfaceAddresses()) {
                    InetAddress local = interfaceAddress.getAddress();
                    if (local instanceof Inet4Address && !local.isLoopbackAddress()
                            && !local.isLinkLocalAddress()) {
                        ip = local.getHostAddress();
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (ip == null || ip.equals(sLastEffectiveIp)) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(ConfigContract.COL_EFFECTIVE_IP, ip);
            context.getContentResolver().update(ConfigContract.CONTENT_URI, values, null, null);
            sLastEffectiveIp = ip;
            XposedBridge.log("[" + TAG + "] [Success] effective IP: " + ip + " (" + ifaceName + ")");
        } catch (Throwable throwable) {
            XposedBridge.log("[" + TAG + "] [Warning]: write effective IP failed: " + throwable);
        }
    }

    private String findLocalPrefixConflict(IpPrefix prefix, String excludeIface) {
        try {
            InetAddress target = prefix.getAddress();
            if (!(target instanceof Inet4Address)) {
                return null;
            }
            int targetValue = ipv4ToInt(target);
            int targetLength = prefix.getPrefixLength();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                String name = nic.getName();
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                if (excludeIface != null && excludeIface.equals(name)) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : nic.getInterfaceAddresses()) {
                    InetAddress local = interfaceAddress.getAddress();
                    if (!(local instanceof Inet4Address)) {
                        continue;
                    }
                    int localLength = interfaceAddress.getNetworkPrefixLength();
                    if (localLength < 0) {
                        continue;
                    }
                    int common = Math.min(targetLength, localLength);
                    int mask = common == 0 ? 0 : 0xFFFFFFFF << (32 - common);
                    if ((targetValue & mask) == (ipv4ToInt(local) & mask)) {
                        return "conflicts with " + name + " " + local + "/" + localLength;
                    }
                }
            }
        } catch (Throwable throwable) {
            XposedBridge.log("[" + TAG + "] [Warning] conflict scan failed: " + throwable);
        }
        return null;
    }

    private static int ipv4ToInt(InetAddress address) {
        byte[] bytes = address.getAddress();
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        ClassLoader classLoader = lpparam.classLoader;
//        XposedBridge.log("["+TAG+"] [handleLoadPackage] packageName: "
//                + lpparam.packageName + "-" + lpparam.processName + "-" + classLoader
//        );

        // 固定热点ip
        final String className = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? className_P :
                className_Q;
        final String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? methodName_R :
                methodName_P_Q;

        // 安卓9-10
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // 安卓框架
            findAndHookMethod(className, classLoader, methodName,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            Config config = readConfig();
                            return config != null && isValidIpv4(config.ip)
                                    ? config.ip : WIFI_HOST_IFACE_ADDR;
                        }
                    });
        }
        // 安卓11+
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Constructor<?> ctor_LinkAddress = LinkAddress.class.getDeclaredConstructor(String.class);
                Constructor<?> ctor_IpPrefix = IpPrefix.class.getDeclaredConstructor(String.class);

                Class<?> klass = classLoader.loadClass(className);
                Method method = ReflectUtils.findMethod(klass, methodName);
                if (method == null) {
                    XposedBridge.log("[" + TAG + "] [Error]: [" + methodName + "] not found in class " + klass.getName());
                    return;
                } else {
                    XposedBridge.log("[" + TAG + "] [Success]: [" + methodName + "] found in " + lpparam.processName);
                }

                XposedBridge.hookMethod(method,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);

//                                XposedBridge.log("[" + TAG + "] [Success Hook]: [" + methodName + "] found in " + StackUtils.getStackTraceString());

                                Field field_mInterfaceType = ReflectUtils.findField(klass, "mInterfaceType");
                                int mInterfaceType = 0;
                                if(field_mInterfaceType == null){
                                    // avoid exception
                                    XposedBridge.log("[" + TAG + "] [Warning]: [" + WIFI_HOST_IFACE_ADDR + "] field_mInterfaceType not found.");
                                }else{
                                    mInterfaceType = field_mInterfaceType.getInt(param.thisObject);
                                }

                                String address = AddressMap.get(mInterfaceType);
                                Config config = null;
                                if (mInterfaceType == TETHERING_WIFI
                                        || mInterfaceType == TETHERING_ETHERNET) {
                                    config = readConfig();
                                }
                                if (config != null && mInterfaceType == TETHERING_WIFI
                                        && isValidIpv4(config.ip)) {
                                    address = config.ip + "/24";
                                } else if (config != null && mInterfaceType == TETHERING_ETHERNET
                                        && isValidIpv4(config.ethernetIp)) {
                                    address = config.ethernetIp + "/24";
                                }

                                final LinkAddress mLinkAddress = (LinkAddress) ctor_LinkAddress.newInstance(address);
                                final IpPrefix prefix = (IpPrefix) ctor_IpPrefix.newInstance(address);

                                if (address != null && StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                    String conflict = getConflictReason(klass, param.thisObject, prefix);
                                    if (conflict != null) {
                                        XposedBridge.log("[" + TAG + "] [Warning] " + address
                                                + " " + conflict + ", do not replace.");
                                    } else {
                                        XposedBridge.log("[" + TAG + "] [Success Edit]:" + address);
                                        param.setResult(mLinkAddress);
                                    }
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                super.afterHookedMethod(param);
                                try {
                                    Field fieldType = ReflectUtils.findField(klass, "mInterfaceType");
                                    int type = fieldType != null
                                            ? fieldType.getInt(param.thisObject) : TETHERING_INVALID;
                                    if (type != TETHERING_WIFI) {
                                        return;
                                    }
                                    if (!StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                        return;
                                    }
                                    String ifaceName = findIfaceName(klass, param.thisObject);
                                    if (ifaceName == null) {
                                        return;
                                    }
                                    new Thread(() -> reportEffectiveIp(ifaceName)).start();
                                } catch (Throwable ignored) {
                                }
                            }
                        });
            } catch (Exception exception) {
//                XposedBridge.log("[" + TAG + "] exception in " + lpparam.packageName + ": " + exception);
            }
        }

        // 阻止热点下发IPv6 (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !sIpv6BlockHookInstalled) {
            try {
                Class<?> klassIpServer = classLoader.loadClass(className_Q);
                Method method = ReflectUtils.findMethod(klassIpServer, "updateUpstreamIPv6LinkProperties");
                if (method == null) {
                    XposedBridge.log("[" + TAG + "] [Warning]: [" + "updateUpstreamIPv6LinkProperties"
                            + "] not found in " + lpparam.processName);
                } else {
                    Field field_mInterfaceType = ReflectUtils.findField(klassIpServer, "mInterfaceType");
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            // keep the null-LinkProperties path so stale RA params get cleared
                            if (param.args[0] == null) {
                                return;
                            }
                            int type = field_mInterfaceType != null
                                    ? field_mInterfaceType.getInt(param.thisObject) : -1;
                            if (type != TETHERING_WIFI) {
                                return;
                            }
                            Config config = readConfig();
                            if (config == null || !config.blockIpv6Enabled) {
                                return;
                            }
                            // skip RA params, so no IPv6 prefix/default route is advertised downstream
                            param.setResult(null);
                            XposedBridge.log("[" + TAG + "] [Success] IPv6: blocked downstream RA for Wi-Fi tethering.");
                        }
                    });
                    XposedBridge.log("[" + TAG + "] [Success]: [" + "updateUpstreamIPv6LinkProperties"
                            + "] found in " + lpparam.processName);
                    sIpv6BlockHookInstalled = true;
                }
            } catch (ClassNotFoundException ignored) {
                // IpServer is not visible from this process
            } catch (Throwable throwable) {
                XposedBridge.log("[" + TAG + "] [Error]: IPv6 block hook failed: " + throwable);
            }
        }

        //固定5G热点信道 (Android 9-11)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q |
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            // TODO
        }
        // Android 12+
        else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) {
            // TODO
        }
        // Android 13+
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            try {
                for (Constructor<?> ctor : SoftApConfiguration.class.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                    super.beforeHookedMethod(param);

                                    // staticBSSID / random-each-start: read app config, takes effect on next hotspot start
                                    Config config = readConfig();
                                    if (config != null && config.bssidEnabled) {
                                        String bssid = config.randomMacEachTime
                                                ? randomBssid(config.bssid) : config.bssid;
                                        try {
                                            param.args[1] = MacAddress.fromString(bssid);
                                            XposedBridge.log("[" + TAG + "] [Success] "
                                                    + (config.randomMacEachTime
                                                    ? "random BSSID: " : "static BSSID: ")
                                                    + bssid);
                                        } catch (Throwable throwable) {
                                            XposedBridge.log("[" + TAG + "] [Error] invalid BSSID: "
                                                    + bssid + ", " + throwable);
                                        }
                                    }

                                    SparseIntArray channels = (SparseIntArray) param.args[4];
                                    int channel5gIndex = channels.indexOfKey(BAND_5GHZ);

                                    Set<Integer> allowedAcsChannels5g = (Set<Integer>) param.args[21];

                                    // config has set 5G channel
                                    if (channel5gIndex >= 0) {
                                        int channel = channels.get(BAND_5GHZ);

                                        // 5GHz + allowedAcsChannels5g.size == 0
                                        if (channel == 0 && allowedAcsChannels5g.size() == 0) {
                                            if (config == null || !config.lock5gEnabled) {
                                                XposedBridge.log("[" + TAG + "] [Info] 5G lock disabled, keep defaults.");
                                            } else {
                                                Set<Integer> lockChannels = parse5gChannels(config.lock5gChannels);
                                                int lockWidth = bandwidthToWidth(config.lock5gBandwidth);
                                                if (lockChannels.isEmpty() || lockWidth < 0) {
                                                    XposedBridge.log("[" + TAG + "] [Warning] invalid 5G config: channels="
                                                            + config.lock5gChannels + ", bandwidth="
                                                            + config.lock5gBandwidth + ", keep defaults.");
                                                } else {
                                                    // 5G ACS channels
                                                    param.args[21] = lockChannels;
                                                    // max bandwidth
                                                    param.args[23] = lockWidth;
                                                    XposedBridge.log("[" + TAG + "] [Success] 5G lock: channels="
                                                            + lockChannels + ", bandwidth="
                                                            + config.lock5gBandwidth + "MHz, width=" + lockWidth);
                                                }
                                            }
                                        }
                                    }
                                }

                            });
                }

            } catch (Exception exception) {
                XposedBridge.log("[" + TAG + "] exception in " + lpparam.packageName + ": " + exception);
            }
        }

        //隐藏热点类型 (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Class<?> klass = classLoader.loadClass("android.net.dhcp.DhcpServingParamsParcelExt");
                Method method = ReflectUtils.findMethod(klass, "setMetered");
                if (method == null) {
                    XposedBridge.log("[" + TAG + "] [Error]: [" + "setMetered" + "] not found in class " + klass.getName());
                }else {
                    XposedBridge.log("[" + TAG + "] [Success]: [" + "setMetered" + "] found in " + lpparam.processName);
                }

                XposedBridge.hookMethod(method,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);
                                param.args[0] = false;
                            }
                        });


                Class<?> klassAccessPoint = classLoader.loadClass("android.net.wifi.WifiConfiguration");
                Method method_isMetered = ReflectUtils.findMethod(klass, "isMetered");
                if (method_isMetered == null) {
                    XposedBridge.log("[" + TAG + "] [Error]: [" + "isMetered" + "] not found in class " + klassAccessPoint.getName());
                }else {
                    XposedBridge.log("[" + TAG + "] [Success]: [" + "isMetered" + "] found in " + lpparam.processName);
                }

                XposedBridge.hookMethod(method,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);
//                                param.setResult(false);
                                XposedBridge.log("[" + TAG + "] [Success]: [" + "isMetered" + "] stack:\n" + StackUtils.getStackTraceString());

                            }
                        });




            } catch (Exception exception) {
//                XposedBridge.log("[" + TAG + "] exception in " + lpparam.packageName + ": " + exception);
            }
        }

    }


}