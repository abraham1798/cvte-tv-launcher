package com.cleanyourtv.launcher;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** A launchable app on the home screen, or a shortcut to one of the TV's inputs. */
final class App {
    final String pkg;
    final String label;
    final ComponentName component;
    final boolean leanback;
    /** Banner if there is one, otherwise the icon (dock tiles). */
    final Drawable art;
    /** The app's icon (grid tiles draw it as a white silhouette when no line icon fits). */
    final Drawable icon;
    final boolean isBanner;
    final int color;
    /** For input shortcuts: the TV source to switch to (e.g. "HDMI 1"); null for apps. */
    final String source;

    App(String pkg, String label, ComponentName component, boolean leanback, Drawable art, Drawable icon,
            boolean isBanner) {
        this(pkg, label, component, leanback, art, icon, isBanner, null);
    }

    private App(String pkg, String label, ComponentName component, boolean leanback, Drawable art,
            Drawable icon, boolean isBanner, String source) {
        this.pkg = pkg;
        this.label = label;
        this.component = component;
        this.leanback = leanback;
        this.art = art;
        this.icon = icon;
        this.isBanner = isBanner;
        this.color = Ui.sampleColor(art);
        this.source = source;
    }

    static App input(String source, String label, Drawable banner) {
        return new App("input:" + source, label, null, false, banner, banner, true, source);
    }

    Intent launchIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }
}
