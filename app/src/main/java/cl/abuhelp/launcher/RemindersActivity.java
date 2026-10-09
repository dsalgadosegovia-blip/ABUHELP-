package cl.abuhelp.launcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public final class RemindersActivity extends Activity {
    private LinearLayout rows, form;
    private TextView state, draftView;
    private EditText label, time;
    private CheckBox daily;
    private Button auth, permissions, save;
    private String editingId;
    private boolean unlocked;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static Intent pendingDraft;
    private static long pendingUntil;
    private final Runnable gateWatch = new Runnable() {
        @Override public void run() {
            if (unlocked != CaregiverGate.isUnlocked()) refresh();
            handler.postDelayed(this, 1000);
        }
    };
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size);
        v.setPadding(0, dp(8), 0, dp(8)); return v;
    }
    private Button button(String title, View.OnClickListener click) {
        Button b = new Button(this); b.setText(title); b.setTextSize(24); b.setMinHeight(dp(72));
        b.setOnClickListener(click); return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(root); setContentView(scroll);
        root.addView(text("Recordatorios", 32));
        root.addView(button("Volver", v -> finish()));
        root.addView(text("Horarios de Santiago de Chile. Los avisos no confirman una toma.", 20));
        state = text("", 21); root.addView(state);
        auth = button("Entrar como cuidador", v -> enterCaregiver()); root.addView(auth);
        permissions = button("Revisar permisos de avisos", v -> permissions()); root.addView(permissions);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); root.addView(rows);
        form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); root.addView(form);
        draftView = text("", 20); form.addView(draftView);
        form.addView(text("Nombre del recordatorio", 23));
        label = new EditText(this); label.setTextSize(25); label.setSingleLine(true);
        label.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        form.addView(label);
        form.addView(text("Hora en formato 24 horas: HH:mm", 23));
        time = new EditText(this); time.setTextSize(26); time.setSingleLine(true);
        time.setHint("08:30"); time.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        form.addView(time);
        daily = new CheckBox(this); daily.setText("Todos los días"); daily.setTextSize(24);
        daily.setMinHeight(dp(72)); form.addView(daily);
        save = button("Revisar y confirmar", v -> preview()); form.addView(save);
        form.addView(button("Limpiar borrador", v -> resetForm()));
        if (saved != null) {
            editingId = saved.getString("editingId"); label.setText(saved.getString("label", ""));
            time.setText(saved.getString("time", "")); daily.setChecked(saved.getBoolean("daily"));
            draftView.setText(saved.getString("draft", ""));
        } else seedDraft();
    }
    private void seedDraft() {
        Intent source = getIntent();
        boolean hasDraft = source.hasExtra("draft") || source.hasExtra("label");
        if (hasDraft && !CaregiverGate.isUnlocked()) {
            pendingDraft = new Intent(source); pendingUntil = SystemClock.elapsedRealtime() + 300000;
        } else if (!hasDraft && CaregiverGate.isUnlocked() && pendingDraft != null
                && SystemClock.elapsedRealtime() < pendingUntil) source = pendingDraft;
        if (CaregiverGate.isUnlocked()) pendingDraft = null;
        String raw = source.getStringExtra("draft");
        if (raw != null) {
            raw = raw.substring(0, Math.min(raw.length(), 300));
            draftView.setText("Borrador de voz: " + raw + "\nRevise los datos antes de confirmar.");
            ReminderDraft parsed = ReminderDraft.parse(raw);
            if (!parsed.label.isEmpty()) label.setText(parsed.label);
            if (parsed.hour >= 0) time.setText(String.format(Locale.ROOT,"%02d:%02d",parsed.hour,parsed.minute));
            daily.setChecked(parsed.daily);
            if (!parsed.explanation.isEmpty()) draftView.append("\n" + parsed.explanation);
        }
        if (source.hasExtra("label")) label.setText(source.getStringExtra("label"));
        if (source.hasExtra("hour") && source.hasExtra("minute")) {
            int h = source.getIntExtra("hour", -1), m = source.getIntExtra("minute", -1);
            if (h >= 0 && h < 24 && m >= 0 && m < 60)
                time.setText(String.format(Locale.ROOT, "%02d:%02d", h, m));
        }
    }
    @Override protected void onResume() {
        super.onResume(); ReminderStore.rescheduleAll(this); refresh();
        handler.removeCallbacks(gateWatch); handler.postDelayed(gateWatch, 1000);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(gateWatch); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("editingId", editingId); out.putString("label", label.getText().toString());
        out.putString("time", time.getText().toString()); out.putBoolean("daily", daily.isChecked());
        out.putString("draft", draftView.getText().toString()); super.onSaveInstanceState(out);
    }
    private void enterCaregiver() {
        startActivity(new Intent(this, CaregiverActivity.class).putExtra("destination", "reminders"));
    }
    private boolean allowed() {
        if (CaregiverGate.isUnlocked()) return true;
        Toast.makeText(this, "Se requiere modo cuidador", Toast.LENGTH_LONG).show();
        refresh(); return false;
    }
    private void refresh() {
        unlocked = CaregiverGate.isUnlocked();
        auth.setVisibility(unlocked ? View.GONE : View.VISIBLE);
        form.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        permissions.setVisibility(unlocked ? View.VISIBLE : View.GONE);
        rows.removeAllViews();
        try {
            String issue = ReminderStore.problem(this);
            state.setText(issue == null ? "Avisos habilitados. Revise también sonido y batería."
                    : "Avisos pendientes: " + issue);
            for (ReminderStore.Item x : ReminderStore.list(this)) {
                rows.addView(text(x.label + "\n" + String.format(Locale.ROOT, "%02d:%02d", x.hour, x.minute)
                        + (x.daily ? " · Todos los días" : " · Una vez")
                        + "\n" + ReminderStore.status(this, x), 24));
                if (unlocked) {
                    rows.addView(button("Editar este recordatorio", v -> {
                        if (!allowed()) return;
                        editingId = x.id; label.setText(x.label);
                        time.setText(String.format(Locale.ROOT, "%02d:%02d", x.hour, x.minute));
                        daily.setChecked(x.daily); draftView.setText("Edición: requiere nueva confirmación");
                        label.requestFocus();
                    }));
                    rows.addView(button("Borrar este recordatorio", v -> {
                        if (!allowed()) return;
                        new AlertDialog.Builder(this).setTitle("Borrar recordatorio").setMessage(x.label)
                                .setNegativeButton("Cancelar", null).setPositiveButton("Borrar", (d, which) -> {
                                    if (!allowed()) return;
                                    try { ReminderStore.delete(this, x.id); if (x.id.equals(editingId)) resetForm(); refresh(); }
                                    catch (RuntimeException e) { error(e.getMessage()); }
                                }).show();
                    }));
                }
            }
        } catch (RuntimeException e) { state.setText("No se pudieron leer los avisos. Los datos no se borraron."); }
    }
    private void resetForm() {
        editingId = null; label.setText(""); time.setText(""); daily.setChecked(false); draftView.setText("");
        pendingDraft = null;
    }
    private void error(String message) {
        new AlertDialog.Builder(this).setTitle("Revise el recordatorio")
                .setMessage(message == null ? "No se pudo completar la operación" : message)
                .setPositiveButton("Entendido", null).show();
    }
    private void preview() {
        if (!allowed()) return;
        try {
            String input = time.getText().toString().trim();
            if (!input.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))
                throw new IllegalArgumentException("Escriba la hora completa, por ejemplo 08:30 o 20:30.");
            int h = Integer.parseInt(input.substring(0, 2)), m = Integer.parseInt(input.substring(3, 5));
            boolean everyDay = daily.isChecked();
            long now = System.currentTimeMillis();
            String id = editingId == null ? UUID.randomUUID().toString() : editingId;
            ReminderStore.Item x = new ReminderStore.Item(id, label.getText().toString(), h, m,
                    everyDay, everyDay ? 0 : ReminderStore.futureWallTime(h, m, now));
            long at = ReminderStore.next(this, x, now);
            String summary = x.label + "\nPrimera fecha: " + ReminderStore.date(at)
                    + "\nZona: Santiago de Chile\n" + (everyDay ? "Todos los días" : "Una sola vez")
                    + "\n\nSe guardará un aviso; no se modifica ninguna dosis.";
            new AlertDialog.Builder(this).setTitle("Confirmar recordatorio").setMessage(summary)
                    .setNegativeButton("Corregir", null).setPositiveButton("Confirmar", (d, which) -> {
                        if (!allowed()) return;
                        try {
                            if (at <= System.currentTimeMillis()) { error("La hora pasó. Revise y confirme una fecha futura."); return; }
                            if (editingId != null && ReminderStore.find(this, id) == null) {
                                error("Este recordatorio ya no existe. Cree uno nuevo."); return;
                            }
                            ReminderStore.put(this, x);
                            Toast.makeText(this, "Guardado. " + ReminderStore.status(this, x), Toast.LENGTH_LONG).show();
                            resetForm(); refresh();
                        } catch (RuntimeException e) { error(e.getMessage()); }
                    }).show();
        } catch (RuntimeException e) { error(e.getMessage()); }
    }
    private void permissions() {
        if (!allowed()) return;
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                SharedPreferences ui = getSharedPreferences("abuhelp_ui", MODE_PRIVATE);
                if (!ui.getBoolean("notificationAsked", false)) {
                    ui.edit().putBoolean("notificationAsked", true).commit();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
                } else {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName()))); return;
            }
            startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderStore.CHANNEL));
        } catch (RuntimeException e) { error("Abra Ajustes y revise los permisos de ABUHELP."); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] names, int[] grants) {
        super.onRequestPermissionsResult(requestCode, names, grants);
        ReminderStore.rescheduleAll(this); refresh();
    }
}
