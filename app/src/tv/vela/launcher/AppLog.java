package tv.vela.launcher;

import android.util.Log;

/** The app's log (adb logcat -s Vela). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("Vela", message);
    }
}
