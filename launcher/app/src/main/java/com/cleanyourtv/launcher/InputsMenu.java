package com.cleanyourtv.launcher;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Compact glass dropdown under the Inputs pill. */
final class InputsMenu extends FrameLayout {
    static final class Item {
        final String label, inputId;
        final boolean connected, liveTv;
        final int sourceId; // CVTE source id, or -1 for a TV input framework input

        Item(String label, String inputId, boolean connected, boolean liveTv) {
            this(label, inputId, connected, liveTv, -1);
        }

        Item(String label, String inputId, boolean connected, boolean liveTv, int sourceId) {
            this.label = label;
            this.inputId = inputId;
            this.connected = connected;
            this.liveTv = liveTv;
            this.sourceId = sourceId;
        }
    }

    interface Listener {
        void onPick(Item item);
    }

    private final LinearLayout panel;
    private final ArrayList<View> rows = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private Listener listener;
    private int sel;
    private boolean open;

    InputsMenu(Context c) {
        super(c);
        setVisibility(GONE);
        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.ipx(8);
        panel.setPadding(p, p, p, p);
        panel.setBackground(Ui.rounded(0xF21C1C1E, 0x33FFFFFF, Ui.px(22)));
        LayoutParams lp = new LayoutParams(Ui.ipx(300), LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        addView(panel, lp);
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isOpen() {
        return open;
    }

    void show(List<Item> list, float x, float y, float screenW) {
        items = list;
        panel.removeAllViews();
        rows.clear();
        sel = -1;
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            LinearLayout r = new LinearLayout(getContext());
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.ipx(16), Ui.ipx(12), Ui.ipx(16), Ui.ipx(12));
            View dot = new View(getContext());
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(it.liveTv ? 0x00000000 : it.connected ? 0xFF30D158 : 0xFF48484A);
            dot.setBackground(d);
            r.addView(dot, new LinearLayout.LayoutParams(Ui.ipx(8), Ui.ipx(8)));
            TextView t = Ui.text(getContext(), Ui.regular, 19,
                    it.connected || it.liveTv ? 0xFFF2F2F7 : 0xFF8E8E93);
            t.setText(it.label);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = Ui.ipx(12);
            r.addView(t, tl);
            panel.addView(r, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            rows.add(r);
            if (sel < 0 && it.connected && !it.liveTv) sel = i;
        }
        if (sel < 0) sel = 0;
        LayoutParams lp = (LayoutParams) panel.getLayoutParams();
        lp.leftMargin = Math.round(Math.min(x, screenW - Ui.px(80) - lp.width));
        lp.topMargin = Math.round(y);
        panel.setLayoutParams(lp);
        highlight();
        open = true;
        setVisibility(VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationY(-Ui.px(8));
        panel.animate().alpha(1f).translationY(0).setDuration(180).withEndAction(null).start();
    }

    void close() {
        open = false;
        panel.animate().alpha(0f).translationY(-Ui.px(8)).setDuration(140)
                .withEndAction(() -> {
                    if (!open) setVisibility(GONE);
                }).start();
    }

    /** Handles a key while open; returns false for keys the activity should handle (Back). */
    boolean onKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (sel > 0) sel--;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (sel < items.size() - 1) sel++;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                Item it = items.get(sel);
                close();
                if (listener != null) listener.onPick(it);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
        highlight();
        return true;
    }

    private void highlight() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setBackground(i == sel ? Ui.rounded(0x2EFFFFFF, 0, Ui.px(14)) : null);
        }
    }
}
