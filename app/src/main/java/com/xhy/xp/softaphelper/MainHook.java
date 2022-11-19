package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;

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

    private static final String WIFI_HOST_IFACE_ADDR = "192.168.31.1";
    private static final String WIFI_HOST_IFACE_ADDRESS = WIFI_HOST_IFACE_ADDR + "/24";

    // TetheringType
    public static final int TETHERING_INVALID = -1;
    public static final int TETHERING_WIFI = 0;
    public static final int TETHERING_USB = 1;
    public static final int TETHERING_BLUETOOTH = 2;
    public static final int TETHERING_WIFI_P2P = 3;
    public static final int TETHERING_NCM = 4;
    public static final int TETHERING_ETHERNET = 5;
    public static final int TETHERING_WIGIG = 6;

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
//        XposedBridge.log("[handleLoadPackage] packageName: " + lpparam.packageName);

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
            Constructor<?> ctor_LinkAddress = LinkAddress.class.getDeclaredConstructor(String.class);
            final LinkAddress mLinkAddress = (LinkAddress) ctor_LinkAddress.newInstance(WIFI_HOST_IFACE_ADDRESS);
            Constructor<?> ctor_IpPrefix = IpPrefix.class.getDeclaredConstructor(String.class);
            final IpPrefix prefix = (IpPrefix) ctor_IpPrefix.newInstance(WIFI_HOST_IFACE_ADDRESS);

            try {
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
                                Object mPrivateAddressCoordinator = ReflectUtils.findField(klass, "mPrivateAddressCoordinator").get(param.thisObject);
                                if (mInterfaceType == TETHERING_WIFI
                                        && StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                    if (isConflictPrefix(mPrivateAddressCoordinator, prefix)) {
                                        Log.w(TAG, "[Warning]: [" + WIFI_HOST_IFACE_ADDR + "] isConflictPrefix! do not replace.");
                                    } else {
                                        param.setResult(mLinkAddress);
                                    }
                                }
                            }
                        });
            } catch (ClassNotFoundException exception) {
//                XposedBridge.log("ClassNotFoundException in " + lpparam.packageName);
            }
        }
    }


}