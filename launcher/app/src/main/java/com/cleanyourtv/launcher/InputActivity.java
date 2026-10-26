package com.cleanyourtv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Full-screen HDMI/AV player: tunes a passthrough input through the TV input framework. */
public class InputActivity extends Activity {
    static final String EXTRA_INPUT = "input_id";
    static final String EXTRA_LABEL = "label";

    private final Handler handler = new Handler();
    private TvView tv;
    private TextView chip;
    private String label;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        chip = Ui.text(this, Ui.regular, 20, 0xFFF2F2F7);
        chip.setBackground(Ui.rounded(0xCC1C1C1E, 0x33FFFFFF, Ui.px(20)));
        chip.setPadding(Ui.ipx(20), Ui.ipx(10), Ui.ipx(20), Ui.ipx(10));
        Ui.place(chip, Ui.px(64), Ui.px(48), -1, -1);
        root.addView(chip);
        setContentView(root);

        tv.setCallback(new Callback(this));
        tune(getIntent());
    }

    // Static nested classes instead of anonymous ones: the d8 in build-tools 34
    // crashes on the constructor metadata newer javac writes for anonymous classes.
    private static final class Callback extends TvView.TvInputCallback {
        private final InputActivity a;

        Callback(InputActivity a) {
            this.a = a;
        }

        @Override
        public void onConnectionFailed(String inputId) {
            a.fail();
        }

        @Override
        public void onDisconnected(String inputId) {
            a.fail();
        }

        @Override
        public void onVideoAvailable(String inputId) {
            a.showChip(a.label, 2500);
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            a.showChip(a.label + "  ·  " + a.getString(R.string.no_signal), 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        tune(intent);
    }

    private void tune(Intent i) {
        String id = i.getStringExtra(EXTRA_INPUT);
        if (id == null) {
            finish();
            return;
        }
        label = i.getStringExtra(EXTRA_LABEL);
        if (label == null) label = "";
        showChip(label, 2500);
        tv.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
    }

    private void showChip(String text, long hideAfterMs) {
        handler.removeCallbacksAndMessages(null);
        chip.setText(text);
        chip.animate().alpha(1f).setDuration(150).start();
        if (hideAfterMs > 0) {
            handler.postDelayed(() -> chip.animate().alpha(0f).setDuration(400).start(), hideAfterMs);
        }
    }

    private void fail() {
        Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Release the input as soon as we leave, so the TV app can take the tuner/HDMI back.
        handler.removeCallbacksAndMessages(null);
        tv.reset();
        finish();
    }
}
