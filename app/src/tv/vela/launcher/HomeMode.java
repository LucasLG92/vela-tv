package tv.vela.launcher;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Original ⇄ Vela. "free": our home screen and screensaver, and the apps in the lists below turned off
 * (pm disable-user: still installed, back with one button). "original": everything as it came from the factory.
 *
 * It all goes through the TV's own ADB (Adb, 127.0.0.1:5555), which you allow once on the TV. The switches run as
 * background jobs (ModeJob) in the main process; the settings screen (the :home process) asks for them and follows the
 * state file. Only the TV itself asks for switches: nothing here listens to the network.
 */
final class HomeMode {
    private HomeMode() {}

    static final String PKG = "tv.vela.launcher";
    static final String HOME_ACTIVITY = PKG + "/" + PKG + ".HomeActivity";
    static final String SETTINGS_ACTIVITY = PKG + "/" + PKG + ".SettingsActivity";
    static final String DREAM = PKG + "/" + PKG + ".Screensaver";
    static final String FREE = "free", ORIGINAL = "original";

    /** Google TV's home screen: the fallback when the TV's own one wasn't recorded. */
    static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx/.home.HomeActivity";
    /** Google TV has a RecoveryActivity that would take over the home screen without its launcher. */
    static final String GOOGLE_SETUP = "com.google.android.tungsten.setupwraith";

    /**
     * Turned off in Vela mode, when the TV has them. Google extras first, then TV-maker extras (TCL here; add
     * your brand's, see the README). The TV's own home screen and screensaver are added at switch time.
     */
    static final String[] DEBLOAT = {
        "com.google.android.play.games", "com.google.android.youtube.tvmusic",        // Play Games, YouTube Music
        // TCL
        "com.tcl.magiconnectfree", "com.tcl.miracast",                                // screen mirroring apps
        "com.tcl.waterfall.overseas", "com.tcl.channelplus",                          // TCL Channel and its Live TV
        "com.tcl.messagebox", "com.tcl.usercenter",                                   // TCL messages and account
        "com.tcl.exhibit", "com.tcl.esticker", "com.tcl.hotelmenu",                   // store demo, hotel menu
        "com.tcl.repairguide", "com.tcl.ocean.instructions", "com.tcl.logkit",        // repair guide, manual, logs
    };

    /** The voice assistant, only with disable_assistant=true in config.properties (the mic button stops working). */
    static final String[] ASSISTANT = {"com.google.android.katniss", "com.android.hotwordenrollment.okgoogle"};

    /** Never turned off, not even by mistake (picture, sound, inputs, remote, store). Checked before every disable. */
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
        "com.tcl.ttvs", "com.tvos", "com.tcl.systemserver", "com.tcl.systemui.plugin", "com.tcl.tv", "com.tcl.tvinput",
        "com.tcl.hearaid", "com.dolby.android.audio.service", "com.tcl.globalkeyoverlay", "com.tcl.autopair",
        "com.tcl.UpdatePeripheral", "com.tcl.dashboard", "com.tcl.suspension", "com.tcl.providers.config",
        "com.tcl.gamebar", "com.tcl.airplay2", "com.mirakulo.astrotv.service", "com.tcl.initsetup",
        "com.tcl.useragreement", "com.tcl.factory.view", "com.tcl.guard", "com.tcl.t_solo",
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.google.android.apps.mediashell",
        "com.google.android.tv.remote.service", "com.google.android.inputmethod.latin", "com.google.android.webview",
        "com.android.tv.settings", "com.android.systemui", "android", PKG));

    /** One switch at a time (jobs can overlap: a boot check and a button press). */
    private static final Object RUN = new Object();
    private static final Object FILE_LOCK = new Object();

    // MARK: - Requests (from any process)

    /** Asks the main process to switch (the settings screen calls this). */
    static void request(Context c, String mode) {
        ModeJob.schedule(c, ModeJob.APPLY, mode, 0);
    }

    /** Our home screen crashed 3 times in 5 minutes: give the home screen back to the TV's own (asked by :home). */
    static void requestRevertHome(Context c) {
        ModeJob.schedule(c, ModeJob.REVERT, null, 0);
    }

    /** After the TV boots (and after a TV update): wait for the system to settle and check Vela mode. */
    static void bootCheck(Context c) {
        ModeJob.schedule(c, ModeJob.VERIFY, null, 45_000);
    }

    /** After an update of this app: complete Vela mode if anything is missing. */
    static void verify(Context c) {
        ModeJob.schedule(c, ModeJob.VERIFY, null, 0);
    }

    // MARK: - Switches (main process, from ModeJob)

    static void applyNow(Context c, String mode) {
        synchronized (RUN) {
            if (FREE.equals(mode)) applyFree(c); else applyOriginal(c);
        }
    }

    private static void applyFree(Context c) {
        JSONObject s = state(c);
        try (Adb adb = connect(c, s)) {
            // The TV's own home screen and screensaver, recorded once (before ours replace them) so "original" restores
            // exactly what was there, on any brand.
            String home = s.optString("originalHome", "");
            boolean fresh = ORIGINAL.equals(s.optString("mode")) && !s.optBoolean("switchPending")
                    && stringList(s, "disabled").isEmpty();
            if (fresh || home.isEmpty() || home.startsWith(PKG + "/")) {
                String found = Config.RECOVERY_HOME.isEmpty() ? currentHome(adb) : configuredRecoveryHome(adb);
                home = found != null ? found : "";
            }
            String dream = s.optString("originalDream", "");
            if (dream.isEmpty()) dream = defaultDream(adb);
            JSONObject latest = state(c);
            if (home.isEmpty()) throw new IOException(Ui.tr("Início original não encontrado.",
                    "Original home screen not found.", "No se ha encontrado el inicio original."));
            if (!latest.has("originalDreamComponents") || fresh) {
                latest.put("originalDreamComponents", adb.shell("settings get secure screensaver_components").trim());
                latest.put("originalDisabled", new JSONArray(sorted(packages(adb, "-d"))));
            }
            latest.put("isolateHome",isolatedHome(s,home)?expandedComponent(home):"");
            latest.put("originalHome", home).put("originalDream", dream).put("switchPending", true);
            saveRequired(c, latest);
            busy(c, Ui.tr("Trocando o Início e a tela de descanso…", "Switching the home screen and screensaver…", "Cambiando el inicio y el salvapantallas…"));
            check(adb.shell("cmd package set-home-activity " + HOME_ACTIVITY), "Success",
                    Ui.tr("o Android não aceitou o novo Início", "Android didn't accept the new home screen", "Android no ha aceptado el nuevo inicio"));
            adb.shell("settings put secure screensaver_components " + DREAM);
            if (!DREAM.equals(adb.shell("settings get secure screensaver_components").trim())) {
                throw new IOException(Ui.tr("A tela de descanso não mudou.", "The screensaver was not changed.",
                        "No se ha podido cambiar el salvapantallas."));
            }
            List<String> targets = targets(home, dream, isolatedHome(latest,home));
            Set<String> disabled = new HashSet<>(stringList(state(c), "disabled"));
            Set<String> present = packages(adb, "");
            Set<String> alreadyDisabled = packages(adb, "-d");
            int done = 0;
            for (String pkg : targets) {
                done++;
                busy(c, Ui.tr("Desligando apps (", "Turning off apps (", "Desactivando apps (") + done + "/" + targets.size() + ")…");
                if (PROTECTED.contains(pkg) || !present.contains(pkg) || alreadyDisabled.contains(pkg)) continue;
                // Record before the command: even a lost ADB reply must leave a recovery path.
                disabled.add(pkg);
                journalDisabled(c, disabled);
                String out = adb.shell("pm disable-user --user 0 " + pkg);
                if (!out.contains("disabled-user")) {
                    disabled.remove(pkg);
                    journalDisabled(c, disabled);
                    AppLog.w("could not turn off " + pkg + ": " + out.trim());
                }
            }
            // A privileged TCL factory home can outrank a preferred third-party home until it is disabled.
            check(adb.shell("cmd package set-home-activity " + HOME_ACTIVITY), "Success", "Android rejected Vela home");
            checkSelectedHome(adb);
            Watchdog.clear(c);
            s = state(c);
            s.put("mode", FREE).put("since", System.currentTimeMillis()).put("homeReverted", false).put("switchPending", false)
                    .put("disabled", new JSONArray(sorted(disabled)));
            saveRequired(c, s);
            notice(c, Ui.tr("Vela ligado: Início e tela de descanso novos, " + disabled.size() + " apps desligados.",
                    "Vela is on: new home screen and screensaver, " + disabled.size() + " apps turned off.", "Vela activo: nuevo inicio y salvapantallas; " + disabled.size() + " apps desactivadas."));
            done(c, null);
            adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        } catch (Exception e) {
            if (state(c).optBoolean("switchPending")) applyOriginal(c);
            done(c, Ui.tr("Não deu para ligar o Vela: ", "Couldn't turn on Vela: ", "No se ha podido activar Vela: ") + e.getMessage());
        }
    }

    private static void applyOriginal(Context c) {
        JSONObject s = state(c);
        try (Adb adb = connect(c, s)) {
            s = state(c);
            s.put("switchPending", true);
            saveRequired(c, s);
            busy(c, Ui.tr("Religando os apps…", "Turning the apps back on…", "Restaurando las apps…"));
            String home = s.optString("originalHome", "");
            Set<String> toEnable = new HashSet<>(stringList(s, "disabled"));
            Set<String> present = packages(adb, "");
            int done = 0;
            List<String> failed = new ArrayList<>();
            for (String pkg : sorted(toEnable)) {
                done++;
                busy(c, Ui.tr("Religando apps (", "Turning apps back on (", "Restaurando apps (") + done + "/" + toEnable.size() + ")…");
                if (!present.contains(pkg)) continue;
                // Checked: whatever didn't come back stays on the list (and in the notice); nothing disappears silently.
                String out = adb.shell("pm enable --user 0 " + pkg);
                if (!out.contains("new state: enabled")) failed.add(pkg);
            }
            busy(c, Ui.tr("Devolvendo o Início original…", "Restoring the original home screen…", "Restaurando el inicio original…"));
            boolean homeOk = restoreHome(adb, home);
            restoreDream(adb, s);
            s = state(c);
            s.put("mode", ORIGINAL).put("since", System.currentTimeMillis()).put("homeReverted", false)
                    .put("switchPending", !homeOk || !failed.isEmpty())
                    .put("disabled", new JSONArray(failed));
            saveRequired(c, s);
            if (failed.isEmpty() && homeOk) {
                notice(c, Ui.tr("Modo original: Início, tela de descanso e apps de fábrica de volta.",
                        "Original mode: the factory home screen, screensaver and apps are back.", "Modo original: inicio, salvapantallas y apps restaurados."));
            } else {
                List<String> problems = new ArrayList<>(failed);
                if (!homeOk) problems.add(Ui.tr("o Início original", "the original home screen", "el inicio original"));
                notice(c, Ui.tr("Modo original, mas não consegui religar: ", "Original mode, but I couldn't turn back on: ", "Modo original; no se ha podido restaurar: ")
                        + String.join(", ", problems) + ".");
            }
            done(c, null);
            adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
        } catch (Exception e) {
            done(c, Ui.tr("Não deu para voltar ao original: ", "Couldn't go back to the original: ", "No se ha podido volver al original: ") + e.getMessage());
        }
    }

    /** The recorded home screen; without one, Google TV's; without that, the system picks (we leave the role). */
    private static boolean restoreHome(Adb adb, String home) throws IOException {
        if (home.isEmpty() && adb.shell("pm path " + packageOf(GOOGLE_HOME)).contains("package:")) home = GOOGLE_HOME;
        if (!home.isEmpty()) {
            adb.shell("pm enable " + packageOf(home));
            if (adb.shell("cmd package set-home-activity " + home).contains("Success")) return true;
        }
        adb.shell("cmd role remove-role-holder --user 0 android.app.role.HOME " + PKG);
        return !adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").contains(PKG);
    }

    /** Safety net: our home screen crashed 3 times in a row. Only the home screen goes back; the rest stays. */
    static void revertHomeNow(Context app) {
        synchronized (RUN) {
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                JSONObject s = state(app);
                String home = s.optString("originalHome", "");
                if (stringList(s, "disabled").contains(GOOGLE_SETUP)) adb.shell("pm enable --user 0 " + GOOGLE_SETUP);
                if (!restoreHome(adb, home)) throw new IOException("could not restore the original home screen");
                String homePkg = packageOf(home);
                JSONArray kept = new JSONArray();
                for (String pkg : stringList(s, "disabled")) {
                    if (!pkg.equals(GOOGLE_SETUP) && !pkg.equals(homePkg)) kept.put(pkg);
                }
                s.put("homeReverted", true).put("disabled", kept);
                save(app, s);
                notice(app, Ui.tr("O Início do Vela caiu 3 vezes seguidas; voltei para o Início original.",
                        "Vela's home screen crashed 3 times in a row; I switched back to the original one.", "El inicio de Vela ha fallado 3 veces seguidas; se ha restaurado el original."));
                adb.shell("am start -a android.intent.action.MAIN -c android.intent.category.HOME");
            } catch (Exception e) {
                AppLog.w("could not give the home screen back: " + e.getMessage());
            }
        }
    }

    /**
     * In Vela mode, checks that everything is still as we left it (the listed apps off, our home screen and
     * screensaver) and completes whatever is missing, with a notice. Runs after the TV boots (TV updates can turn
     * things back on) and after an update of this app.
     */
    static void verifyNow(Context app) {
        synchronized (RUN) {
            JSONObject s = state(app);
            if (s.optBoolean("switchPending")) {
                applyOriginal(app);
                return;
            }
            String fingerprint = Build.FINGERPRINT;
            boolean updated = !fingerprint.equals(s.optString("fingerprint", fingerprint));
            try {
                s.put("fingerprint", fingerprint);
                save(app, s);
            } catch (JSONException ignored) {}
            if (!FREE.equals(s.optString("mode"))) return;
            List<String> fixed = new ArrayList<>();
            try (Adb adb = Adb.connect(app, 10_000, null)) {
                Set<String> present = packages(adb, "");
                Set<String> disabledNow = packages(adb, "-d");
                boolean reverted = s.optBoolean("homeReverted");
                String home = s.optString("originalHome", "");
                Set<String> wanted = Config.DISABLE_APPS ? new HashSet<>(stringList(s, "disabled")) : new HashSet<>();
                Set<String> originalDisabled = new HashSet<>(stringList(s, "originalDisabled"));
                boolean isolated=isolatedHome(s,home);
                if(isolated)s.put("isolateHome",expandedComponent(home));
                for (String pkg : targets(home, s.optString("originalDream", ""), isolated)) {
                    if (!reverted || !(pkg.equals(packageOf(home)) || pkg.equals(GOOGLE_SETUP))) wanted.add(pkg);
                }
                Set<String> disabled = new HashSet<>(stringList(s, "disabled"));
                for (String pkg : sorted(wanted)) {
                    if (PROTECTED.contains(pkg) || !present.contains(pkg) || originalDisabled.contains(pkg)) continue;
                    if (disabledNow.contains(pkg)) {
                        disabled.add(pkg);
                        continue;
                    }
                    disabled.add(pkg);
                    journalDisabled(app, disabled);
                    if (adb.shell("pm disable-user --user 0 " + pkg).contains("disabled-user")) {
                        fixed.add(pkg);
                    } else {
                        disabled.remove(pkg);
                        journalDisabled(app, disabled);
                    }
                }
                if (!reverted && !adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").contains(PKG)) {
                    adb.shell("cmd package set-home-activity " + HOME_ACTIVITY);
                    checkSelectedHome(adb);
                    fixed.add(Ui.tr("o Início", "the home screen", "el inicio"));
                }
                if (!adb.shell("settings get secure screensaver_components").contains(DREAM)) {
                    adb.shell("settings put secure screensaver_components " + DREAM);
                    fixed.add(Ui.tr("a tela de descanso", "the screensaver", "el salvapantallas"));
                }
                JSONObject latest = state(app);
                if(isolated)latest.put("isolateHome",expandedComponent(home));
                latest.put("disabled", new JSONArray(sorted(disabled)));
                save(app, latest);
            } catch (Exception e) {
                notice(app, Ui.tr("Não consegui conferir o Vela: ", "Couldn't check Vela: ", "No se ha podido comprobar Vela: ") + e.getMessage());
                return;
            }
            if (!fixed.isEmpty()) {
                notice(app, (updated ? Ui.tr("A atualização da TV religou ", "The TV update turned back on ", "La actualización de la TV ha reactivado ")
                        : Ui.tr("Desliguei o que faltava: ", "Turned off what was missing: ", "Se ha desactivado lo pendiente: ")) + String.join(", ", fixed) + ".");
            } else if (updated) {
                notice(app, Ui.tr("A TV foi atualizada; o Vela continua igual.", "The TV was updated; Vela is still on.", "La TV se ha actualizado; Vela sigue activo."));
            }
        }
    }

    /** What to turn off: the TV's own home screen and screensaver, Google TV's recovery home, and the lists above. */
    /** Remember an already validated factory-home exception across builds; never extend it to a different home. */
    private static boolean isolatedHome(JSONObject state,String home){
        if(home.isEmpty())return false;
        return expandedComponent(home).equals(state.optString("isolateHome"))
                ||(Config.ISOLATE_RECOVERY_HOME&&!Config.RECOVERY_HOME.isEmpty()
                    &&expandedComponent(home).equals(expandedComponent(Config.RECOVERY_HOME)))
                ||(!Config.DISABLE_APPS&&stringList(state,"disabled").contains(packageOf(home)));
    }
    private static List<String> targets(String home, String dream, boolean isolate) {
        List<String> out = new ArrayList<>();
        String homePkg = packageOf(home);
        if (isolate
                && homePkg != null && !homePkg.equals(PKG)) out.add(homePkg);
        if (!Config.DISABLE_APPS) return out;
        if (homePkg != null && !homePkg.equals(PKG) && !out.contains(homePkg)) out.add(homePkg);
        out.add(GOOGLE_SETUP);
        String dreamPkg = packageOf(dream);
        if (dreamPkg != null && !dreamPkg.equals(PKG) && !dreamPkg.equals(homePkg)) out.add(dreamPkg);
        out.addAll(Arrays.asList(DEBLOAT));
        if (Config.DISABLE_ASSISTANT) out.addAll(Arrays.asList(ASSISTANT));
        return out;
    }

    private static void checkSelectedHome(Adb adb) throws IOException {
        String resolved = adb.shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        for (String line : resolved.split("\n"))
            if (expandedComponent(HOME_ACTIVITY).equals(expandedComponent(line.trim()))) return;
        throw new IOException(Ui.tr("O sistema não manteve o Vela como início.", "The system did not keep Vela as home.",
                "El sistema no ha mantenido Vela como inicio."));
    }

    /** A personal recovery target is accepted only when Package Manager advertises it as a HOME activity. */
    private static String configuredRecoveryHome(Adb adb) throws IOException {
        String configured = Config.RECOVERY_HOME.trim();
        if (!configured.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+") || configured.startsWith(PKG + "/"))
            throw new IOException("Invalid recovery home");
        String activities = adb.shell("cmd package query-activities --brief --user 0 --query-flags 512"
                + " -a android.intent.action.MAIN -c android.intent.category.HOME");
        for (String line : activities.split("\n")) {
            line = line.trim();
            if (expandedComponent(configured).equals(expandedComponent(line))) return line;
        }
        throw new IOException(Ui.tr("Início de recuperação não encontrado.", "Recovery home screen not found.",
                "No se ha encontrado el inicio de recuperación. No se han cambiado los ajustes."));
    }

    /** The home screen in use now ("package/activity"): the HOME role holder, among the TV's home screens. */
    private static String currentHome(Adb adb) throws IOException {
        String holder = adb.shell("cmd role get-role-holders --user 0 android.app.role.HOME").trim();
        if (PKG.equals(holder)) return null;
        String list = adb.shell("cmd package query-activities --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME");
        Set<String> candidates = new LinkedHashSet<>();
        for (String line : list.split("\n")) {
            line = line.trim();
            if (!line.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+") || line.startsWith(PKG + "/")
                    || line.startsWith("android/") || line.endsWith(".FallbackHome")) continue;
            candidates.add(line);
        }
        for (String candidate : candidates) if (candidate.startsWith(holder + "/")) return candidate;
        // Older TCL firmware does not implement get-role-holders. The system chooser is never a recoverable home.
        String resolved = adb.shell("cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME").trim();
        for (String line : resolved.split("\n")) {
            line = line.trim();
            if (line.startsWith(PKG + "/")) return null;
            for (String candidate : candidates) if (expandedComponent(candidate).equals(expandedComponent(line))) return candidate;
        }
        // Installing another launcher can clear Android's preferred home. A single real alternative is unambiguous.
        return candidates.size() == 1 ? candidates.iterator().next() : null;
    }

    private static String expandedComponent(String component) {
        int slash = component.indexOf('/');
        if (slash < 0) return component;
        String pkg = component.substring(0, slash), activity = component.substring(slash + 1);
        return pkg + "/" + (activity.startsWith(".") ? pkg + activity : activity);
    }

    /** The system's default screensaver ("package/service"), or "". */
    private static String defaultDream(Adb adb) throws IOException {
        String d = adb.shell("settings get secure screensaver_default_component").trim();
        return d.contains("/") && !d.startsWith(PKG + "/") ? d : "";
    }

    private static String packageOf(String component) {
        if (component == null || component.isEmpty()) return null;
        int slash = component.indexOf('/');
        return slash > 0 ? component.substring(0, slash) : component;
    }

    /** Packages the shell sees (all; "-d" = only the disabled ones). The app alone can't see the ones it doesn't declare. */
    private static Set<String> packages(Adb adb, String flags) throws IOException {
        Set<String> out = new HashSet<>();
        for (String line : adb.shell("pm list packages " + flags).split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring(8));
        }
        return out;
    }

    private static Adb connect(Context c, JSONObject s) throws IOException {
        busy(c, Ui.tr("Conectando…", "Connecting…", "Conectando…"));
        Adb adb = Adb.connect(c, 120_000, () -> busy(c, Ui.tr("Aceite o aviso “Permitir depuração” na TV (marque “Sempre permitir”).",
                "Accept “Allow USB debugging?” on the TV (tick “Always allow”).", "Acepta «Permitir depuración USB» en la TV y marca «Permitir siempre».")));
        if (!s.optBoolean("adbAuthorized")) {
            JSONObject latest = state(c);
            try { latest.put("adbAuthorized", true); } catch (JSONException ignored) {}
            save(c, latest);
        }
        return adb;
    }

    private static void check(String output, String expected, String error) throws IOException {
        if (!output.contains(expected)) throw new IOException(error + " (" + output.trim() + ")");
    }

    // MARK: - Measurements (without opening apps: memory and the home button)

    private static void measureHomeTime(Context c, Adb adb, String mode) throws IOException, JSONException {
        List<Integer> samples = new ArrayList<>();
        Pattern total = Pattern.compile("TotalTime: (\\d+)");
        for (int i = 0; i < 3; i++) {
            adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
            SystemClock.sleep(500);
            Matcher m = total.matcher(adb.shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"));
            if (m.find()) samples.add(Integer.parseInt(m.group(1)));
            SystemClock.sleep(600);
        }
        adb.shell("am start -W -n " + SETTINGS_ACTIVITY);
        if (samples.isEmpty()) return;
        Collections.sort(samples);
        putMetric(c, mode, "homeMs", samples.get(samples.size() / 2));
    }

    /** Two minutes after a switch (the home screen settled, caches trimmed). */
    static void measureMemoryNow(Context c, String mode) {
        synchronized (RUN) {
            if (!mode.equals(state(c).optString("mode"))) return;
            try (Adb adb = Adb.connect(c, 10_000, null)) {
                measureMemory(c, adb, mode);
            } catch (Exception e) {
                AppLog.w("memory measurement failed: " + e.getMessage());
            }
        }
    }

    private static void measureMemory(Context c, Adb adb, String mode) throws IOException, JSONException {
        Matcher avail = Pattern.compile("MemAvailable:\\s+(\\d+) kB").matcher(adb.shell("cat /proc/meminfo"));
        if (avail.find()) putMetric(c, mode, "memAvailableMB", Long.parseLong(avail.group(1)) / 1024);
        long pss = 0;
        String home = packageOf(state(c).optString("originalHome", ""));
        String[] processes = FREE.equals(mode) ? new String[] {PKG + ":home"}
                : home == null ? new String[0] : new String[] {home, home + ":coreservices"};
        Pattern total = Pattern.compile("TOTAL PSS:\\s+(\\d+)");
        for (String p : processes) {
            Matcher m = total.matcher(adb.shell("dumpsys meminfo " + p));
            if (m.find()) pss += Long.parseLong(m.group(1));
        }
        if (pss > 0) putMetric(c, mode, "homePssMB", pss / 1024);
    }

    private static void putMetric(Context c, String mode, String key, long value) throws JSONException {
        JSONObject s = state(c);
        JSONObject metrics = s.optJSONObject("metrics");
        if (metrics == null) metrics = new JSONObject();
        JSONObject m = metrics.optJSONObject(mode);
        if (m == null) m = new JSONObject();
        m.put(key, value).put("at", System.currentTimeMillis());
        metrics.put(mode, m);
        s.put("metrics", metrics);
        save(c, s);
    }

    private static JSONObject metrics(JSONObject s, String mode) {
        JSONObject metrics = s.optJSONObject("metrics");
        return metrics == null ? null : metrics.optJSONObject(mode);
    }

    // MARK: - State (a file shared by the two processes; atomic writes)

    static JSONObject state(Context c) {
        synchronized (FILE_LOCK) {
            File f = new File(c.getFilesDir(), "home-state.json");
            if (!f.exists()) return defaults();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] data = new byte[(int) f.length()];
                int read = 0;
                while (read < data.length) {
                    int n = in.read(data, read, data.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return new JSONObject(new String(data, 0, read, StandardCharsets.UTF_8));
            } catch (IOException | JSONException e) {
                return defaults();
            }
        }
    }

    private static JSONObject defaults() {
        try {
            return new JSONObject().put("mode", ORIGINAL);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static boolean save(Context c, JSONObject s) {
        synchronized (FILE_LOCK) {
            File dir = c.getFilesDir();
            File tmp = new File(dir, "home-state.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(s.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (IOException e) {
                AppLog.w("could not save the state: " + e.getMessage());
                return false;
            }
            try {
                Files.move(tmp.toPath(), new File(dir, "home-state.json").toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                AppLog.w("could not replace the state file: " + e.getMessage());
                return false;
            }
            return true;
        }
    }

    private static void saveRequired(Context c, JSONObject s) throws IOException {
        if (!save(c, s)) throw new IOException(Ui.tr("Não foi possível guardar a recuperação.",
                "Could not save recovery data.", "No se han podido guardar los datos de recuperación."));
    }

    private static void journalDisabled(Context c, Set<String> disabled) throws IOException, JSONException {
        JSONObject latest = state(c);
        latest.put("disabled", new JSONArray(sorted(disabled)));
        saveRequired(c, latest);
    }

    private static void restoreDream(Adb adb, JSONObject state) throws IOException {
        String original = state.optString("originalDreamComponents", state.optString("originalDream", "null"));
        if (original.isEmpty() || original.equals("null")) {
            adb.shell("settings delete secure screensaver_components");
            original = "null";
        } else {
            adb.shell("settings put secure screensaver_components '" + original.replace("'", "'\\''") + "'");
        }
        if (!original.equals(adb.shell("settings get secure screensaver_components").trim())) {
            throw new IOException(Ui.tr("Não foi possível restaurar a tela de descanso.",
                    "Could not restore the screensaver.", "No se ha podido restaurar el salvapantallas."));
        }
    }

    private static void busy(Context c, String text) {
        JSONObject s = state(c);
        try { s.put("busy", text).put("error", ""); } catch (JSONException ignored) {}
        save(c, s);
    }

    private static void done(Context c, String error) {
        JSONObject s = state(c);
        s.remove("busy");
        try { s.put("error", error == null ? "" : error); } catch (JSONException ignored) {}
        save(c, s);
        if (error != null) {
            AppLog.w(error);
            notice(c, error);
        }
    }

    /** A notice for the home screen (a bubble) and the settings screen. Keeps the last 20. */
    static void notice(Context c, String text) {
        AppLog.w("notice: " + text);
        JSONObject s = state(c);
        JSONArray old = s.optJSONArray("notices");
        JSONArray list = new JSONArray();
        try {
            list.put(new JSONObject().put("at", System.currentTimeMillis()).put("text", text));
            if (old != null) for (int i = 0; i < old.length() && list.length() < 20; i++) list.put(old.get(i));
            s.put("notices", list);
        } catch (JSONException ignored) {}
        save(c, s);
    }

    // MARK: - Helpers

    static List<String> stringList(JSONObject s, String key) {
        List<String> out = new ArrayList<>();
        JSONArray a = s.optJSONArray(key);
        if (a != null) for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    private static List<String> sorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
