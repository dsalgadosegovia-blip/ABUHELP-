package cl.abuhelp.launcher;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(a))
        {
            PendingResult pending = goAsync();
            Context app = c.getApplicationContext();
            new Thread(() -> {
                try { ReminderStore.rescheduleAll(app); }
                finally { pending.finish(); }
            }, "abuhelp-reminders-recovery").start();
        }
    }
}
