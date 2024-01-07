package com.xposed.injector;

import java.io.File;
import java.util.List;
import java.util.Locale;
import android.os.Bundle;
import java.util.ArrayList;
import android.content.Context;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.annotation.SuppressLint;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainClass implements IXposedHookLoadPackage {
    public static final boolean injectDex = false;
    public static final boolean injectLibrary = false;
    public static final boolean callDexEntryPoint = false;

    public static final String injectDexName = "";
    public static final String injectLibraryName = "";
    public static final String injectedDexEntryClassName = "";
    public static final String injectedDexEntryMethodName = "";

    public static final String gamePackageName = "";
    public static final String gameMainActivity = "";

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    public static void loadLibrary(Context context) {
        try {
            System.load(String.format(Locale.ENGLISH, "%s/%s", context.getCacheDir().getAbsolutePath(), injectLibraryName));
        } catch (Exception exception) {
            XposedBridge.log(exception.toString());
        }
    }

    public static void loadDex(Context context) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            if (classLoader == null)
                return;

            Class<?> clazz = classLoader.getClass();
            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null)
                return;

            Field getPathList = superClass.getDeclaredField("pathList");
            getPathList.setAccessible(true);
            Object pathList = getPathList.get(classLoader);
            if (pathList == null)
                return;

            Class<?> pathListClass = pathList.getClass();
            Field getDexElements = pathListClass.getDeclaredField("dexElements");
            if (!getDexElements.isAccessible())
                getDexElements.setAccessible(true);

            Object[] dexElements = (Object[])getDexElements.get(pathList);
            if (dexElements == null)
                return;

            ArrayList<File> arrayList = new ArrayList<>();
            arrayList.add(new File(context.getCacheDir().getAbsolutePath(), injectDexName));

            Method method = pathListClass.getDeclaredMethod("makePathElements", List.class, File.class, List.class);
            method.setAccessible(true);

            Object[] makePathElements = (Object[])method.invoke(getDexElements, arrayList, null, null);
            if (makePathElements == null)
                return;

            Class<?> dexElementsClass = dexElements.getClass();
            Class<?> componentType = dexElementsClass.getComponentType();
            if (componentType == null)
                return;

            Object[] instance = (Object[]) Array.newInstance(componentType, makePathElements.length + dexElements.length);
            System.arraycopy(makePathElements, 0, instance, 0, makePathElements.length);
            System.arraycopy(dexElements, 0, instance, makePathElements.length, dexElements.length);

            getDexElements.set(pathList, instance);
        } catch (Exception exception) {
            XposedBridge.log(exception.toString());
        }
    }

    public static void callDexEntry(Context context) {
        try {
            Class<?> clazz = Class.forName(injectedDexEntryClassName);
            Method method = clazz.getDeclaredMethod(injectedDexEntryMethodName, Context.class);
            if (!method.isAccessible())
                method.setAccessible(true);

            method.invoke(null, context);
        } catch (Exception exception) {
            XposedBridge.log(exception.toString());
        }
    }

    public static void onGameStarted(Context context) {
        if (injectLibrary)
            loadLibrary(context);

        if (injectDex)
            loadDex(context);

        if (callDexEntryPoint)
            callDexEntry(context);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!loadPackageParam.packageName.equals(gamePackageName))
            return;

        XposedHelpers.findAndHookMethod(gameMainActivity, loadPackageParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                if (thiz != null) {
                    onGameStarted((Context) thiz);
                }
            }
        });
    }
}
