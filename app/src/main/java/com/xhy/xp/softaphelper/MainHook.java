package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    public static final String TAG = "SoftApHelper";

    private static final String className_P = "com.android.server.connectivity.tethering.TetherInterfaceStateMachine";
    private static final String className_Q = "android.net.ip.IpServer";

    private static final String methodName_P_Q = "getRandomWifiIPv4Address";
    private static final String methodName_R = "requestIpv4Address";

    private static final String callerMethodName_Q = "configureIPv4";

    private static final String WIFI_HOST_IFACE_ADDR = "192.168.43.1";

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

    private static HashMap<Integer, String> AddressMap = new HashMap<>();


    public static final int WIFI_BAND_INDEX_24_GHZ = 0;
    public static final int WIFI_BAND_INDEX_5_GHZ = 1;
    public static final int WIFI_BAND_INDEX_5_GHZ_DFS_ONLY = 2;
    public static final int WIFI_BAND_INDEX_6_GHZ = 3;
    public static final int WIFI_BAND_INDEX_60_GHZ = 4;
    public static final int WIFI_BAND_COUNT = 5;

    /** no band specified; use channel list instead */
    public static final int WIFI_BAND_UNSPECIFIED = 0;
    /** 2.4 GHz band */
    public static final int WIFI_BAND_24_GHZ = 1 << WIFI_BAND_INDEX_24_GHZ;
    /** 5 GHz band excluding DFS channels */
    public static final int WIFI_BAND_5_GHZ = 1 << WIFI_BAND_INDEX_5_GHZ;
    /** DFS channels from 5 GHz band only */
    public static final int WIFI_BAND_5_GHZ_DFS_ONLY  = 1 << WIFI_BAND_INDEX_5_GHZ_DFS_ONLY;
    /** 6 GHz band */
    public static final int WIFI_BAND_6_GHZ = 1 << WIFI_BAND_INDEX_6_GHZ;
    /** 60 GHz band */
    public static final int WIFI_BAND_60_GHZ = 1 << WIFI_BAND_INDEX_60_GHZ;

    // channel: 149,153,157,161,165
    // freq:    5745,5765,5785,5805,5825
    private static HashSet<Integer> AvailableChannelSet = new HashSet<>(Arrays.asList(5745,5765,5785,5805,5825));

    static {
        AddressMap.put(TETHERING_WIFI, WIFI_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_USB, USB_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_BLUETOOTH, BT_HOST_IFACE_ADDRESS);
    }

    private boolean isConflictPrefix(Object mPrivateAddressCoordinator, IpPrefix prefix) throws Exception {
        Class<?> privateAddressCoordinator = mPrivateAddressCoordinator.getClass();
        // Android 12+
        Method m_getConflictPrefix = ReflectUtils.findMethod(privateAddressCoordinator, "getConflictPrefix");
        if (m_getConflictPrefix != null) {
            return m_getConflictPrefix.invoke(mPrivateAddressCoordinator, prefix) != null;
        }

        // Android 11
        Method m_isDownstreamPrefixInUse = ReflectUtils.findMethod(privateAddressCoordinator, "isDownstreamPrefixInUse");
        Method m_isConflictWithUpstream = ReflectUtils.findMethod(privateAddressCoordinator, "isConflictWithUpstream");
        if (m_isDownstreamPrefixInUse != null && m_isConflictWithUpstream != null) {
            return (boolean) m_isDownstreamPrefixInUse.invoke(mPrivateAddressCoordinator, prefix) ||
                    (boolean) m_isConflictWithUpstream.invoke(mPrivateAddressCoordinator, prefix);
        }

        Log.e(TAG, "[Error]: [isConflictPrefix] method not found.");
        return false;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        ClassLoader classLoader = lpparam.classLoader;
//        Log.e(TAG, "[handleLoadPackage] packageName: " + lpparam.packageName);

        // 固定热点ip
        final String className = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? className_P :
                className_Q;
        final String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? methodName_R :
                methodName_P_Q;

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // 安卓框架
            findAndHookMethod(className, classLoader, methodName,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return WIFI_HOST_IFACE_ADDR;
                        }
                    });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Constructor<?> ctor_LinkAddress = LinkAddress.class.getDeclaredConstructor(String.class);
                Constructor<?> ctor_IpPrefix = IpPrefix.class.getDeclaredConstructor(String.class);

                Class<?> klass = classLoader.loadClass(className);
                Method method = ReflectUtils.findMethod(klass, methodName);
                if (method == null) {
                    Log.e(TAG, "[Error]: [" + methodName + "] not found in " + klass.getName());
                    return;
                }

                XposedBridge.hookMethod(method,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);
                                int mInterfaceType = ReflectUtils.findField(klass, "mInterfaceType").getInt(param.thisObject);

                                String address = AddressMap.get(mInterfaceType);

                                final LinkAddress mLinkAddress = (LinkAddress) ctor_LinkAddress.newInstance(address);
                                final IpPrefix prefix = (IpPrefix) ctor_IpPrefix.newInstance(address);

                                Object mPrivateAddressCoordinator = ReflectUtils.findField(klass, "mPrivateAddressCoordinator").get(param.thisObject);

                                if (address != null && StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                    if (isConflictPrefix(mPrivateAddressCoordinator, prefix)) {
                                        Log.w(TAG, "[Warning]: [" + WIFI_HOST_IFACE_ADDR + "] isConflictPrefix! do not replace.");
                                    } else {
                                        param.setResult(mLinkAddress);
                                    }
                                }
                            }
                        });
            } catch (ClassNotFoundException exception) {
                Log.e(TAG, "ClassNotFoundException in " + lpparam.packageName);
            }
        }

    }


}