package tv.vela.launcher;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A bounded row of native app shortcuts shared by the HDMI Home in both editions. */
final class VelaStartApps extends LinearLayout {
    private static final int MAX_SHORTCUTS = 5;

    static List<LiteCatalog.App> read(Context context) {
        List<LiteCatalog.App> apps = LiteCatalog.apps(context);
        List<String> order = VelaLayout.apps(context);
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        apps.sort(Comparator.comparingInt(app -> rank.getOrDefault(app.key, Integer.MAX_VALUE)));
        return new ArrayList<>(apps.subList(0, Math.min(MAX_SHORTCUTS, apps.size())));
    }

    VelaStartApps(Context context, List<LiteCatalog.App> apps, Runnable showApps) {
        super(context);
        setOrientation(HORIZONTAL);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        addShortcut(L10n.text("All apps"), null, 212, showApps);
        for (LiteCatalog.App app : apps) addShortcut(app.title, app, 270, () -> {
            try {
                context.startActivity(app.intent());
            } catch (RuntimeException unavailable) {
                Toast.makeText(context, L10n.text("App unavailable"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addShortcut(String title, LiteCatalog.App app, int width, Runnable open) {
        TextView button = new TextView(getContext());
        button.setText(title);
        VelaReading.textSize(button,20);
        button.setTypeface(Ui.medium);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setSingleLine();
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(16, 0, 14, 0);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setDefaultFocusHighlightEnabled(false);
        if (app != null) {
            try {
                Drawable icon = getContext().getPackageManager().getActivityIcon(app.component);
                icon.setBounds(0, 0, 38, 38);
                button.setCompoundDrawablePadding(12);
                button.setCompoundDrawablesRelative(icon, null, null, null);
            } catch (android.content.pm.PackageManager.NameNotFoundException unavailable) {
                // A package may disappear while this row is being created.
            }
        }
        int accent = VelaReading.accent(getContext());
        button.setTextColor(VelaTheme.WHITE);
        button.setBackground(VelaTheme.row(false, accent));
        button.setOnFocusChangeListener((view, focused) -> {
            button.setTextColor(focused ? VelaTheme.INK : VelaTheme.WHITE);
            button.setBackground(VelaTheme.row(focused, accent));
        });
        button.setOnClickListener(view -> open.run());
        LayoutParams params = new LayoutParams(width, LayoutParams.MATCH_PARENT);
        params.rightMargin = 14;
        addView(button, params);
    }
}
