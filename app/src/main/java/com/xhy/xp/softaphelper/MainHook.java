package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.net.LinkAddress;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String className_P = "com.android.server.connectivity.tethering.TetherInterfaceStateMachine";
    private static final String className_Q = "android.net.ip.IpServer";

    private static final String methodName_P_Q = "getRandomWifiIPv4Address";
    private static final String methodName_R = "requestIpv4Address";

    private static final String callerMethodName_Q = "configureIPv4";

    private static final String WIFI_HOST_IFACE_ADDR = "192.168.43.1";

    public Method findMethod(Class<?> klass, String methodName) {
        for (Method m : klass.getDeclaredMethods()) {
            m.setAccessible(true);
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        ClassLoader classLoader = lpparam.classLoader;
//        XposedBridge.log("[handleLoadPackage] packageName: " + lpparam.packageName);

        final String className = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? className_P :
                className_Q;
        final String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? methodName_R :
                methodName_P_Q;
        final String WIFI_HOST_IFACE_ADDRESS = WIFI_HOST_IFACE_ADDR + "/24";

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
            Constructor<?> ctor = LinkAddress.class.getDeclaredConstructor(String.class);
            final Object mLinkAddress = ctor.newInstance(WIFI_HOST_IFACE_ADDRESS);
            try {
                Class<?> klass = classLoader.loadClass(className);
                Method method = findMethod(klass, methodName);
                if (method == null) {
                    XposedBridge.log("[Error]: [" + methodName + "] not found in " + klass.getName());
                    return;
                }

                XposedBridge.hookMethod(method,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                super.beforeHookedMethod(param);
//                            XposedBridge.log(StackUtils.getStackTraceString());
                                if (StackUtils.isCallingFrom(className, callerMethodName_Q)) {
                                    param.setResult(mLinkAddress);
                                }
                            }
                        });
            } catch (ClassNotFoundException exception) {
//                XposedBridge.log("ClassNotFoundException in " + lpparam.packageName);
            }
        }
    }


}