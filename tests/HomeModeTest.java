package tv.vela.launcher;
import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

public class HomeModeTest {
    private static int passed;
    private static Context context(String dream) throws Exception {
        Adb.reset(dream);
        return new Context(Files.createTempDirectory("home-mode-test-").toFile());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void pass(String name) { System.out.println("PASS " + name); passed++; }
    public static void main(String[] args) throws Exception {
        Context c = context("com.vendor.dream/.Personal,com.vendor.dream/.Second");
        HomeMode.applyNow(c, HomeMode.FREE);
        check(HomeMode.FREE.equals(HomeMode.state(c).optString("mode")), "free mode not reached");
        check(Adb.commands.stream().noneMatch(s -> s.startsWith("pm disable-user")), "default mode disabled an app");
        HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.STOCK.equals(Adb.home), "stock home not restored");
        check(Adb.dream.equals("com.vendor.dream/.Personal,com.vendor.dream/.Second"), "custom screensaver list lost");
        pass("conservative activation and exact custom screensaver recovery");

        c = context("null");
        HomeMode.applyNow(c, HomeMode.FREE); HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.dream.equals("null"), "unset screensaver override was not removed");
        pass("unset screensaver preference stays unset");

        c = context("com.vendor.dream/.Personal");
        Adb.rejectDream = true;
        HomeMode.applyNow(c, HomeMode.FREE);
        check(Adb.home.equals(Adb.STOCK), "failed switch left our home active");
        check(Adb.dream.equals("com.vendor.dream/.Personal"), "failed switch lost custom screensaver");
        check(HomeMode.ORIGINAL.equals(HomeMode.state(c).optString("mode")), "failed switch was marked active");
        check(!HomeMode.state(c).optString("error").isEmpty(), "failed switch was not reported");
        pass("rejected screensaver change rolls back and reports failure");

        c = context("com.vendor.dream/.Personal");
        Config.DISABLE_APPS = true;
        Adb.powerLossOn = "pm disable-user --user 0 com.vendor.launcher";
        try { HomeMode.applyNow(c, HomeMode.FREE); throw new AssertionError("power loss not simulated"); }
        catch (Adb.PowerLoss expected) {}
        check(HomeMode.stringList(HomeMode.state(c), "disabled").contains("com.vendor.launcher"), "change was not journaled before command");
        check(HomeMode.state(c).optBoolean("switchPending"), "interrupted transaction not marked pending");
        HomeMode.verifyNow(c);
        check(!Adb.disabled.contains("com.vendor.launcher"), "boot recovery did not restore disabled launcher");
        check(Adb.home.equals(Adb.STOCK) && Adb.dream.equals("com.vendor.dream/.Personal"), "boot recovery lost originals");
        check(!HomeMode.state(c).optBoolean("switchPending"), "completed recovery still pending");
        pass("power loss after disable is recovered on boot");

        c = context("null");
        Config.DISABLE_APPS = true;
        String preDisabled = HomeMode.DEBLOAT[0];
        Adb.disabled.add(preDisabled);
        HomeMode.applyNow(c, HomeMode.FREE); HomeMode.verifyNow(c); HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.disabled.contains(preDisabled), "previously disabled app was enabled");
        check(!HomeMode.stringList(HomeMode.state(c), "disabled").contains(preDisabled), "preexisting disable claimed as ours");
        pass("previously disabled apps stay disabled");

        c = context("com.vendor.dream/.Personal");
        new File(c.getFilesDir(), "home-state.json.tmp").mkdir();
        HomeMode.applyNow(c, HomeMode.FREE);
        check(Adb.commands.stream().noneMatch(s -> s.startsWith("cmd package set-home-activity") || s.startsWith("settings put")),
                "system changed without durable recovery data");
        pass("unwritable recovery journal blocks system changes");

        c = context("com.vendor.dream/.First");
        HomeMode.applyNow(c, HomeMode.FREE); HomeMode.applyNow(c, HomeMode.ORIGINAL);
        Adb.dream = "com.vendor.dream/.ChangedByUser";
        HomeMode.applyNow(c, HomeMode.FREE); HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.dream.equals("com.vendor.dream/.ChangedByUser"), "next activation reused an outdated snapshot");
        pass("new activation captures changes made in original mode");
        c = context("com.vendor.dream/.Personal");
        Adb.roleResponse = "Unknown command: get-role-holders";
        Adb.resolveResponse = "android/com.android.internal.app.ResolverActivity";
        Adb.homeActivities = "priority=0\n" + Adb.STOCK + "\n" + HomeMode.HOME_ACTIVITY
                + "\ncom.android.tv.settings/.system.FallbackHome";
        HomeMode.applyNow(c, HomeMode.FREE);
        check(HomeMode.FREE.equals(HomeMode.state(c).optString("mode")), "Android 11 activation failed");
        check(Adb.STOCK.equals(HomeMode.state(c).optString("originalHome")), "resolver or fallback saved as backup");
        HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.STOCK.equals(Adb.home), "Android 11 backup home not restored");
        pass("Android 11 without role commands preserves the only real alternative");

        c = context("null");
        Adb.roleResponse = "";
        Adb.resolveResponse = "android/com.android.internal.app.ResolverActivity";
        Adb.homeActivities = Adb.STOCK + "\ncom.other.launcher/.Home\n" + HomeMode.HOME_ACTIVITY;
        HomeMode.applyNow(c, HomeMode.FREE);
        check(Adb.commands.stream().noneMatch(command -> command.startsWith("cmd package set-home-activity") || command.startsWith("settings put")), "ambiguous homes changed system defaults");
        check(!HomeMode.FREE.equals(HomeMode.state(c).optString("mode")), "ambiguous home was guessed");
        pass("ambiguous chooser blocks activation instead of guessing a backup");

        c = context("com.vendor.dream/.Personal");
        HomeMode.applyNow(c, HomeMode.FREE); HomeMode.verifyNow(c);
        check(Adb.commands.stream().noneMatch(command -> command.startsWith("pm disable-user")), "verification disabled apps despite disable_apps=false");
        HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.home.equals(Adb.STOCK) && Adb.dream.equals("com.vendor.dream/.Personal"), "conservative verification broke recovery");
        pass("verification respects disable_apps=false and preserves apps");
        c = context("com.vendor.dream/.Personal");
        Config.RECOVERY_HOME = HomeMode.GOOGLE_HOME;
        Adb.home = "com.retired.launcher/.Home";
        Adb.homeActivities = Adb.home + "\n" + HomeMode.GOOGLE_HOME + "\n" + HomeMode.HOME_ACTIVITY;
        Adb.present.add("com.google.android.apps.tv.launcherx");
        Adb.disabled.add("com.google.android.apps.tv.launcherx");
        HomeMode.applyNow(c, HomeMode.FREE);
        check(HomeMode.GOOGLE_HOME.equals(HomeMode.state(c).optString("originalHome")), "retired launcher saved as recovery");
        check(Adb.commands.stream().noneMatch(command -> command.startsWith("am start -W")), "switch ran obsolete foreground benchmarks");
        HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(HomeMode.GOOGLE_HOME.equals(Adb.home), "configured factory home not restored");
        check(!Adb.disabled.contains("com.google.android.apps.tv.launcherx"), "factory recovery launcher remained disabled");
        pass("retired launcher replaced by a validated factory recovery home");

        c = context("null");
        Config.RECOVERY_HOME = "com.missing.launcher/.Home";
        HomeMode.applyNow(c, HomeMode.FREE);
        check(Adb.commands.stream().noneMatch(command -> command.startsWith("cmd package set-home-activity") || command.startsWith("settings put")), "missing recovery target changed defaults");
        pass("missing recovery target blocks activation before changing settings");
        c = context("com.vendor.dream/.Personal");
        Config.RECOVERY_HOME = Adb.STOCK; Config.ISOLATE_RECOVERY_HOME = true;
        Adb.factoryDominates = true; Adb.homeActivities = Adb.STOCK + "\n" + HomeMode.HOME_ACTIVITY;
        HomeMode.applyNow(c, HomeMode.FREE);
        check(HomeMode.FREE.equals(HomeMode.state(c).optString("mode")), "exclusive factory activation failed");
        check(Adb.disabled.size()==1 && Adb.disabled.contains("com.vendor.launcher"), "exclusive mode affected unrelated apps");
        check(HomeMode.stringList(HomeMode.state(c),"disabled").contains("com.vendor.launcher"), "factory disable not journaled");
        HomeMode.verifyNow(c);
        check(Adb.disabled.size()==1, "verification expanded the exclusive package list");
        Config.RECOVERY_HOME="";Config.ISOLATE_RECOVERY_HOME=false;
        HomeMode.verifyNow(c);
        check(Adb.disabled.size()==1,"neutral APK expanded the migrated package list");
        HomeMode.applyNow(c, HomeMode.ORIGINAL);
        check(Adb.disabled.isEmpty() && Adb.home.equals(Adb.STOCK), "exclusive mode did not restore factory home");
        HomeMode.applyNow(c,HomeMode.FREE);
        check(HomeMode.FREE.equals(HomeMode.state(c).optString("mode"))&&Adb.disabled.size()==1,
                "neutral APK lost the recorded factory home exception after restoring Original");
        HomeMode.applyNow(c,HomeMode.ORIGINAL);
        check(Adb.disabled.isEmpty()&&Adb.home.equals(Adb.STOCK),"neutral APK failed to restore Original");
        pass("higher-priority factory home isolated alone and restored exactly");
        System.out.println(passed + " recovery scenarios passed");
    }
}
