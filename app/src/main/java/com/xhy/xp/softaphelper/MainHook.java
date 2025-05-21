package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final String P2P_HOST_IFACE_ADDRESS = "192.168.49.1/24";
    private static final String ETHERNET_HOST_IFACE_ADDRESS = "192.168.45.1/24";

    // staticBSSID Switch
    private static final boolean shouldStaticBSSID = false;

    private static HashMap<Integer, String> AddressMap = new HashMap<>();


    public static final int BAND_5GHZ = 1 << 1;
    public static final int CHANNEL_WIDTH_320MHZ = 11;
    // channel: 149,153,157,161,165
    // freq:    5745,5765,5785,5805,5825
    private static HashSet<Integer> AvailableChannelSet_LOW = new HashSet<>(Arrays.asList(36, 40, 44));
    private static HashSet<Integer> AvailableChannelSet_HIGH = new HashSet<>(Arrays.asList(149, 153, 157, 161, 165));
//    private static HashSet<Integer> AvailableChannelFreqSet = new HashSet<>(Arrays.asList(5745, 5765, 5785, 5805, 5825));

    static {
        AddressMap.put(TETHERING_WIFI, WIFI_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_USB, USB_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_BLUETOOTH, BT_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_WIFI_P2P, P2P_HOST_IFACE_ADDRESS);
        AddressMap.put(TETHERING_ETHERNET, ETHERNET_HOST_IFACE_ADDRESS);
    }

    private boolean isConflictPrefix(Class<?> klass, Object thiz, IpPrefix prefix) throws Exception {
        Field field_mPrivateAddressCoordinator = ReflectUtils.findField(klass, "mPrivateAddressCoordinator");
        // Android 15+, bypass
        if(field_mPrivateAddressCoordinator == null){
            XposedBridge.log("[" + TAG + "] [Warning]: [" + WIFI_HOST_IFACE_ADDR + "] field_mPrivateAddressCoordinator not found.");
            return false;
        }
        Object mPrivateAddressCoordinator = field_mPrivateAddressCoordinator.get(thiz);
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

        XposedBridge.log("[" + TAG + "] [Error]: [isConflictPrefix] method not found.");
        return false;
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
                            return WIFI_HOST_IFACE_ADDR;
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

                                final LinkAddress mLinkAddress = (LinkAddress) ctor_LinkAddress.newInstance(address);
                                final IpPrefix prefix = (IpPrefix) ctor_IpPrefix.newInstance(address);

                                if (address != null && StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                    if (isConflictPrefix(klass, param.thisObject, prefix)) {
                                        XposedBridge.log("[" + TAG + "] [Warning]: [" + WIFI_HOST_IFACE_ADDR + "] isConflictPrefix! do not replace.");
                                    } else {
                                        XposedBridge.log("[" + TAG + "] [Success Edit]:" + address);
                                        param.setResult(mLinkAddress);
                                    }
                                }
                            }
                        });
            } catch (Exception exception) {
//                XposedBridge.log("[" + TAG + "] exception in " + lpparam.packageName + ": " + exception);
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

                                    // staticBSSID
                                    if(shouldStaticBSSID) {
                                        param.args[1] = MacAddress.fromString("aa:bb:cc:dd:ee:ff");
                                    }

                                    SparseIntArray channels = (SparseIntArray) param.args[4];
                                    int channel5gIndex = channels.indexOfKey(BAND_5GHZ);

                                    Set<Integer> allowedAcsChannels5g = (Set<Integer>) param.args[21];
//                                    int maxChannelBandwidth = (int) param.args[23];
//                                    XposedBridge.log("["+TAG+"] orig channel5gIndex " + channel5gIndex);
//                                    XposedBridge.log("["+TAG+"] orig channels " + channels);
//                                    XposedBridge.log("["+TAG+"] orig allowedAcsChannels5g " + allowedAcsChannels5g);
//                                    XposedBridge.log("["+TAG+"] orig maxChannelBandwidth " + maxChannelBandwidth);

                                    // config has set 5G channel
                                    if (channel5gIndex >= 0) {
                                        int channel = channels.get(BAND_5GHZ);

                                        // 5GHz + allowedAcsChannels5g.size == 0
                                        if (channel == 0 && allowedAcsChannels5g.size() == 0) {
                                            // 5G ACS channels
                                            param.args[21] = AvailableChannelSet_HIGH;
                                            // max bandwidth
                                            param.args[23] = CHANNEL_WIDTH_320MHZ;
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