package tv.vela.launcher;
import android.content.Context;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

final class Adb implements Closeable {
    interface Approval { void waiting(); }
    static final String STOCK = "com.vendor.launcher/.Home";
    static String home, dream, failOnce, powerLossOn, roleResponse, resolveResponse, homeActivities;
    static boolean rejectDream, factoryDominates;
    static final Set<String> present = new HashSet<>(), disabled = new HashSet<>();
    static final List<String> commands = new ArrayList<>();
    static void reset(String originalDream) {
        Config.DISABLE_APPS = false; Config.RECOVERY_HOME = ""; Config.ISOLATE_RECOVERY_HOME = false; factoryDominates = false;
        home = STOCK; dream = originalDream; failOnce = null; powerLossOn = null; rejectDream = false;
        roleResponse = null; resolveResponse = null; homeActivities = null;
        commands.clear(); present.clear(); disabled.clear();
        present.add("com.vendor.launcher"); present.add("com.vendor.dream");
        present.add(HomeMode.GOOGLE_SETUP); present.addAll(Arrays.asList(HomeMode.DEBLOAT));
    }
    static Adb connect(Context c, int timeout, Approval approval) throws IOException { return new Adb(); }
    String shell(String command) throws IOException {
        commands.add(command);
        if (command.equals(failOnce)) { failOnce = null; throw new IOException("simulated connection loss"); }
        if (command.startsWith("pm list packages")) {
            Set<String> selected = command.endsWith("-d") ? disabled : present;
            StringBuilder out = new StringBuilder();
            for (String pkg : selected) out.append("package:").append(pkg).append('\n');
            return out.toString();
        }
        if (command.startsWith("cmd role get-role-holders")) return roleResponse != null ? roleResponse : home.substring(0, home.indexOf('/'));
        if (command.startsWith("cmd package query-activities")) return homeActivities != null ? homeActivities : home;
        if (command.startsWith("cmd package resolve-activity")) return resolveResponse != null ? resolveResponse : factoryDominates && !disabled.contains("com.vendor.launcher") ? STOCK : home;
        if (command.equals("settings get secure screensaver_components")) return dream;
        if (command.equals("settings get secure screensaver_default_component")) return "com.vendor.dream/.Default";
        if (command.startsWith("cmd package set-home-activity ")) { home = command.substring(command.lastIndexOf(' ') + 1); resolveResponse = null; return "Success"; }
        if (command.startsWith("settings put secure screensaver_components ")) {
            String value = command.substring("settings put secure screensaver_components ".length());
            if (!rejectDream || !value.equals(HomeMode.DREAM)) {
                dream = value.startsWith("'") ? value.substring(1, value.length() - 1).replace("'\\''", "'") : value;
            }
            return "";
        }
        if (command.equals("settings delete secure screensaver_components")) { dream = "null"; return ""; }
        if (command.startsWith("pm disable-user")) {
            String pkg = command.substring(command.lastIndexOf(' ') + 1);
            disabled.add(pkg);
            if (command.equals(powerLossOn)) { powerLossOn = null; throw new PowerLoss(); }
            return "Package " + pkg + " new state: disabled-user";
        }
        if (command.startsWith("pm enable")) {
            String pkg = command.substring(command.lastIndexOf(' ') + 1);
            disabled.remove(pkg); return "Package " + pkg + " new state: enabled";
        }
        if (command.startsWith("am start -W")) return "TotalTime: 10";
        if (command.startsWith("pm path")) return "package:/system/app/Launcher.apk";
        return "";
    }
    public void close() {}
    static final class PowerLoss extends Error {}
}
