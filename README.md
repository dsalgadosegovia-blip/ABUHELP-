# ABUHELP — Android accesible

Versión inicial de evaluación. Launcher, contactos con letra grande, controles propios de llamadas telefónicas, voz mediante botón y recordatorios locales.

## Funciones
- Inicio fijo: Llamar, YouTube, Noticias, Clima, Mahjong y Remedios.
- Contactos seleccionados por un familiar; importación opcional de la agenda y alta manual.
- Teléfono predeterminado con Contestar, Rechazar, Colgar y Altavoz grandes. Requiere conceder el rol de teléfono.
- Botón Hablar: hora, apertura de funciones, búsqueda de contacto y borradores de recordatorios.
- Configuración del cuidador protegida por PIN de seis cifras, sin PIN predeterminado.
- Recordatorios configurados y confirmados por el cuidador, con avisos locales. Registrar un aviso visto no prueba una toma.
- Noticias de Chile; acceso web al clima de San Miguel, Santiago; juego Mahjong elegido entre las aplicaciones instaladas.

## Compilar
Java 17, Android SDK 35, Build Tools 34.0.0, Gradle 8.9 y Android Gradle Plugin 8.7.3.

```sh
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

GitHub Actions ejecuta estas tareas, comprueba la firma y genera el hash SHA-256. Una compilación satisfactoria no equivale a validación en un teléfono físico.

## Instalar la beta
1. En Actions, abre la ejecución satisfactoria más reciente y descarga el artefacto ABUHELP-debug-beta.
2. Descomprime el ZIP y transfiere el APK al teléfono.
3. Android puede solicitar autorización para instalar desde esa aplicación de archivos/navegador.
4. Abre ABUHELP y entra en Acceso familiar. Crea y guarda el PIN.
5. Añade contactos; selecciona Mahjong; permite notificaciones y pantalla de llamadas.
6. Selecciona ABUHELP como teléfono e inicio predeterminados.
7. Configura y prueba los recordatorios con un aviso ficticio, antes de introducir horarios reales.

## Alcance y límites de la beta
- Las llamadas personales de WhatsApp conservan su propia interfaz. El acceso Abrir WhatsApp lleva al contacto; no implementa controles propios para responder, terminar ni cambiar su altavoz.
- La interfaz de aplicaciones externas permanece bajo su control.
- El PIN protege ABUHELP; no bloquea ajustes de Android, desinstalación ni detención forzada.
- Los avisos requieren permisos de notificación y alarmas exactas; revisar batería y autoinicio del fabricante. Después de reiniciar requieren el primer desbloqueo. No usar como único sistema de supervisión de medicación.
- No se infieren tratamientos, dosis ni compensaciones de tomas.
- La voz usa el servicio Android disponible: puede requerir internet o un idioma descargado y enviar audio al proveedor. ABUHELP no guarda audio.
- Datos personales guardados localmente, sin servidor ni credenciales en el repositorio.
- La firma debug es de prueba y puede cambiar entre compilaciones. Una versión con firma diferente exige desinstalar y pierde datos locales. Una distribución mantenida necesita firma estable privada.
- No existe recuperación universal del PIN; borrar datos o desinstalar elimina contactos y recordatorios de ABUHELP.

## Prueba en dispositivo obligatoria
Verificar llamadas entrantes con pantalla bloqueada y desbloqueada, llamadas salientes y selección SIM, colgar, altavoz/auricular/Bluetooth, llamadas simultáneas, emergencia mediante el sistema, regreso desde apps, tamaño máximo de letra, toques repetidos, permisos denegados, reposo, reinicio y cambios de hora. Validar por separado la accesibilidad real de WhatsApp. Mantener disponible el teléfono original para recuperar el funcionamiento.

## Recuperación
Desde Ajustes de Android → Aplicaciones predeterminadas, vuelve al teléfono y launcher originales. Las rutas concretas dependen de Android y HyperOS.
