package tv.vela.launcher;
import android.content.Context;
class Config {
    static String RECOVERY_HOME = "";
    static boolean ISOLATE_RECOVERY_HOME = false;
    static boolean DISABLE_APPS = false;
    static final boolean DISABLE_ASSISTANT = false;
}
class Ui { static String tr(String pt, String en, String es) { return es; } }
class AppLog { static void w(String message) {} }
class Watchdog { static void clear(Context context) {} }
class ModeJob {
    static final String APPLY = "apply", REVERT = "revert", VERIFY = "verify", MEMORY = "memory";
    static void schedule(Context context, String action, String mode, long delay) {}
}
