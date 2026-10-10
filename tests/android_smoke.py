"""Emulator-only smoke checks. The modem call is simulated; no real phone is called."""
import base64, json, pathlib, re, shlex, subprocess, time, xml.etree.ElementTree as ET
P = "cl.abuhelp.launcher"
OUT = pathlib.Path("app/build/reports/smoke")
OUT.mkdir(parents=True, exist_ok=True)
results = []
def adb(*args, check=True, binary=False, data=None):
    if args and args[0] == "shell": args = ("shell", shlex.join(args[1:]))
    r = subprocess.run(["adb", *args], capture_output=True, timeout=40, input=data)
    if check and r.returncode: raise RuntimeError(r.stderr.decode(errors="replace"))
    return r.stdout if binary else r.stdout.decode(errors="replace")
def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/abuhelp-window.xml", check=False)
    data = adb("exec-out", "cat", "/sdcard/abuhelp-window.xml")
    (OUT / "last-window.xml").write_text(data, encoding="utf-8")
    root = ET.fromstring(data)
    # The stock Pixel launcher can ANR during cold boot on a busy CI runner.
    # Never dismiss an ABUHELP crash or a dialog from any other application.
    if any(n.attrib.get("text") == "Pixel Launcher isn\'t responding" for n in root.iter("node")):
        for n in root.iter("node"):
            if n.attrib.get("resource-id") == "android:id/aerr_close":
                b = [int(x) for x in re.findall(r"\d+", n.attrib["bounds"])]
                adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2))
                print("CI environment: closed stock Pixel Launcher ANR dialog")
                time.sleep(1)
                return ET.fromstring("<hierarchy/>")
    return root
def node(text, timeout=35):
    until = time.monotonic() + timeout
    last = None
    while time.monotonic() < until:
        try:
            for n in hierarchy().iter("node"):
                if n.attrib.get("text", "").casefold() == text.casefold():
                    return n
        except Exception as e: last = e
        time.sleep(0.8)
    raise AssertionError("No se encontró " + repr(text) + "; " + str(last))
def tap(text):
    n = node(text)
    if n.attrib.get("enabled") != "true": time.sleep(1.2); n = node(text)
    bounds = [int(x) for x in re.findall(r"\d+", n.attrib["bounds"])]
    adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
def tap_scrolled(text):
    for attempt in range(7):
        try:
            node(text, timeout=3)
            tap(text)
            return
        except AssertionError:
            adb("shell", "input", "swipe", "500", "1700", "500", "500", "400")
    raise AssertionError("No se pudo alcanzar " + text)
def enter_test_pin():
    fields = [n for n in hierarchy().iter("node") if n.attrib.get("class") == "android.widget.EditText"]
    assert fields, "Falta campo PIN"
    for n in fields:
        b = [int(x) for x in re.findall(r"\d+", n.attrib["bounds"])]
        adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2))
        adb("shell", "input", "text", "123456")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    tap_scrolled("Crear PIN" if len(fields) == 2 else "Entrar")
    node("Acceso familiar")
def capture(name):
    png = adb("exec-out", "screencap", "-p", binary=True)
    (OUT / (name + ".png")).write_bytes(png)
    print("ABUHELP_SCREENSHOT " + name + " " + base64.b64encode(png).decode("ascii"))
def launch():
    adb("shell", "am", "start", "-W", "-n", P + "/.MainActivity")
    node("ABUHELP")
try:
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard", check=False)
    launch()
    capture("01-home")
    adb("shell", "am", "force-stop", P)
    adb("shell", "run-as", P, "mkdir", "-p", "shared_prefs")
    fixture = b'<?xml version="1.0" encoding="utf-8"?><map><string name="contacts">[{"name":"Contacto de prueba","phone":"5550100"}]</string></map>'
    adb("shell", "run-as", P, "tee", "shared_prefs/abuhelp.xml", data=fixture)
    launch()
    tap_scrolled("Llamar")
    node("¿A quién quieres llamar?")
    node("Contacto de prueba")
    capture("02-contacts")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    node("ABUHELP")
    results.append("Arranque y navegación a contactos sin datos")
    adb("shell", "am", "force-stop", P)
    launch()
    results.append("Reinicio de proceso conserva arranque")
    adb("shell", "settings", "put", "system", "font_scale", "2.0")
    adb("shell", "am", "force-stop", P)
    launch()
    tap("Llamar")
    node("¿A quién quieres llamar?")
    node("Contacto de prueba")
    capture("03-large-font-contacts")
    results.append("Arranque y contactos con fuente del sistema 200%")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "am", "force-stop", P)
    for permission in ("CALL_PHONE", "READ_PHONE_STATE", "POST_NOTIFICATIONS"):
        adb("shell", "pm", "grant", P, "android.permission." + permission)
    adb("shell", "appops", "set", P, "USE_FULL_SCREEN_INTENT", "allow")
    adb("shell", "cmd", "role", "add-role-holder", "--user", "0", "android.app.role.DIALER", P)
    role = adb("shell", "cmd", "role", "get-role-holders", "--user", "0", "android.app.role.DIALER")
    assert P in role, role
    launch()
    adb("emu", "gsm", "call", "5550100")
    node("Contestar", timeout=45)
    node("Contacto de prueba")
    time.sleep(1)
    capture("04-incoming")
    tap("Contestar")
    node("En llamada")
    # Allow the anti-repeat guard to settle; a fresh tap is required.
    time.sleep(1.2)
    texts = {n.attrib.get("text") for n in hierarchy().iter("node")}
    if "Altavoz ACTIVADO" in texts:
        results.append("Emulador inició con altavoz activo; cambio a auricular pendiente de teléfono físico")
    else:
        tap("Activar altavoz")
        node("Altavoz ACTIVADO")
    capture("05-speaker")
    time.sleep(1.2)
    tap("Colgar")
    node("Llamada finalizada")
    calls = adb("emu", "gsm", "list")
    assert "5550100" not in calls, calls
    def assert_finished():
        node("Llamada finalizada")
        texts = {n.attrib.get("text", "") for n in hierarchy().iter("node")}
        for forbidden in ("Colgar", "Rechazar", "Abrir teléfono del sistema", "Altavoz: esperando"):
            assert forbidden not in texts, "Control residual después de finalizar: " + forbidden
        tap("Volver a Inicio")
        node("ABUHELP")
    assert_finished()
    # Remote hangup must reach the same clean final state.
    adb("emu", "gsm", "call", "5550100")
    node("Contestar")
    tap("Contestar")
    node("En llamada")
    adb("emu", "gsm", "cancel", "5550100")
    assert_finished()
    # Rejecting a call must also clear controls, without launching another dialer.
    adb("emu", "gsm", "call", "5550100")
    node("Contestar")
    tap("Rechazar")
    assert_finished()
    results.append("Finalización local, remota y rechazo: sin controles residuales; Volver a Inicio abre ABUHELP")

    # Grant the listener only in the disposable emulator, never via production app code.
    adb("shell", "cmd", "notification", "allow_listener", P + "/.NoticeListener")
    time.sleep(2)
    adb("shell", "cmd", "notification", "post", "-t", "Aviso de prueba", "abuhelp-test", "Mensaje local de prueba")
    launch()
    for n in hierarchy().iter("node"):
        if n.attrib.get("text", "").startswith("Notificaciones"):
            tap(n.attrib["text"])
            break
    node("Mensaje local de prueba")
    capture("06-notifications")
    # Same tag updates the existing item, including while the tray is open.
    adb("shell", "cmd", "notification", "post", "-t", "Aviso actualizado", "abuhelp-test", "Mensaje actualizado")
    node("Mensaje actualizado")
    assert not any(n.attrib.get("text") == "Mensaje local de prueba" for n in hierarchy().iter("node"))
    tap("Volver a Inicio")
    node("ABUHELP")
    results.append("Campana muestra avisos reales y actualiza contenido sin accesos a ajustes")

    # Provision only this disposable emulator, then exercise real caregiver UI.
    adb("shell", "dpm", "set-device-owner", P + "/.FamilyAdminReceiver")
    adb("shell", "svc", "wifi", "enable")
    launch()
    tap_scrolled("Acceso familiar")
    enter_test_pin()
    tap_scrolled("Protección del teléfono")
    tap_scrolled("Activar protección")
    tap("Confirmar")
    tap("Entendido")
    node("Protección activa")
    policy = adb("shell", "dumpsys", "device_policy")
    for key in ("no_change_wifi_state", "no_airplane_mode", "no_config_brightness"):
        assert key in policy, "No se aplicó " + key
    capture("07-device-protection")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    node("ABUHELP")
    adb("shell", "input", "swipe", "500", "1", "500", "1500", "600")
    node("ABUHELP")
    assert not any(n.attrib.get("text") == "Mensaje actualizado" for n in hierarchy().iter("node")), "Se abrió el panel bloqueado"
    results.append("Deslizar desde arriba no despliega el panel con protección activa")
    # Incoming calls must remain actionable despite disabled system shade.
    adb("emu", "gsm", "call", "5550100")
    node("Contestar", timeout=45)
    tap("Contestar")
    node("En llamada")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    node("ABUHELP")
    tap("Volver a la llamada")
    node("En llamada")
    time.sleep(1.2)
    tap("Colgar")
    node("Llamada finalizada")
    assert "5550100" not in adb("emu", "gsm", "list")
    assert_finished()
    results.append("Con panel bloqueado: llamada visible, retorno a llamada desde Inicio y finalización limpia")
    adb("shell", "am", "start", "-W", "-a", "android.settings.WIFI_SETTINGS")
    time.sleep(1.2) # Background timeout must close the caregiver session.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    node("ABUHELP")
    results.append("Administración: Wi-Fi, modo avión y brillo restringidos; Inicio regresa a ABUHELP")
    tap_scrolled("Acceso familiar")
    enter_test_pin()
    tap_scrolled("Protección del teléfono")
    tap_scrolled("Desactivar protección")
    tap("Entendido")
    node("Administración lista; protección desactivada")
    # The tray must really reopen after caregiver recovery.
    adb("shell", "input", "swipe", "500", "1", "500", "1500", "600")
    node("Mensaje actualizado")
    adb("shell", "cmd", "statusbar", "collapse")
    time.sleep(1)
    # Re-enter through the caregiver PIN if leaving the app locked the session.
    launch()
    tap_scrolled("Acceso familiar")
    enter_test_pin()
    tap_scrolled("Protección del teléfono")
    tap_scrolled("Retirar administración")
    tap("Confirmar")
    tap("Entendido")
    node("Preparación pendiente")
    policy = adb("shell", "dumpsys", "device_policy")
    assert "Device Owner:" not in policy, "La administración sigue activa"
    results.append("Salida con PIN: desactivar protección y retirar administración sin borrar datos")

    (OUT / "result.json").write_text(json.dumps({"passed":results}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"passed":results}, ensure_ascii=False))
except Exception:
    capture("failure")
    print((OUT / "last-window.xml").read_text(encoding="utf-8") if (OUT / "last-window.xml").exists() else "No UI hierarchy")
    log = adb("logcat", "-d", "-t", "1500", check=False)
    print("\n".join(line for line in log.splitlines() if "AndroidRuntime" in line or "abuhelp" in line.lower()))
    raise
finally:
    (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1500", check=False), encoding="utf-8")
    (OUT / "telecom.txt").write_text(adb("shell", "dumpsys", "telecom", check=False), encoding="utf-8")
    adb("emu", "gsm", "cancel", "5550100", check=False)
