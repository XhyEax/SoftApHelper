package com.xhy.xp.softaphelper;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.net.LinkAddress;
import android.os.Build;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

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
    private static final String WIFI_HOST_IFACE_ADDRESS = "192.168.43.1/24";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        ClassLoader classLoader = lpparam.classLoader;
//        XposedBridge.log("[handleLoadPackage] packageName: " + packageName);

        String className = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ? className_P :
                className_Q;
        final String methodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? methodName_R :
                methodName_P_Q;

        ArrayList<String> list = new ArrayList<>();
        list.add("com.android.networkstack.tethering.inprocess");
        list.add("com.android.networkstack.tethering");
        list.add("com.google.android.networkstack.tethering.inprocess");
        list.add("com.google.android.networkstack.tethering");
        list.add("android");
        if (!list.contains(packageName))
            return;
//        XposedBridge.log("[handleLoadPackage] packageName: " + packageName);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
//            if (!packageName.equals("android"))
//                return;

            findAndHookMethod(className, classLoader, methodName,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return WIFI_HOST_IFACE_ADDR;
                        }
                    });
        } else if (Build.MANUFACTURER.equalsIgnoreCase("motorola") &&
                Build.VERSION.SDK_INT == Build.VERSION_CODES.R){
//            if (!packageName.equals("com.google.android.networkstack.tethering"))
//                return;
            Constructor<?> ctor = LinkAddress.class.getDeclaredConstructor(String.class);
            final Object mLinkAddress = ctor.newInstance(WIFI_HOST_IFACE_ADDRESS);

            findAndHookMethod(className, classLoader, methodName, boolean.class,
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
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
//            if (!packageName.equals("com.android.networkstack.tethering.inprocess"))
//                return;

            Constructor<?> ctor = LinkAddress.class.getDeclaredConstructor(String.class);
            final Object mLinkAddress = ctor.newInstance(WIFI_HOST_IFACE_ADDRESS);

            findAndHookMethod(className, classLoader, methodName,
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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            if (!packageName.equals("com.android.networkstack.tethering.inprocess"))
//                return;

            Constructor<?> ctor = LinkAddress.class.getDeclaredConstructor(String.class);
            final Object mLinkAddress = ctor.newInstance(WIFI_HOST_IFACE_ADDRESS);

            findAndHookMethod(className, classLoader, methodName, boolean.class,
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
        }
    }


}