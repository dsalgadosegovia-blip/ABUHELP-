"""Emulator-only smoke checks. The modem call is simulated; no real phone is called."""
import json, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
P = "cl.abuhelp.launcher"
OUT = pathlib.Path("app/build/reports/smoke")
OUT.mkdir(parents=True, exist_ok=True)
results = []
def adb(*args, check=True, binary=False, data=None):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=40, input=data)
    if check and r.returncode: raise RuntimeError(r.stderr.decode(errors="replace"))
    return r.stdout if binary else r.stdout.decode(errors="replace")
def hierarchy():
    adb("shell", "uiautomator", "dump", "/sdcard/abuhelp-window.xml", check=False)
    data = adb("exec-out", "cat", "/sdcard/abuhelp-window.xml")
    (OUT / "last-window.xml").write_text(data, encoding="utf-8")
    return ET.fromstring(data)
def node(text, timeout=35):
    until = time.monotonic() + timeout
    last = None
    while time.monotonic() < until:
        try:
            for n in hierarchy().iter("node"):
                if n.attrib.get("text") == text:
                    return n
        except Exception as e: last = e
        time.sleep(0.8)
    raise AssertionError("No se encontró " + repr(text) + "; " + str(last))
def tap(text):
    n = node(text)
    if n.attrib.get("enabled") != "true": time.sleep(1.2); n = node(text)
    bounds = [int(x) for x in re.findall(r"\d+", n.attrib["bounds"])]
    adb("shell", "input", "tap", str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
def capture(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
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
    tap("Llamar")
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
    tap("Activar altavoz")
    node("Altavoz ACTIVADO")
    capture("05-speaker")
    time.sleep(1.2)
    tap("Colgar")
    node("Sin llamadas disponibles")
    calls = adb("emu", "gsm", "list")
    assert "5550100" not in calls, calls
    results.append("Llamada entrante simulada: contestar, altavoz y colgar")
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
