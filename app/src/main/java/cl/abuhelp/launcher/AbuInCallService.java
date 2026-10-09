package cl.abuhelp.launcher;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.telecom.*;
import android.telephony.PhoneNumberUtils;
import org.json.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
@SuppressWarnings("deprecation")
public final class AbuInCallService extends InCallService {
    public static final String EXTRA_ID = "call_id", EXTRA_DIALPAD = "dialpad";
    public static final String CHANNEL = "abuhelp_calls_v1";
    private static final int NOTICE = 4001;
    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();
    private static AbuInCallService instance; private static int nextId = 1;
    private final LinkedHashMap<Call,Integer> calls = new LinkedHashMap<>();
    private final Map<Call,Call.Callback> callbacks = new HashMap<>();
    private final Set<Integer> silenced = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager notifications; private CallAudioState audio;
    private Ringtone ringtone;
    private int ringingId = -1, lastNoticeCall = -1;
    private boolean foreground;
    public String problem = "";
    // All access occurs on the main thread; no public/exported broadcast is used.
    public static AbuInCallService current() { return instance; }
    public static void addListener(Runnable r) { listeners.add(r); }
    public static void removeListener(Runnable r) { listeners.remove(r); }
    public static List<Call> snapshot() {
        List<Call> result = new ArrayList<>();
        if (instance != null) for (Call c : instance.calls.keySet())
            if (state(c) != Call.STATE_DISCONNECTED) result.add(c);
        return result;
    }
    public static boolean hasCalls() { return !snapshot().isEmpty(); }
    public static int state(Call c) { return c == null ? -1 : c.getState(); }
    public static int id(Call c) {
        Integer n = instance == null ? null : instance.calls.get(c);
        return n == null ? -1 : n;
    }
    public static Call byId(int n) {
        for (Call c : snapshot()) if (id(c) == n) return c;
        return null;
    }
    public static boolean notificationsReady(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL);
        return nm.areNotificationsEnabled() &&
            (channel == null || channel.getImportance() >= NotificationManager.IMPORTANCE_HIGH);
    }
    public static boolean fullscreenReady(Context ctx) {
        return Build.VERSION.SDK_INT < 34 ||
            ctx.getSystemService(NotificationManager.class).canUseFullScreenIntent();
    }
    public static String name(Context ctx, Call c) {
        Call.Details d = c == null ? null : c.getDetails();
        if (d == null) return "Llamada";
        if (d.hasProperty(Call.Details.PROPERTY_CONFERENCE)) return "Llamada grupal";
        String number = d.getHandlePresentation() == TelecomManager.PRESENTATION_ALLOWED &&
            d.getHandle() != null ? d.getHandle().getSchemeSpecificPart() : "";
        if (!number.isEmpty()) {
            String normalized = PhoneNumberUtils.normalizeNumber(number);
            try {
                JSONArray a = new JSONArray(ctx.getSharedPreferences("abuhelp", MODE_PRIVATE)
                    .getString("contacts", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject item = a.getJSONObject(i);
                    String saved = PhoneNumberUtils.normalizeNumber(item.optString("phone"));
                    if (!saved.isEmpty() && PhoneNumberUtils.compare(item.optString("phone"),number) && !item.optString("name").isEmpty())
                        return item.optString("name");
                }
            } catch (JSONException ignored) { }
        }
        String display = d.getCallerDisplayNamePresentation() == TelecomManager.PRESENTATION_ALLOWED
            ? d.getCallerDisplayName() : null;
        return display != null && !display.isEmpty() ? display
            : number.isEmpty() ? "Número privado o desconocido" : number;
    }
    public static String stateLabel(Call c) {
        switch (state(c)) {
            case Call.STATE_RINGING: return "Llamada entrante";
            case Call.STATE_ACTIVE: return "En llamada";
            case Call.STATE_HOLDING: return "En espera";
            case Call.STATE_DIALING: case Call.STATE_CONNECTING: return "Llamando";
            case Call.STATE_SELECT_PHONE_ACCOUNT: return "Elige una SIM";
            case Call.STATE_DISCONNECTING: return "Finalizando";
            case Call.STATE_DISCONNECTED: return "Llamada terminada";
            default: return "Conectando";
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Llamadas",
            NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null); // The service owns the ringtone, without double audio.
        channel.enableVibration(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notifications.createNotificationChannel(channel);
        fire();
    }
    @Override public void onCallAdded(Call c) {
        if (calls.containsKey(c)) return;
        calls.put(c, nextId++);
        Call.Callback callback = new Call.Callback() {
            @Override public void onStateChanged(Call call, int state) { refresh(); }
            @Override public void onDetailsChanged(Call call, Call.Details details) { refresh(); }
            @Override public void onChildrenChanged(Call call, List<Call> children) { refresh(); }
            @Override public void onParentChanged(Call call, Call parent) { refresh(); }
            @Override public void onCallDestroyed(Call call) { remove(call); }
        };
        callbacks.put(c, callback);
        c.registerCallback(callback, main);
        refresh();
        if (state(c) != Call.STATE_RINGING || !notificationsReady(this)) bringToFront(c, false);
    }
    @Override public void onCallRemoved(Call c) { remove(c); }
    private void remove(Call c) {
        Call.Callback cb = callbacks.remove(c);
        if (cb != null) c.unregisterCallback(cb);
        Integer n = calls.remove(c);
        if (n != null) silenced.remove(n);
        refresh();
    }
    @Override public void onCallAudioStateChanged(CallAudioState a) { audio = a; fire(); }
    public CallAudioState audioState() {
        return audio != null ? audio : getCallAudioState();
    }
    @Override public void onSilenceRinger() {
        for (Call c : snapshot()) if (state(c) == Call.STATE_RINGING) silenced.add(id(c));
        stopRingtone();
    }
    private final Runnable ringTick = new Runnable() {
        @Override public void run() {
            if (ringtone == null) return;
            try { if (!ringtone.isPlaying()) ringtone.play(); }
            catch (RuntimeException ignored) { stopRingtone(); return; }
            main.postDelayed(this, 2000);
        }
    };
    private void updateRingtone(Call incoming) {
        boolean ongoing = false;
        for (Call c : snapshot()) if (state(c) == Call.STATE_ACTIVE) ongoing = true;
        AudioManager am = getSystemService(AudioManager.class);
        int wanted = incoming == null || ongoing || silenced.contains(id(incoming)) ||
            am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL ||
            notifications.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL
            ? -1 : id(incoming);
        if (wanted == ringingId) return;
        stopRingtone();
        if (wanted < 0) return;
        try {
            ringtone = RingtoneManager.getRingtone(this,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone == null) return;
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
            ringingId = wanted;
            ringtone.play();
            main.postDelayed(ringTick, 2000);
        } catch (RuntimeException ignored) { stopRingtone(); }
    }
    private void stopRingtone() {
        main.removeCallbacks(ringTick);
        if (ringtone != null) try { ringtone.stop(); } catch (RuntimeException ignored) { }
        ringtone = null; ringingId = -1;
    }
    private Intent screenIntent(Call c, boolean dialpad) {
        return new Intent(this, CallActivity.class)
            .setData(Uri.parse("abuhelp://call/" + id(c)))
            .putExtra(EXTRA_ID, id(c)).putExtra(EXTRA_DIALPAD, dialpad)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
    private void bringToFront(Call c, boolean dialpad) {
        try { startActivity(screenIntent(c, dialpad)); }
        catch (RuntimeException ignored) { problem = "Abre la llamada desde la notificación."; fire(); }
    }
    @Override public void onBringToForeground(boolean dialpad) {
        List<Call> active = snapshot();
        bringToFront(active.size() == 1 ? active.get(0) : null, dialpad);
    }
    private void refresh() {
        if (instance != this) return;
        List<Call> live = snapshot();
        Call incoming = null;
        for (Call c : live) if (state(c) == Call.STATE_RINGING) { incoming = c; break; }
        updateRingtone(incoming);
        if (live.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foreground = false; lastNoticeCall = -1;
            notifications.cancel(NOTICE); fire(); return;
        }
        Call focus = incoming != null ? incoming : live.get(0);
        boolean allowed = notificationsReady(this);
        problem = !allowed ? "Cuidador: habilita avisos prioritarios de llamadas."
            : !fullscreenReady(this) ? "Cuidador: permite avisos a pantalla completa." : "";
        PendingIntent open = PendingIntent.getActivity(this, id(focus), screenIntent(focus, false),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(incoming != null ? "Llamada entrante" : "ABUHELP: llamada")
            .setContentText(name(this, focus)).setContentIntent(open)
            .setCategory(Notification.CATEGORY_CALL).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(true).setOnlyAlertOnce(lastNoticeCall == id(focus))
            .addAction(new Notification.Action.Builder(null, "Abrir controles", open).build());
        if (Build.VERSION.SDK_INT >= 31)
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (incoming != null && allowed && fullscreenReady(this))
            builder.setFullScreenIntent(open, true);
        Notification notice = builder.build();
        try {
            if (!foreground) {
                if (Build.VERSION.SDK_INT >= 29)
                    startForeground(NOTICE, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL);
                else startForeground(NOTICE, notice);
                foreground = true;
            } else if (allowed) notifications.notify(NOTICE, notice);
            lastNoticeCall = id(focus);
        } catch (RuntimeException ignored) {
            problem = "Cuidador: revisa permisos y avisos de llamadas.";
            if (allowed) try { notifications.notify(NOTICE, notice); }
                catch (RuntimeException alsoIgnored) { }
        }
        fire();
    }
    private static void fire() { for (Runnable r : listeners) try { r.run(); } catch (RuntimeException ignored) { } }
    @Override public void onDestroy() {
        stopRingtone(); main.removeCallbacksAndMessages(null);
        for (Map.Entry<Call,Call.Callback> entry : callbacks.entrySet())
            entry.getKey().unregisterCallback(entry.getValue());
        callbacks.clear(); calls.clear(); silenced.clear();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (notifications != null) notifications.cancel(NOTICE);
        if (instance == this) instance = null;
        fire(); super.onDestroy();
    }
}
