package cl.abuhelp.launcher;
import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.*;
import android.telecom.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.List;
@SuppressWarnings("deprecation")
public final class CallActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable listener = this::render;
    private Call selected, drawn, toneCall; private int drawnState = -2;
    private long epoch, blockedUntil;
    private LinearLayout body, root;
    private ScrollView scroll; private SafeButton answer, end;
    private boolean dialpad;
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edges = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(dp(16)+edges.left, dp(16)+edges.top, dp(16)+edges.right, dp(16)+edges.bottom);
            } else v.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),
                dp(16)+insets.getSystemWindowInsetTop(), dp(16)+insets.getSystemWindowInsetRight(),
                dp(16)+insets.getSystemWindowInsetBottom());
            return insets;
        });
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        end = button("Colgar", Color.rgb(175, 15, 30));
        root.addView(end, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root); root.requestApplyInsets();
        applyIntent(getIntent());
    }
    @Override protected void onStart() {
        super.onStart(); AbuInCallService.addListener(listener); render();
    }
    @Override protected void onStop() {
        AbuInCallService.removeListener(listener);
        ui.removeCallbacksAndMessages(null); stopTone(); super.onStop();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); applyIntent(intent); render();
    }
    private void applyIntent(Intent intent) {
        List<Call> calls = AbuInCallService.snapshot();
        if (intent.hasExtra(AbuInCallService.EXTRA_ID))
            selected = AbuInCallService.byId(intent.getIntExtra(AbuInCallService.EXTRA_ID, -1));
        else if (selected == null && calls.size() == 1) selected = calls.get(0);
        dialpad = intent.getBooleanExtra(AbuInCallService.EXTRA_DIALPAD, false);
        stopTone(); block();
    }
    private void block() {
        epoch++; blockedUntil = SystemClock.uptimeMillis() + 800;
        ui.removeCallbacks(listener); ui.postDelayed(listener, 850);
    }
    private boolean valid(Call c, int state) {
        return c != null && selected == c && AbuInCallService.snapshot().contains(c)
            && AbuInCallService.state(c) == state;
    }
    private void act(Call c, int state, Runnable action) {
        if (!valid(c, state)) { render(); return; }
        block();
        try { action.run(); } catch (RuntimeException ignored) { toast("No se pudo completar. Intenta otra vez."); }
        render();
    }
    private void render() {
        if (body == null || isFinishing()) return;
        List<Call> calls = AbuInCallService.snapshot();
        if (selected != null && !calls.contains(selected)) selected = null;
        int state = AbuInCallService.state(selected);
        if (selected != drawn || state != drawnState) {
            stopTone(); block(); drawn = selected; drawnState = state;
        }
        boolean ringing = false;
        for (Call c : calls) if (AbuInCallService.state(c) == Call.STATE_RINGING) ringing = true;
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(!calls.isEmpty()); setTurnScreenOn(ringing);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            if (ringing) getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        int position = scroll.getScrollY();
        body.removeAllViews();
        AbuInCallService service = AbuInCallService.current();
        text(calls.isEmpty() ? "Sin llamadas disponibles"
            : selected == null ? "Elige la llamada" : AbuInCallService.name(this, selected));
        if (service == null) text("Servicio no disponible. Abre Teléfono del sistema.");
        else if (!service.problem.isEmpty()) text(service.problem);
        final Call c = selected;
        final int shownState = state;
        if (c != null) text(AbuInCallService.stateLabel(c));
        answer = button("Contestar", Color.rgb(0, 105, 55));
        body.addView(answer);
        // Its reserved space never becomes the disconnect button.
        answer.setVisibility(state == Call.STATE_RINGING ? View.VISIBLE : View.INVISIBLE);
        answer.setEnabled(state == Call.STATE_RINGING && SystemClock.uptimeMillis() >= blockedUntil);
        answer.setOnClickListener(v -> act(c, Call.STATE_RINGING,
            () -> c.answer(VideoProfile.STATE_AUDIO_ONLY)));
        if (calls.size() > 1) text("Hay varias llamadas. Selecciona la que quieres controlar.");
        for (Call item : calls) {
            SafeButton choose = button((item == c ? "Seleccionada: " : "Ver: ") +
                AbuInCallService.name(this, item) + "\n" + AbuInCallService.stateLabel(item),
                Color.rgb(35, 55, 85));
            body.addView(choose);
            choose.setOnClickListener(v -> {
                if (!AbuInCallService.snapshot().contains(item)) { render(); return; }
                selected = item; dialpad = false; block(); render();
            });
        }
        if (c != null && state == Call.STATE_RINGING && calls.size() > 1)
            text("Contestar puede poner en espera o terminar la otra llamada.");
        CallAudioState audio = service == null ? null : service.audioState();
        boolean speaker = audio != null && audio.getRoute() == CallAudioState.ROUTE_SPEAKER;
        SafeButton loud = button(audio == null ? "Altavoz: esperando"
            : speaker ? "Altavoz ACTIVADO" : "Activar altavoz", Color.rgb(25, 60, 110));
        loud.setEnabled(c != null && state == Call.STATE_ACTIVE && audio != null);
        body.addView(loud);
        loud.setOnClickListener(v -> act(c, Call.STATE_ACTIVE, () -> {
            AbuInCallService s = AbuInCallService.current();
            CallAudioState actual = s == null ? null : s.audioState();
            if (actual == null) { toast("Audio no disponible."); return; }
            int route = actual.getRoute() == CallAudioState.ROUTE_SPEAKER
                ? CallAudioState.ROUTE_WIRED_OR_EARPIECE : CallAudioState.ROUTE_SPEAKER;
            if ((actual.getSupportedRouteMask() & route) == 0) { toast("Salida no disponible."); return; }
            s.setAudioRoute(route);
        }));
        if (c != null && (state == Call.STATE_HOLDING ||
            state == Call.STATE_ACTIVE && c.getDetails().can(Call.Details.CAPABILITY_HOLD))) {
            SafeButton hold = button(state == Call.STATE_HOLDING ? "Retomar llamada" : "Poner en espera",
                Color.rgb(60, 60, 60));
            body.addView(hold);
            hold.setOnClickListener(v -> act(c, shownState, () -> {
                if (shownState == Call.STATE_HOLDING) c.unhold(); else c.hold();
            }));
        }
        if (c != null && state == Call.STATE_SELECT_PHONE_ACCOUNT && service != null) {
            try {
                TelecomManager tm = getSystemService(TelecomManager.class);
                for (PhoneAccountHandle account : tm.getCallCapablePhoneAccounts()) {
                    PhoneAccount detail = tm.getPhoneAccount(account);
                    SafeButton sim = button("Usar " + (detail == null ? "SIM" : detail.getLabel()),
                        Color.rgb(50, 50, 85));
                    body.addView(sim);
                    sim.setOnClickListener(v -> act(c, shownState, () -> c.phoneAccountSelected(account, false)));
                }
            } catch (SecurityException ignored) { text("Cuidador: configura la SIM predeterminada."); }
        }
        if (c != null && state == Call.STATE_ACTIVE) {
            SafeButton toggle = button(dialpad ? "Ocultar teclado" : "Mostrar teclado", Color.DKGRAY);
            body.addView(toggle);
            toggle.setOnClickListener(v -> { dialpad = !dialpad; block(); render(); });
            if (dialpad) {
                String keys = "123456789*0#";
                for (int row = 0; row < 4; row++) {
                    LinearLayout line = new LinearLayout(this);
                    body.addView(line);
                    for (int col = 0; col < 3; col++) {
                        final char key = keys.charAt(row * 3 + col);
                        SafeButton digit = button(String.valueOf(key), Color.DKGRAY);
                        line.addView(digit, new LinearLayout.LayoutParams(0, -2, 1));
                        digit.setOnClickListener(v -> act(c, Call.STATE_ACTIVE, () -> {
                            stopTone(); toneCall = c; c.playDtmfTone(key); ui.postDelayed(this::stopTone, 180);
                        }));
                    }
                }
            }
        }
        Button back = button("Volver al inicio", Color.DKGRAY);
        body.addView(back); back.setOnClickListener(v -> finish());
        if (service == null) {
            Button fallback = button("Abrir teléfono del sistema", Color.DKGRAY);
            body.addView(fallback); fallback.setOnClickListener(v -> {
                try {
                    TelecomManager tm = getSystemService(TelecomManager.class);
                    String pkg = Build.VERSION.SDK_INT >= 29 ? tm.getSystemDialerPackage() : null;
                    if (pkg == null || pkg.equals(getPackageName())) { toast("Consulta al cuidador."); return; }
                    startActivity(new Intent(Intent.ACTION_DIAL).setPackage(pkg));
                } catch (RuntimeException ignored) { toast("No se pudo abrir. Consulta al cuidador."); }
            });
        }
        end.setText(state == Call.STATE_RINGING ? "Rechazar" : "Colgar");
        end.setEnabled(c != null && state != Call.STATE_DISCONNECTING &&
            SystemClock.uptimeMillis() >= blockedUntil);
        end.setOnClickListener(v -> act(c, shownState, () -> {
            if (shownState == Call.STATE_RINGING) c.reject(false, null); else c.disconnect();
        }));
        scroll.post(() -> scroll.scrollTo(0, position));
    }
    private void stopTone() {
        if (toneCall != null) try { toneCall.stopDtmfTone(); } catch (RuntimeException ignored) { }
        toneCall = null;
    }
    private void text(String value) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(30);
        t.setTextColor(Color.WHITE); t.setPadding(0, dp(8), 0, dp(12)); body.addView(t);
    }
    private SafeButton button(String title, int color) {
        SafeButton b = new SafeButton(); b.setText(title); b.setTextSize(30); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setMinHeight(dp(96)); b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setBackgroundTintList(ColorStateList.valueOf(color)); return b;
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private final class SafeButton extends Button {
        private Call downCall;
        private long downEpoch;
        private boolean armed, downAllowed;
        SafeButton() { super(CallActivity.this); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downCall = selected; downEpoch = epoch; armed = true;
                downAllowed = SystemClock.uptimeMillis() >= blockedUntil;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) downEpoch = -1;
            if (e.getActionMasked() == MotionEvent.ACTION_UP && armed &&
                (!downAllowed || downCall != selected || downEpoch != epoch || SystemClock.uptimeMillis() < blockedUntil)) {
                MotionEvent cancel = MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.onTouchEvent(cancel); cancel.recycle(); armed = false; return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) armed = false;
            return super.onTouchEvent(e);
        }
        @Override public boolean performClick() {
            boolean valid = !armed || downAllowed && downCall == selected && downEpoch == epoch;
            armed = false;
            return isEnabled() && valid && SystemClock.uptimeMillis() >= blockedUntil && super.performClick();
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            if (action == AccessibilityNodeInfo.ACTION_CLICK) armed = false;
            return super.performAccessibilityAction(action, args);
        }
        @Override public boolean onKeyDown(int code, KeyEvent event) {
            armed = false; return super.onKeyDown(code, event);
        }
    }
}
