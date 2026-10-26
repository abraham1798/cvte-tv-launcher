package com.cleanyourtv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Apple TV style home screen: clock and pills on top, a dock band at the bottom,
 * the remaining apps in a grid below it. Focus is driven by hand so D-pad moves
 * are deterministic: Up/Down change exactly one row.
 */
public class MainActivity extends Activity {
    private static final int TOP = -1;

    private final ArrayList<App> dockApps = new ArrayList<>();
    private final ArrayList<App> gridApps = new ArrayList<>();
    private final ArrayList<ArrayList<Views.Tile>> rows = new ArrayList<>();
    private final ArrayList<View> tileViews = new ArrayList<>();
    private final Views.Pill[] pills = new Views.Pill[3];

    private FrameLayout content;
    private Views.Glass glass;
    private Views.Glow glow;
    private StarField stars;
    private View band;
    private TextView greeting, clock, date, dockTitle;
    private InputsMenu menu;
    private SharedPreferences prefs;

    private int columns, dockColumns;
    private int row, col, dockCol, topIndex;
    private View focused;
    private boolean resumed;
    private float screenW, screenH, margin, gap, tileW, tileH, dockTileW, dockTileH, labelH;
    private float bandTop, bandBottom, dockY, gridTop, pitch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Some TV builds default windows to 16-bit colour, which bands gradients.
        getWindow().setFormat(android.graphics.PixelFormat.RGBA_8888);
        Ui.init(this);
        CvteSources.connect(this);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        columns = Math.max(3, Math.min(8, getResources().getInteger(R.integer.columns)));
        dockColumns = Math.max(2, Math.min(8, getResources().getInteger(R.integer.dock_columns)));
        // Preview another dock size without rebuilding:
        //   adb shell am start -S -n com.cleanyourtv.launcher/.MainActivity --ei dock_columns 5
        int preview = getIntent().getIntExtra("dock_columns", 0);
        if (preview >= 2 && preview <= 8) dockColumns = preview;
        metrics();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        addWallpaper(root);
        content = new FrameLayout(this);
        content.setClipChildren(false);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) screenH));
        buildStatic();
        menu = new InputsMenu(this);
        menu.setListener(this::onInputPicked);
        root.addView(menu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        loadApps();
        buildTiles();
        row = prefs.getInt("row", 0);
        col = prefs.getInt("col", 0);
        dockCol = prefs.getInt("dockCol", 0);
        topIndex = prefs.getInt("top", 0);
        String pkg = prefs.getString("pkg", null);
        if (pkg != null && row != TOP) findApp(pkg);
        clampFocus();

        IntentFilter time = new IntentFilter(Intent.ACTION_TIME_TICK);
        time.addAction(Intent.ACTION_TIME_CHANGED);
        time.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, time);
        IntentFilter net = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        net.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(netReceiver, net);
        IntentFilter pk = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        pk.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pk.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pk.addDataScheme("package");
        registerReceiver(packageReceiver, pk);

        updateClock();
        updateNetwork();
        applyFocus(false);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(timeReceiver);
        unregisterReceiver(netReceiver);
        unregisterReceiver(packageReceiver);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (stars != null) stars.setRunning(row <= 0);
        updateClock();
        updateNetwork();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (stars != null) stars.setRunning(false);
        SharedPreferences.Editor e = prefs.edit()
                .putInt("row", row).putInt("col", col).putInt("dockCol", dockCol).putInt("top", topIndex);
        e.putString("pkg", row == TOP || rows.isEmpty() ? null : rows.get(row).get(col).app.pkg);
        e.apply();
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: close the menu and go back to the dock.
        if (menu.isOpen()) menu.close();
        if (resumed && row != 0 && !rows.isEmpty()) {
            row = 0;
            col = dockCol;
            clampFocus();
            applyFocus(true);
        }
    }

    // ---- layout -------------------------------------------------------------

    private void metrics() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        margin = Ui.px(80);
        gap = Ui.px(26);
        tileW = (screenW - 2 * margin - (columns - 1) * gap) / columns;
        tileH = tileW * 9f / 16f;
        dockTileW = (screenW - 2 * margin - (dockColumns - 1) * gap) / dockColumns;
        dockTileH = dockTileW * 9f / 16f;
        labelH = Ui.px(36);
        bandBottom = screenH - Ui.px(64);
        bandTop = bandBottom - (Ui.px(22) + dockTileH + labelH);
        dockY = bandTop + Ui.px(22);
        gridTop = bandBottom + Ui.px(40);
        pitch = tileH + labelH + gap;
    }

    private void addWallpaper(FrameLayout root) {
        // Optional: app/src/main/assets/wallpaper.(jpg|png|webp), shown behind everything.
        String file = null;
        for (String f : new String[] {"wallpaper.jpg", "wallpaper.png", "wallpaper.webp"}) {
            try (InputStream in = getAssets().open(f)) {
                file = f;
                break;
            } catch (IOException ignored) {
                // try the next name
            }
        }
        if (file == null) return; // no wallpaper: pitch black
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getAssets().open(file)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (o.outWidth / (sample * 2) >= screenW && o.outHeight / (sample * 2) >= screenH) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            // Full 8-bit colour: RGB_565 bands the dark sky gradients (~8 MB at 1080p).
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b;
            try (InputStream in = getAssets().open(file)) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(b);
            root.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // No scrim view: the darkening for legible text is baked into the image
            // (tools/Bake.java), which saves a full-screen blend on every frame.
            if (getResources().getBoolean(R.bool.star_animation)) {
                stars = new StarField(this, getResources().getBoolean(R.bool.show_libra));
                stars.place(root);
            }
        } catch (IOException ignored) {
            // no wallpaper: pitch black
        }
    }

    private void buildStatic() {
        glow = new Views.Glow(this);
        glow.setAlpha(0f);
        float glowH = Ui.px(320);
        float titleY = bandTop - Ui.px(84);
        Ui.place(glow, 0, titleY + Ui.px(28) - glowH / 2, screenW, glowH);
        content.addView(glow);

        band = new View(this);
        // translucent, so a wallpaper shows through it like frosted glass
        band.setBackground(Ui.rounded(0x8C1C1E22, 0x1FFFFFFF, Ui.px(30)));
        content.addView(band);

        dockTitle = Ui.text(this, Ui.light, 46, 0xFFF2F2F7);
        dockTitle.setAlpha(0f);
        content.addView(dockTitle);
        layoutDock(0);

        greeting = Ui.text(this, Ui.light, 24, 0xFF98989D);
        Ui.place(greeting, margin, Ui.px(58), Ui.px(640), -1);
        content.addView(greeting);
        clock = Ui.text(this, Ui.thin, 112, 0xFFF5F5F7);
        Ui.place(clock, margin - Ui.px(6), Ui.px(88), Ui.px(640), -1);
        content.addView(clock);
        date = Ui.text(this, Ui.light, 24, 0xFFAEAEB2);
        Ui.place(date, margin, Ui.px(228), Ui.px(640), -1);
        content.addView(date);

        pills[0] = new Views.Pill(this, Views.Icon.INPUTS, getString(R.string.inputs));
        pills[1] = new Views.Pill(this, Views.Icon.WIFI, "Wi-Fi");
        pills[2] = new Views.Pill(this, Views.Icon.SETTINGS, getString(R.string.settings));
        for (Views.Pill p : pills) content.addView(p);
        layoutPills();

        glass = new Views.Glass(this);
        content.addView(glass);
    }

    private void layoutPills() {
        float x = screenW - margin, y = Ui.px(60), h = Ui.px(44);
        for (int i = pills.length - 1; i >= 0; i--) {
            Views.Pill p = pills[i];
            float w = p.measureWidth();
            x -= w;
            p.x = x;
            p.y = y;
            p.w = w;
            p.h = h;
            Ui.place(p, x, y, w, h);
            x -= Ui.px(12);
        }
    }

    private void buildTiles() {
        for (View v : tileViews) content.removeView(v);
        tileViews.clear();
        rows.clear();
        focused = null;
        float dockX = layoutDock(dockApps.size());
        if (!dockApps.isEmpty()) {
            ArrayList<Views.Tile> dock = new ArrayList<>();
            for (int i = 0; i < dockApps.size(); i++) {
                dock.add(addTile(dockApps.get(i), dockX + i * (dockTileW + gap), dockY, dockTileW, dockTileH, true));
            }
            rows.add(dock);
        }
        for (int i = 0; i < gridApps.size(); i++) {
            int r = i / columns, c = i % columns;
            if (c == 0) rows.add(new ArrayList<>());
            rows.get(rows.size() - 1).add(addTile(gridApps.get(i), margin + c * (tileW + gap), gridTop + r * pitch,
                    tileW, tileH, false));
        }
        int gridRows = (gridApps.size() + columns - 1) / columns;
        int contentH = Math.round(gridTop + gridRows * pitch + screenH);
        content.getLayoutParams().height = contentH;
        Ui.place(glass, 0, 0, screenW, contentH);
        glass.bringToFront();
        content.requestLayout();
    }

    /**
     * Fits the dock band around n tiles, left-aligned with the grid, and lines the
     * title and glow up with it. Returns the x of the first dock tile.
     */
    private float layoutDock(int n) {
        int count = Math.max(1, n);
        float tilesW = count * dockTileW + (count - 1) * gap;
        float x = margin;
        float pad = Ui.px(26);
        Ui.place(band, x - pad, bandTop, tilesW + 2 * pad, bandBottom - bandTop);
        Ui.place(dockTitle, x, bandTop - Ui.px(84), screenW - x - margin, -1);
        glow.setCenter(x + Ui.px(170), Ui.px(320) / 2, Ui.px(460));
        return x;
    }

    /** Dock tiles show their banner/logo; grid tiles a white line icon with the name inside. */
    private Views.Tile addTile(App app, float x, float y, float w, float h, boolean dock) {
        Views.Tile t = new Views.Tile(this, app, x, y, w, h, dock && app.isBanner);
        Ui.place(t, x, y, w, h);
        content.addView(t);
        tileViews.add(t);
        return t;
    }

    // ---- apps ---------------------------------------------------------------

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> all = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, all);
        collect(pm, Intent.CATEGORY_LAUNCHER, false, all);
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        for (String h : hidden) all.remove(h);

        dockApps.clear();
        gridApps.clear();
        for (String item : getResources().getStringArray(R.array.dock_apps)) {
            if (dockApps.size() >= dockColumns) break;
            if (item.startsWith("input:")) {
                // input:SOURCE|Label|banner
                String[] f = item.substring(6).split("\\|");
                Drawable banner = f.length > 2 ? assetBanner(f[2]) : null;
                if (banner == null) banner = getDrawable(R.drawable.ic_launcher);
                dockApps.add(App.input(f[0].trim(), f.length > 1 ? f[1] : f[0], banner));
                continue;
            }
            App a = all.remove(item);
            if (a != null) dockApps.add(a);
        }
        gridApps.addAll(all.values());
        final Collator coll = Collator.getInstance();
        Collections.sort(gridApps, (a, b) -> coll.compare(a.label, b.label));
        if (dockApps.isEmpty()) {
            while (!gridApps.isEmpty() && dockApps.size() < dockColumns) dockApps.add(gridApps.remove(0));
        }
    }

    /** assets/banners/<name>.png, or null. */
    private Drawable assetBanner(String name) {
        try (InputStream in = getAssets().open("banners/" + name + ".png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            return b != null ? new android.graphics.drawable.BitmapDrawable(getResources(), b) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void collect(PackageManager pm, String category, boolean leanback, Map<String, App> out) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (out.containsKey(pkg)) continue;
            Drawable banner = assetBanner(pkg); // a replacement banner wins over the app's own
            if (banner == null) banner = ri.activityInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            out.put(pkg, new App(pkg, String.valueOf(ri.loadLabel(pm)),
                    new ComponentName(pkg, ri.activityInfo.name), leanback, banner != null ? banner : icon, icon,
                    banner != null));
        }
    }

    private void reloadApps() {
        String pkg = row == TOP || rows.isEmpty() ? null : rows.get(row).get(col).app.pkg;
        loadApps();
        buildTiles();
        if (pkg != null) findApp(pkg);
        clampFocus();
        applyFocus(false);
    }

    private void findApp(String pkg) {
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                if (rows.get(r).get(c).app.pkg.equals(pkg)) {
                    row = r;
                    col = c;
                    return;
                }
            }
        }
    }

    // ---- focus --------------------------------------------------------------

    private void clampFocus() {
        topIndex = Math.max(0, Math.min(pills.length - 1, topIndex));
        if (rows.isEmpty()) {
            row = TOP;
            return;
        }
        dockCol = Math.max(0, Math.min(rows.get(0).size() - 1, dockCol));
        if (row == TOP) return;
        row = Math.max(0, Math.min(rows.size() - 1, row));
        col = Math.max(0, Math.min(rows.get(row).size() - 1, col));
    }

    private void applyFocus(boolean animate) {
        View target;
        RectF r = new RectF();
        float radius;
        if (row == TOP) {
            Views.Pill p = pills[topIndex];
            target = p;
            r.set(p.x, p.y, p.x + p.w, p.y + p.h);
            r.inset(-Ui.px(3), -Ui.px(3));
            radius = r.height() / 2;
        } else {
            Views.Tile t = rows.get(row).get(col);
            target = t;
            float cx = t.x + t.w / 2, cy = t.y + t.h / 2;
            float hw = t.w * Views.FOCUS_SCALE / 2 + Ui.px(5), hh = t.h * Views.FOCUS_SCALE / 2 + Ui.px(5);
            r.set(cx - hw, cy - hh, cx + hw, cy + hh);
            radius = Ui.px(15);
            if (row == 0) dockCol = col;
        }
        if (focused != target) {
            setFocusState(focused, false);
            setFocusState(target, true);
            focused = target;
        }
        glass.moveTo(r, radius, animate);
        // the sky only animates while the top of Home is on screen
        if (stars != null) stars.setRunning(resumed && row <= 0);

        float ty = row <= 0 ? 0 : -(gridTop + (row - 1) * pitch - Ui.px(170));
        content.animate().translationY(ty).setDuration(animate ? 420 : 0)
                .setInterpolator(new DecelerateInterpolator(2.2f)).start();

        if (row == 0) {
            App a = rows.get(0).get(col).app;
            if (!a.label.contentEquals(dockTitle.getText())) {
                dockTitle.setText(a.label);
                if (animate) dockTitle.setAlpha(0.35f);
            }
            dockTitle.animate().alpha(1f).setDuration(220).start();
            glow.animate().alpha(1f).setDuration(300).start();
            glow.setColor(a.color, animate);
        } else {
            dockTitle.animate().alpha(0f).setDuration(200).start();
            glow.animate().alpha(0f).setDuration(250).start();
        }
    }

    private static void setFocusState(View v, boolean f) {
        if (v instanceof Views.Tile) ((Views.Tile) v).setFocusedState(f);
        else if (v instanceof Views.Pill) ((Views.Pill) v).setFocusedState(f);
    }

    private void move(int dRow, int dCol) {
        if (row == TOP) {
            if (dCol != 0) {
                int n = Math.max(0, Math.min(pills.length - 1, topIndex + dCol));
                if (n == topIndex) return; // the ends of the top bar don't fall through
                topIndex = n;
            } else if (dRow > 0 && !rows.isEmpty()) {
                row = 0;
                col = dockCol;
            } else {
                return;
            }
        } else if (dCol != 0) {
            int n = Math.max(0, Math.min(rows.get(row).size() - 1, col + dCol));
            if (n == col) return;
            col = n;
        } else if (dRow < 0) {
            if (row == 0) {
                row = TOP;
            } else {
                col = nearest(row - 1, rows.get(row).get(col));
                row--;
            }
        } else {
            if (row + 1 >= rows.size()) return;
            col = nearest(row + 1, rows.get(row).get(col));
            row++;
        }
        applyFocus(true);
    }

    /** The tile in row r whose centre is closest to `from` horizontally (dock and grid widths differ). */
    private int nearest(int r, Views.Tile from) {
        float cx = from.x + from.w / 2;
        int best = 0;
        float bestD = Float.MAX_VALUE;
        ArrayList<Views.Tile> list = rows.get(r);
        for (int i = 0; i < list.size(); i++) {
            Views.Tile t = list.get(i);
            float d = Math.abs(t.x + t.w / 2 - cx);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (menu.isOpen() && menu.onKey(k)) return true;
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                move(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                move(0, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                move(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                move(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) activate();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (menu.isOpen()) {
            menu.close();
        } else if (row != 0 && !rows.isEmpty()) {
            row = 0;
            col = dockCol;
            clampFocus();
            applyFocus(true);
        }
        // Back on the dock does nothing: this is Home.
    }

    // ---- actions ------------------------------------------------------------

    private void activate() {
        if (row != TOP) {
            App a = rows.get(row).get(col).app;
            if (a.source != null) {
                // Input shortcut: switch the TV source, then show it in the TV player.
                CvteSources.switchToName(a.source);
                openLiveTv();
            } else {
                start(a.launchIntent());
            }
            return;
        }
        switch (topIndex) {
            case 0:
                openInputs();
                break;
            case 1:
                start(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            default:
                start(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void start(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    private void openInputs() {
        ArrayList<InputsMenu.Item> items = cvteInputs();
        if (items == null) items = tifInputs();
        Views.Pill p = pills[0];
        menu.show(items, p.x, p.y + p.h + Ui.px(12), screenW);
    }

    /** The TV's own source list (CVTE boards): HDMI first, then AV and the rest, TV last. */
    private ArrayList<InputsMenu.Item> cvteInputs() {
        java.util.List<CvteSources.Source> list = CvteSources.list();
        if (list == null || list.isEmpty()) return null;
        // The CVTE API reports every source as connected, so HDMI cable state comes from
        // the TV input framework (its HDMI inputs, ordered by hardware id, are HDMI 1..n).
        ArrayList<String> tifHdmi = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) tifHdmi.add(info.getId());
            }
        }
        Collections.sort(tifHdmi);
        int hdmiCount = 0;
        for (CvteSources.Source s : list) if (s.isHdmi() && !s.isTv()) hdmiCount++;
        boolean states = tim != null && hdmiCount == tifHdmi.size();

        ArrayList<InputsMenu.Item> hdmi = new ArrayList<>(), other = new ArrayList<>(), tv = new ArrayList<>();
        for (CvteSources.Source s : list) {
            String label = s.name.replaceFirst("^(HDMI|AV)\\s*(\\d)$", "$1 $2");
            if (s.isTv()) {
                tv.add(new InputsMenu.Item(label, null, true, true, s.id));
            } else if (s.isHdmi() && states) {
                boolean on = tim.getInputState(tifHdmi.get(hdmi.size())) != TvInputManager.INPUT_STATE_DISCONNECTED;
                hdmi.add(new InputsMenu.Item(label, null, on, false, s.id));
            } else {
                // no reliable cable state: show the source without a dot
                (s.isHdmi() ? hdmi : other).add(new InputsMenu.Item(label, null, true, true, s.id));
            }
        }
        hdmi.addAll(other);
        hdmi.addAll(tv);
        return hdmi;
    }

    /** Fallback: passthrough inputs from the TV input framework, plus Live TV. */
    private ArrayList<InputsMenu.Item> tifInputs() {
        ArrayList<InputsMenu.Item> items = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.getParentId() != null || info.isHidden(this)) continue;
                CharSequence l = info.loadCustomLabel(this);
                if (TextUtils.isEmpty(l)) l = info.loadLabel(this);
                boolean connected = tim.getInputState(info.getId()) != TvInputManager.INPUT_STATE_DISCONNECTED;
                items.add(new InputsMenu.Item(String.valueOf(l), info.getId(), connected, false));
            }
        }
        final Collator coll = Collator.getInstance();
        Collections.sort(items, (a, b) -> a.label.length() != b.label.length()
                ? a.label.length() - b.label.length() : coll.compare(a.label, b.label));
        items.add(new InputsMenu.Item(getString(R.string.live_tv), null, true, true));
        return items;
    }

    private void onInputPicked(InputsMenu.Item item) {
        if (item.sourceId >= 0) {
            // Switch the source the way the stock launcher does, then show it in the TV player.
            CvteSources.switchTo(item.sourceId);
            openLiveTv();
        } else if (item.liveTv) {
            openLiveTv();
        } else {
            start(new Intent(this, InputActivity.class)
                    .putExtra(InputActivity.EXTRA_INPUT, item.inputId)
                    .putExtra(InputActivity.EXTRA_LABEL, item.label));
        }
    }

    private void openLiveTv() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.TV_HOME")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ComponentName cn = ComponentName.unflattenFromString(getString(R.string.live_tv_component));
        if (cn != null) i.setComponent(cn);
        start(i);
    }

    // ---- clock and network ----------------------------------------------------

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        greeting.setText(h >= 5 && h < 12 ? R.string.good_morning
                : h >= 12 && h < 20 ? R.string.good_afternoon : R.string.good_evening);
        clock.setText(DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm", c));
        Locale loc = Locale.getDefault();
        String d = String.valueOf(DateFormat.format(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), c));
        date.setText(d.isEmpty() ? d : d.substring(0, 1).toUpperCase(loc) + d.substring(1));
    }

    private void updateNetwork() {
        String name = null;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm != null ? cm.getActiveNetworkInfo() : null;
        if (ni != null && ni.isConnected()) {
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) {
                name = "Ethernet";
            } else if (ni.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
                String ssid = wi != null ? wi.getSSID() : null;
                name = ssid != null && !ssid.contains("<unknown") ? ssid.replace("\"", "") : "Wi-Fi";
            } else {
                name = ni.getTypeName();
            }
        }
        String text = name != null ? name : getString(R.string.offline);
        if (text.contentEquals(pills[1].text.getText())) return;
        pills[1].setText(text);
        layoutPills();
        if (row == TOP) applyFocus(false);
    }

    private final BroadcastReceiver timeReceiver = new Receiver(this, Receiver.TIME);
    private final BroadcastReceiver netReceiver = new Receiver(this, Receiver.NET);
    private final BroadcastReceiver packageReceiver = new Receiver(this, Receiver.PACKAGES);

    // A static nested class, not anonymous ones: the d8 in build-tools 34 crashes on
    // the constructor metadata newer javac writes for anonymous classes.
    private static final class Receiver extends BroadcastReceiver {
        static final int TIME = 0, NET = 1, PACKAGES = 2;
        private final MainActivity a;
        private final int kind;

        Receiver(MainActivity a, int kind) {
            this.a = a;
            this.kind = kind;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            if (kind == TIME) {
                a.updateClock();
            } else if (kind == NET) {
                a.updateNetwork();
            } else {
                if (a.menu.isOpen()) a.menu.close();
                a.reloadApps();
            }
        }
    }
}
