package tv.vela.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Remote-friendly selection with a schematic preview. No bitmaps or background work. */
final class VelaHomePresets extends FrameLayout {
    interface Choice { void apply(String preset); }

    private final Preview preview;
    private final TextView description;
    private final View[] choices;
    private final int selected;

    VelaHomePresets(Context context, boolean lite, String current, boolean canRestore, Choice apply, Runnable back) {
        super(context);
        setBackground(VelaTheme.canvas());
        setId(View.generateViewId());
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        String[] keys = lite ? new String[]{VelaLayout.TELEVISION, VelaLayout.DEVICES}
            : new String[]{VelaLayout.LIBRARY, VelaLayout.TELEVISION, VelaLayout.DEVICES};
        choices = new View[keys.length];

        TextView title = text("Home layouts", 44, VelaTheme.WHITE);
        title.setTypeface(Ui.displaySemibold);
        place(title, 54, 40, 1250, 72);
        place(text("Choose a layout to preview it. Press OK to use it.", 24, VelaTheme.SECONDARY), 54, 124, 1650, 54);
        preview = new Preview(context);
        place(preview, 645, 216, 1086, 574);
        description = text("", 22, VelaTheme.SECONDARY);
        description.setMaxLines(2);
        place(description, 645, 808, 1086, 72);

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            choices[i] = action(label(key), () -> apply.apply(key), () -> show(key, lite));
            place(choices[i], 54, 216 + i * 104, 545, 82);
            if (i > 0) link(choices[i - 1], choices[i]);
        }
        View last = choices[choices.length - 1];
        if (canRestore) {
            View restore = action("Restore previous Home", () -> apply.apply("previous"), null);
            place(restore, 54, 660, 545, 76);
            link(last, restore);
            last = restore;
        }
        View cancel = action("Back", back, null);
        place(cancel, 54, 804, 545, 70);
        link(last, cancel);
        int initial = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) initial = i;
        selected = initial;
        show(keys[selected], lite);
    }

    void focusChoice() { choices[selected].requestFocus(); }

    static String label(String key) {
        switch (key) {
            case VelaLayout.TELEVISION: return "Television";
            case VelaLayout.DEVICES: return "HDMI and apps";
            default: return "Library";
        }
    }

    private void show(String key, boolean lite) {
        preview.key = key;
        preview.guide = lite;
        preview.invalidate();
        String detail;
        switch (key) {
            case VelaLayout.TELEVISION:
                detail = lite ? "Your antenna channels, favourites and programme guide."
                    : "On now, favourite channels, the previous channel and the full programme guide.";
                break;
            case VelaLayout.DEVICES: detail = "Connected HDMI devices and shortcuts to your installed apps."; break;
            default: detail = "Continue watching and recently added titles from Plex and Jellyfin.";
        }
        description.setText(L10n.text(detail));
        preview.setContentDescription(L10n.text(label(key)) + ". " + L10n.text(detail));
    }

    private TextView text(String key, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(key.isEmpty() ? "" : L10n.text(key));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * VelaReading.scale(getContext()));
        view.setTypeface(Ui.medium);
        view.setIncludeFontPadding(false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private View action(String key, Runnable click, Runnable focus) {
        TextView view = text(key, 26, VelaTheme.WHITE);
        int accent = VelaReading.accent(getContext());
        view.setId(View.generateViewId());
        view.setPadding(24, 0, 14, 0);
        view.setMaxLines(2);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setDefaultFocusHighlightEnabled(false);
        view.setBackground(VelaTheme.row(false, accent));
        view.setOnFocusChangeListener((v, focused) -> {
            view.setBackground(VelaTheme.row(focused, accent));
            view.setTextColor(focused ? VelaTheme.INK : VelaTheme.WHITE);
            if (focused && focus != null) focus.run();
        });
        view.setOnClickListener(v -> click.run());
        return view;
    }

    private void place(View child, int x, int y, int width, int height) {
        LayoutParams params = new LayoutParams(width, height);
        params.leftMargin = x;
        params.topMargin = y;
        addView(child, params);
    }

    private void link(View above, View below) {
        above.setNextFocusDownId(below.getId());
        below.setNextFocusUpId(above.getId());
    }

    private static final class Preview extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int accent;
        private String key = VelaLayout.LIBRARY;
        private boolean guide;

        Preview(Context context) { super(context); accent = VelaReading.accent(context); }

        @Override protected void onDraw(Canvas c) {
            c.save();
            c.scale(getWidth() / 1086f, getHeight() / 574f);
            box(c, 0, 0, 1086, 574, 28, 0xFF15171D);
            box(c, 24, 25, 64, 524, 24, 0xFF252830);
            for (int i = 0; i < 5; i++) box(c, 44, 62 + 52 * i, 24, 24, 7, i == 0 ? accent : 0xFF717781);
            for (int i = 0; i < 3; i++) box(c, 942 + i * 34, 38, 19, 7, 3, 0xFF6F7582);
            if (VelaLayout.TELEVISION.equals(key)) television(c);
            else if (VelaLayout.DEVICES.equals(key)) devices(c);
            else library(c);
            c.restore();
        }

        private void library(Canvas c) {
            box(c, 114, 29, 946, 272, 20, 0xFF272A35);
            box(c, 755, 59, 262, 202, 14, 0xFF39404E);
            label(c, "Library", 151, 115, 38);
            line(c, 151, 146, 248);
            box(c, 151, 205, 140, 36, 13, accent);
            label(c, "Continue watching", 117, 344, 24);
            tiles(c, 358, 5, 172, 89);
            label(c, "Recently added", 117, 485, 24);
            tiles(c, 501, 7, 110, 48);
        }

        private void television(Canvas c) {
            label(c, "Television", 117, 88, 38);
            if (guide) {
                label(c, "Programme guide", 117, 140, 24);
                for (int i = 0; i < 6; i++) {
                    int y = 161 + i * 60;
                    box(c, 117, y, 145, 47, 9, 0xFF353B49);
                    int x = 274;
                    int first = 140 + (i % 3) * 70;
                    box(c, x, y, first, 47, 9, i == 0 ? accent : 0xFF3C4353);
                    box(c, x + first + 9, y, 249, 47, 9, 0xFF272D39);
                    box(c, x + first + 267, y, 517 - first, 47, 9, 0xFF272D39);
                }
            } else {
                box(c, 117, 117, 943, 237, 20, 0xFF272A35);
                label(c, "On now", 147, 172, 25);
                line(c, 147, 216, 332);
                line(c, 147, 248, 197);
                box(c, 147, 282, 128, 35, 12, accent);
                box(c, 795, 158, 152, 152, 22, 0xFF3C4353);
                label(c, "Favorites", 117, 406, 24);
                tiles(c, 429, 5, 172, 104);
            }
        }

        private void devices(Canvas c) {
            label(c, "HDMI inputs", 117, 88, 38);
            box(c, 117, 116, 943, 220, 20, 0xFF272A35);
            label(c, "HDMI 1", 147, 182, 30);
            line(c, 147, 209, 161);
            box(c, 147, 252, 123, 36, 12, accent);
            box(c, 781, 153, 187, 137, 17, 0xFF3D4553);
            box(c, 808, 180, 133, 68, 9, 0xFF181D26);
            box(c, 846, 260, 57, 5, 2, accent);
            label(c, "Apps", 117, 397, 24);
            tiles(c, 421, 6, 131, 112);
        }

        private void label(Canvas c, String key, float x, float baseline, int size) {
            paint.setColor(Color.WHITE);
            paint.setTypeface(Ui.medium);
            paint.setTextSize(size);
            c.drawText(L10n.text(key), x, baseline, paint);
        }

        private void tiles(Canvas c, int y, int count, int width, int height) {
            for (int i = 0; i < count; i++) box(c, 117 + i * (width + 16), y, width, height, 13,
                i % 2 == 0 ? 0xFF3C4353 : 0xFF2C323E);
        }

        private void line(Canvas c, int x, int y, int w) { box(c, x, y, w, 8, 4, 0xFF626B7D); }

        private void box(Canvas c, int x, int y, int w, int h, int radius, int color) {
            paint.setColor(color);
            c.drawRoundRect(x, y, x + w, y + h, radius, radius, paint);
        }
    }
}
