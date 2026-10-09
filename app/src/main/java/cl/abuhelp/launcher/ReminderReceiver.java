package cl.abuhelp.launcher;

import android.app.*;
import android.content.*;
import android.net.Uri;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        long at = intent.getLongExtra("epoch", 0);
        if (id == null) return;
        boolean claimed = false;
        try {
            if (ReminderStore.SEEN.equals(intent.getAction())) {
                ReminderStore.acknowledge(c, id, at);
                return;
            }
            if (!ReminderStore.FIRE.equals(intent.getAction())) return;
            ReminderStore.Item x = ReminderStore.find(c, id);
            if (x == null || !ReminderStore.claim(c, x, at)) return;
            claimed = true;
            Intent open = new Intent(c, RemindersActivity.class)
                    .setData(Uri.parse("abuhelp://reminder/" + id + "/open"));
            PendingIntent content = PendingIntent.getActivity(c, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent seen = new Intent(c, ReminderReceiver.class).setAction(ReminderStore.SEEN)
                    .setData(Uri.parse("abuhelp://reminder/" + id + "/seen/" + at))
                    .putExtra("id", id).putExtra("epoch", at);
            PendingIntent seenPi = PendingIntent.getBroadcast(c, 0, seen,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification publicVersion = new Notification.Builder(c, ReminderStore.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Recordatorio ABUHELP").setContentText("Abra ABUHELP para verlo").build();
            Notification notice = new Notification.Builder(c, ReminderStore.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("Recordatorio ABUHELP").setContentText(x.label)
                    .setStyle(new Notification.BigTextStyle().bigText(x.label
                            + "\nHorario previsto: " + ReminderStore.date(at)
                            + "\nVer el aviso no confirma que se haya tomado un medicamento."))
                    .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion).setContentIntent(content).setAutoCancel(false)
                    .addAction(new Notification.Action.Builder(null, "Aviso visto", seenPi).build()).build();
            c.getSystemService(NotificationManager.class).notify(id, 1, notice);
            ReminderStore.deliverySucceeded(c, id);
        } catch (RuntimeException ignored) {
            if (claimed) ReminderStore.deliveryFailed(c, id);
        } finally { ReminderStore.rescheduleAll(c); }
    }
}
