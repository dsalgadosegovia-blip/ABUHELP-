# Preparar el Xiaomi nuevo para ABUHELP

Esta guía no contiene ningún comando de borrado. Requiere un teléfono nuevo o sin cuentas y un familiar presente. Si ya contiene datos, respáldalos y detente antes de cualquier restablecimiento.

## Antes de entregar el teléfono

1. Instala la APK beta verificada y abre ABUHELP. Crea tu PIN de seis cifras y guárdalo fuera del teléfono. No utilices el PIN ficticio de las pruebas del repositorio.
2. Para preparar la administración, todavía no añadas cuentas de Google, Xiaomi o WhatsApp. Conecta el Wi-Fi.
3. En el PC instala Android SDK Platform Tools desde https://developer.android.com/tools/releases/platform-tools .
4. En el Xiaomi habilita temporalmente las opciones de desarrollador y la depuración USB. Conecta el cable y acepta en el teléfono la autorización del PC.
5. Desde la carpeta de Platform Tools, confirma que el teléfono correcto aparece autorizado:

```powershell
.\adb.exe devices
```

6. Con un solo teléfono conectado, solicita que ABUHELP sea el administrador:

```powershell
.\adb.exe shell dpm set-device-owner cl.abuhelp.launcher/.FamilyAdminReceiver
```

Continúa solamente si Android informa que se asignó correctamente el device owner. Si rechaza la preparación por cuentas, configuración previa o restricciones de HyperOS, conserva el mensaje y solicita ayuda; no borres cuentas ni restablezcas el equipo a ciegas. La compatibilidad exacta debe verificarse en ese Redmi.

7. Desactiva la depuración USB y revoca las autorizaciones USB después de la preparación.
8. Configura las cuentas, YouTube, WhatsApp y Mahjong. En Acceso familiar añade contactos, selecciona Mahjong, habilita avisos/pantalla completa y «Permitir lectura de notificaciones» y elige ABUHELP como teléfono e inicio. Prueba las llamadas y los recordatorios.
9. En **Acceso familiar → Protección del teléfono**, pulsa **Activar protección** y confirma. Primero debe estar encendido el Wi-Fi y apagado el modo avión. El brillo se fija al 63%.
10. En la navegación del sistema selecciona los tres botones, para que tu padre pueda tocar Inicio sin depender de gestos. La ubicación de esta opción depende de HyperOS.

## Qué protege

La administración aplica restricciones de Android al encendido y configuración del Wi-Fi, modo avión, brillo del sistema, redes móviles, restablecimiento de red, fecha/hora, tiempo de apagado de pantalla y controles de aplicaciones. Impide desinstalar ABUHELP y fija su pantalla de inicio. El panel superior queda bloqueado con el teléfono desbloqueado; la campana «Notificaciones» muestra los avisos activos en letra grande y solo permite leerlos. No guarda su contenido, no lo envía ni ejecuta acciones de las notificaciones. Los botones de volumen se mantienen disponibles. Android puede ocultar contenido sensible y los avisos descartados no se conservan.

La API de bloqueo del panel no se aplica en la pantalla bloqueada. Desactiva manualmente en HyperOS el acceso al centro de control y barra de notificaciones desde la pantalla de bloqueo; la ubicación depende de la versión. Comprueba deslizando desde ambos extremos superiores con el teléfono bloqueado y desbloqueado. Si sigue apareciendo, no des por terminado ese requisito. No se elimina el bloqueo de pantalla ni se activa un modo quiosco que pueda impedir abrir otras aplicaciones.

No garantiza conexión si falla el router, la SIM o el operador. No transforma las pantallas internas ni los controles de llamada de WhatsApp. El bloqueo del brillo se refiere al ajuste del sistema; las aplicaciones externas pueden tener controles visuales propios.

## Actualizar desde la beta anterior

La beta anterior se firmó con una clave de prueba efímera. Una nueva compilación no se puede instalar encima con una firma distinta. No retires la administración ni desinstales hasta conservar contactos y horarios y acordar la migración. Desinstalar pierde los datos locales de ABUHELP, incluido PIN, contactos elegidos y recordatorios. La nueva preparación como device owner puede exigir volver a retirar cuentas del teléfono. No se solicita restablecer ni borrar el teléfono.

## Volver y recuperar

Pulsa **Inicio** para regresar a ABUHELP; toca otra vez YouTube, Mahjong u otro botón grande para volver a abrirlo. La aplicación externa decide si conserva el punto exacto anterior.

Para cambiar Wi-Fi u otros ajustes: **Acceso familiar → PIN → Protección del teléfono → Desactivar protección**. Al terminar vuelve a activarla. Al desactivarla se restauran los valores previos de brillo y se retiran los bloqueos establecidos por ABUHELP.

Para dejar de administrar: **Retirar administración**, con PIN y confirmación. Esta función no solicita un borrado del teléfono. Después puedes cambiar el launcher o desinstalar la beta. Si aparece un error, no desinstales ni restablezcas: conserva el mensaje y solicita ayuda.

Esta beta usa firma de prueba. Antes de sustituirla por una APK con firma distinta, retira la administración y conserva una copia de tus contactos y horarios; desinstalar pierde los datos locales de ABUHELP.

## Prueba familiar antes de usarlo a diario

Comprueba con el teléfono desbloqueado y bloqueado que no se pueda desactivar el Wi-Fi, activar modo avión ni bajar el brillo. Prueba Inicio desde YouTube y Mahjong, llamadas entrantes/salientes, altavoz, WhatsApp, un recordatorio ficticio y un reinicio completo. Verifica que el PIN permita recuperar los ajustes. No entregues el equipo con una protección incompleta.

Fuentes técnicas: [restricciones de Android](https://developer.android.com/reference/android/os/UserManager), [dispositivos dedicados](https://developer.android.com/work/dpc/dedicated-devices/cookbook).
