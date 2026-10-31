package tv.vela.launcher;

import android.content.*;

/** Runs only for a cached reminder, reboot or package replacement; never downloads or tunes. */
public final class VelaReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){L10n.init(context,"auto");String action=intent.getAction();if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)){UpdateFiles.cleanup(context);LiteUpdateManager.get(context).cleanup();}if((context.getPackageName()+".PROGRAMME").equals(action))VelaShows.deliver(context);else if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))VelaShows.schedule(context);}
}
