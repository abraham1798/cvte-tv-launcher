package com.cleanyourtv.launcher;

import android.app.Activity;
import android.app.Application;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Source switching through the CVTE TV API (cn.cvte.tv.api, on the boot classpath of
 * CVTE boards). On these sets the HDMI inputs only show a picture when the source is
 * switched this way and the stock TV player is opened; tuning them through the TV
 * input framework from another app leaves the screen black. Reflection keeps the
 * launcher installable on sets without the library.
 */
final class CvteSources {
    private static final String TAG = "CvteSources";
    private static final String API = "com.cvte.tv.api.aidl.ITvApiManager";
    private static final String INPUTS = "com.cvte.tv.api.aidl.ITVApiSystemInputSourceAidl";

    static final class Source {
        final int id;
        final String name, category;
        final boolean connected;

        Source(int id, String name, String category, boolean connected) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.connected = connected;
        }

        boolean isHdmi() {
            return category.endsWith("HDMI");
        }

        boolean isTv() {
            return category.endsWith("_TV") || category.endsWith("ATV") || category.startsWith("INPUTSOURCE_D")
                    || category.endsWith("ATSC") || category.endsWith("ISDB");
        }
    }

    private static Object inputApi;
    private static boolean requested;

    private CvteSources() {}

    /** Starts connecting to the TV API service; must run on the main thread. */
    static void connect(Activity a) {
        if (requested) return;
        requested = true;
        try {
            Class<?> mgrC = Class.forName("com.cvte.tv.api.TvApiSDKManager");
            Object mgr = null;
            try {
                mgr = mgrC.getMethod("getInstance").invoke(null);
            } catch (Throwable ignored) {
                // not initialised yet
            }
            if (mgr == null) mgr = mgrC.getMethod("init", Application.class).invoke(null, a.getApplication());
            Class<?> lisC = Class.forName("com.cvte.tv.api.TvServiceConnectListener");
            Object lis = Proxy.newProxyInstance(lisC.getClassLoader(), new Class<?>[] {lisC}, new OnConnected());
            mgrC.getMethod("getTvApi", lisC).invoke(mgr, lis);
        } catch (Throwable t) {
            Log.i(TAG, "CVTE TV API not available: " + t);
        }
    }

    static boolean available() {
        return inputApi != null;
    }

    /** The TV's own source list, or null when the API isn't connected. */
    static List<Source> list() {
        if (inputApi == null) return null;
        try {
            List<?> raw = (List<?>) Class.forName(INPUTS).getMethod("eventSystemInputSourceGetList").invoke(inputApi);
            ArrayList<Source> out = new ArrayList<>();
            if (raw == null) return out;
            for (Object e : raw) {
                String status = String.valueOf(field(e, "status"));
                if (status.endsWith("HIDE")) continue;
                Object id = field(e, "id");
                if (!(id instanceof Integer)) continue;
                out.add(new Source((Integer) id, String.valueOf(field(e, "name")),
                        String.valueOf(field(e, "category")), status.endsWith("CONNECTED")));
            }
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "source list failed", t);
            return null;
        }
    }

    static boolean switchTo(int id) {
        if (inputApi == null) return false;
        try {
            Object ok = Class.forName(INPUTS).getMethod("eventSystemInputSourceSetInputSource", int.class)
                    .invoke(inputApi, id);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            Log.w(TAG, "switch failed", t);
            return false;
        }
    }

    /** Switches by the TV's own source name; "HDMI 1" matches "HDMI1" and "hdmi 1". */
    static boolean switchToName(String name) {
        List<Source> list = list();
        if (list == null) return false;
        String want = name.replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        for (Source s : list) {
            if (s.name.replace(" ", "").toUpperCase(java.util.Locale.ROOT).equals(want)) return switchTo(s.id);
        }
        Log.w(TAG, "no source named " + name);
        return false;
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    // A named class, not an anonymous one: see the note in InputActivity.
    private static final class OnConnected implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method m, Object[] args) {
            if (m.getDeclaringClass() == Object.class) {
                switch (m.getName()) {
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "equals":
                        return proxy == args[0];
                    default:
                        return "CvteSources.OnConnected";
                }
            }
            if ("OnConnected".equals(m.getName()) && args != null && args.length > 0 && args[0] != null) {
                try {
                    inputApi = Class.forName(API).getMethod("getTVApiSystemInputSource").invoke(args[0]);
                    Log.i(TAG, "connected: " + (inputApi != null));
                } catch (Throwable t) {
                    Log.w(TAG, "getTVApiSystemInputSource failed", t);
                }
            }
            return null;
        }
    }
}
