package cl.abuhelp.launcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ReminderStore {
    public static final String CHANNEL = "abuhelp_reminders_v1";
    public static final String FIRE = "cl.abuhelp.launcher.REMINDER_FIRE";
    public static final String SEEN = "cl.abuhelp.launcher.REMINDER_SEEN";
    public static final ZoneId ZONE = ZoneId.of("America/Santiago");
    private ReminderStore() {}
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("abuhelp_reminders", Context.MODE_PRIVATE);
    }
    public static final class Item {
        public final String id, label;
        public final int hour, minute;
        public final boolean daily;
        public final long epoch;
        public Item(String id, String label, int hour, int minute, boolean daily, long epoch) {
            UUID.fromString(id);
            if (label == null || label.trim().isEmpty() || label.trim().length() > 100
                    || hour < 0 || hour > 23 || minute < 0 || minute > 59 || (!daily && epoch <= 0))
                throw new IllegalArgumentException("Nombre u horario inválido");
            this.id = id; this.label = label.trim(); this.hour = hour; this.minute = minute;
            this.daily = daily; this.epoch = epoch;
        }
        JSONObject json() throws JSONException {
            JSONObject j = new JSONObject().put("id", id).put("label", label).put("hour", hour)
                    .put("minute", minute).put("daily", daily);
            if (!daily) j.put("epoch", epoch);
            return j;
        }
    }
    public static synchronized List<Item> list(Context c) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
            List<Item> rows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (int n = 0; n < a.length(); n++) {
                JSONObject j = a.getJSONObject(n);
                Item x = new Item(j.getString("id"), j.getString("label"), j.getInt("hour"),
                        j.getInt("minute"), j.getBoolean("daily"), j.optLong("epoch", 0));
                if (!ids.add(x.id)) throw new IllegalArgumentException("Identificador duplicado");
                rows.add(x);
            }
            return rows;
        } catch (JSONException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pueden leer los recordatorios. No se borraron los datos.", e);
        }
    }
    private static void write(Context c, List<Item> rows) {
        try {
            JSONArray a = new JSONArray();
            for (Item x : rows) a.put(x.json());
            if (!prefs(c).edit().putString("items", a.toString()).commit())
                throw new IllegalStateException("No se pudieron guardar los datos");
        } catch (JSONException e) { throw new IllegalStateException("No se pudieron guardar los datos", e); }
    }
    public static synchronized void put(Context c, Item item) {
        if (!CaregiverGate.isUnlocked()) throw new SecurityException("Se requiere modo cuidador");
        List<Item> rows = list(c);
        rows.removeIf(x -> x.id.equals(item.id));
        if (rows.size() >= 50) throw new IllegalStateException("Se permiten hasta 50 recordatorios");
        rows.add(item); write(c, rows);
        cancel(c, item.id);
        prefs(c).edit().remove("next." + item.id).remove("error." + item.id).remove("deliveryError." + item.id).commit();
        rescheduleAll(c);
    }
    public static synchronized void delete(Context c, String id) {
        if (!CaregiverGate.isUnlocked()) throw new SecurityException("Se requiere modo cuidador");
        List<Item> rows = list(c);
        rows.removeIf(x -> x.id.equals(id)); write(c, rows);
        cancel(c, id);
        c.getSystemService(NotificationManager.class).cancel(id, 1);
        SharedPreferences.Editor e = prefs(c).edit();
        for (String key : new String[]{"next.", "error.", "deliveryError.", "fired.", "day.", "seen."}) e.remove(key + id);
        e.commit();
    }
    public static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Recordatorios ABUHELP",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Avisos de horarios definidos por la familia");
        ch.enableVibration(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    public static String problem(Context c) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return "Falta permitir las notificaciones";
        if (!nm.areNotificationsEnabled()) return "Las notificaciones están desactivadas";
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL);
        if (ch == null || ch.getImportance() == NotificationManager.IMPORTANCE_NONE)
            return "El canal de recordatorios está desactivado";
        if (Build.VERSION.SDK_INT >= 31 && !c.getSystemService(AlarmManager.class).canScheduleExactAlarms())
            return "Falta permitir alarmas y recordatorios";
        return null;
    }
    private static Intent fireIntent(Context c, String id) {
        return new Intent(c, ReminderReceiver.class).setAction(FIRE)
                .setData(Uri.parse("abuhelp://reminder/" + id + "/fire"));
    }
    private static void cancel(Context c, String id) {
        PendingIntent p = PendingIntent.getBroadcast(c, 0, fireIntent(c, id),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (p != null) c.getSystemService(AlarmManager.class).cancel(p);
    }
    public static long futureWallTime(int h, int m, long now) {
        LocalDate d = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
        ZonedDateTime z = ZonedDateTime.of(d, LocalTime.of(h, m), ZONE).withEarlierOffsetAtOverlap();
        if (z.toInstant().toEpochMilli() <= now)
            z = ZonedDateTime.of(d.plusDays(1), LocalTime.of(h, m), ZONE).withEarlierOffsetAtOverlap();
        return z.toInstant().toEpochMilli();
    }
    public static long next(Context c, Item x, long now) {
        if (!x.daily) return x.epoch > now ? x.epoch : 0;
        LocalDate d = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate();
        long fired = prefs(c).getLong("fired." + x.id, 0);
        if (fired > now) d = Instant.ofEpochMilli(fired).atZone(ZONE).toLocalDate();
        String firedDay = prefs(c).getString("day." + x.id, "");
        for (int n = 0; n < 3; n++, d = d.plusDays(1)) {
            long at = ZonedDateTime.of(d, LocalTime.of(x.hour, x.minute), ZONE)
                    .withEarlierOffsetAtOverlap().toInstant().toEpochMilli();
            if (at > now && !d.toString().equals(firedDay)) return at;
        }
        throw new IllegalStateException("No se pudo calcular la próxima fecha");
    }
    public static synchronized void rescheduleAll(Context context) {
        Context c = context.getApplicationContext();
        try {
            String issue = problem(c);
            long now = System.currentTimeMillis();
            for (Item x : list(c)) {
                long at = next(c, x, now);
                long previous = prefs(c).getLong("next." + x.id, 0);
                // Preserve an alarm already dispatched while the UI resumes. After reboot
                // the PendingIntent does not exist, so old reminders are not replayed.
                if (issue == null && previous > 0 && previous <= now && now - previous <= 300000
                        && prefs(c).getLong("fired." + x.id, 0) < previous
                        && PendingIntent.getBroadcast(c, 0, fireIntent(c, x.id),
                            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null)
                    at = previous;
                if (issue != null || at == 0) {
                    cancel(c, x.id);
                    prefs(c).edit().remove("next." + x.id).commit();
                    continue;
                }
                cancel(c, x.id);
                try {
                    if (!prefs(c).edit().putLong("next." + x.id, at).remove("error." + x.id).commit())
                        throw new IllegalStateException("No se pudo persistir la programación");
                    PendingIntent p = PendingIntent.getBroadcast(c, 0,
                            fireIntent(c, x.id).putExtra("id", x.id).putExtra("epoch", at),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    c.getSystemService(AlarmManager.class).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                } catch (RuntimeException e) {
                    cancel(c, x.id);
                    prefs(c).edit().remove("next." + x.id)
                            .putString("error." + x.id, "No se pudo activar el aviso").commit();
                }
            }
            prefs(c).edit().remove("globalError").commit();
        } catch (RuntimeException e) {
            prefs(c).edit().putString("globalError", "No se pudieron recuperar los avisos").commit();
        }
    }
    public static synchronized Item find(Context c, String id) {
        for (Item x : list(c)) if (x.id.equals(id)) return x;
        return null;
    }
    public static synchronized boolean claim(Context c, Item x, long at) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        if (at <= 0 || at > now || p.getLong("next." + x.id, 0) != at
                || p.getLong("fired." + x.id, 0) >= at || problem(c) != null) return false;
        String day = Instant.ofEpochMilli(at).atZone(ZONE).toLocalDate().toString();
        if (x.daily && (day.equals(p.getString("day." + x.id, ""))
                || !day.equals(Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().toString()))) return false;
        return p.edit().putLong("fired." + x.id, at).putString("day." + x.id, day)
                .remove("next." + x.id).commit();
    }
    public static synchronized void acknowledge(Context c, String id, long at) {
        if (find(c, id) == null || prefs(c).getLong("fired." + id, 0) != at || at <= 0) return;
        if (prefs(c).edit().putLong("seen." + id, at).commit())
            c.getSystemService(NotificationManager.class).cancel(id, 1);
    }
    public static void deliveryFailed(Context c, String id) {
        prefs(c).edit().putString("deliveryError." + id, "El último aviso no se pudo mostrar").commit();
    }
    public static void deliverySucceeded(Context c, String id) {
        prefs(c).edit().remove("deliveryError." + id).commit();
    }
    public static String date(long at) {
        return DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", new Locale("es", "CL"))
                .format(Instant.ofEpochMilli(at).atZone(ZONE));
    }
    public static String status(Context c, Item x) {
        String issue = problem(c);
        if (issue != null) return "Guardado; " + issue.toLowerCase(new Locale("es", "CL"));
        String err = prefs(c).getString("error." + x.id, null);
        if (err != null) return err;
        long at = prefs(c).getLong("next." + x.id, 0);
        String delivery = prefs(c).getString("deliveryError." + x.id, null);
        if (at > System.currentTimeMillis()) return (delivery == null ? "" : delivery + "\n") + "Programado: " + date(at);
        if (delivery != null) return delivery;
        long fired = prefs(c).getLong("fired." + x.id, 0);
        if (fired > 0) return prefs(c).getLong("seen." + x.id, 0) == fired ? "Aviso visto" : "Aviso emitido";
        return "Horario vencido; no se reproducirá un aviso pasado";
    }
}
