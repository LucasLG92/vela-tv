# Vela Lite · Desarrollo y compatibilidad

Un inicio para la **propia televisión**, cuando un dispositivo HDMI se encarga del contenido. Mantiene el aspecto grafito, la barra flotante, Inter y los iconos de Vela, con tres destinos: **Entradas HDMI, Televisión y Aplicaciones**.

## Una televisión, un inicio

- **HDMI:** identifica conexiones activas, conserva el puerto seleccionado y abre la fuente mediante Android. Mantener OK permite elegir un perfil de dispositivo; una entrada desconectada sigue mostrándose como HDMI. MECOOL utiliza el símbolo de Android TV.
- **Antena:** guía horizontal de tres horas, seis canales por página, horarios, programas actuales y siguientes, descripción, progreso y tiempo restante en horas y minutos. También puede elegirse una vista de doce tarjetas desde Ajustes. Incluye televisión, radio, HD y favoritos.
- **Apps:** iconos originales de las aplicaciones instaladas en la televisión, doce por página. Las apps de una TV Box siguen perteneciendo a la TV Box.

La barra lateral permite pasar directamente entre Aplicaciones y Ajustes con las flechas. Los ajustes se presentan en una sola superficie, con categorías fijas a la izquierda y opciones alineadas a la derecha: **Televisión, Aspecto, Actualizaciones, Sistema e Información**. Se puede elegir fondo grafito o negro, mostrar el reloj, reducir el movimiento al enfocar y decidir qué pantalla abre el botón Inicio. Los permisos y los ajustes del fabricante mantienen sus pantallas de Android.

## Guía y directo

El catálogo procede de los canales que ya ha sintonizado la televisión. Lite **no escribe, reordena, borra ni resintoniza** la base de canales del fabricante. Necesita el permiso de lectura de la guía de televisión.

La programación pública de [dobleM](https://github.com/davidmuma/EPG_dobleM) completa los horarios de antena. Usa su XMLTV comprimido ligero, analiza el archivo como un flujo y conserva en SQLite solo hasta 48 horas de los canales sintonizados. La caché se actualiza cuando se utiliza la guía y han pasado cuatro horas; el botón Actualizar permite renovarla antes. No incorpora canales de pago del archivo al catálogo ni reproduce sus emisiones. **No hay listas M3U, cliente Xtream, suscripciones ni claves IPTV.**

Se pueden desactivar los horarios por Internet en Ajustes → Televisión. Si no hay conexión, se conserva la caché y se utiliza la información de antena. Los canales sin datos muestran «Sin programación». Los logos del sintonizador tienen prioridad; los picons públicos y los pequeños logos locales completan los que faltan. Sus fuentes y créditos están incluidos. Los canales desconocidos muestran su número.

Mientras la guía está visible, observa los cambios del proveedor de canales y programas. Al volver desde la sintonización nativa, lee de nuevo el catálogo. Si cambian los identificadores internos tras resintonizar, sustituye las URI antiguas y trata de conservar selección y favoritos por el servicio DVB o por un nombre único. No adivina coincidencias ambiguas. La programación de Internet se relaciona por los nombres, con independencia del número o URI del canal.

El directo utiliza **una única sesión nativa TvView**. OK muestra u oculta información; derecha abre los canales con programa y tiempo restante, arriba y abajo cambian de canal, y Atrás cierra primero el panel y después vuelve a la guía. Mantener OK en la guía o el panel añade o quita favoritos. Los distintivos de directo y resolución desaparecen después del inicio. Se respetan los bloqueos del sintonizador y se ofrece la TV original como alternativa cuando el firmware no permite la reproducción integrada. No incluye grabación, pausa del directo ni reproducción de emisiones pasadas.

## Actualizaciones de Lite

La búsqueda y descarga automáticas están **activadas por defecto**. Una apertura visible puede iniciar una comprobación diaria en el [repositorio de Vela](https://github.com/LucasLG92/vela-tv); el trabajo termina sin mantener un servicio, alarma o consulta periódica. Android sigue pidiendo confirmar la instalación.

Lite recibe **solo versiones Lite**, nunca la APK de Vela completa. Antes de instalar se comprueban origen HTTPS, tamaño, SHA-256 del recurso de GitHub, paquete, versión, Android mínimo y firma. Una solicitud explícita de instalación se conserva durante el paso por los permisos de Android y se retoma al volver. La APK instalada se elimina en la siguiente apertura de Lite.

Para distribuir una nueva versión compatible deben aumentarse `versionCode` y `versionName`, conservar el paquete y la firma, y publicarse una release con:

- Etiqueta: `lite-v0.3.4-preview` (con la versión que corresponda).
- APK: `Vela-Lite-0.3.4-preview.apk`.
- Recurso subido completamente y con `digest` SHA-256 en la API de GitHub.

Si la API no responde o limita las consultas, se utiliza `lite/updates.json` en el mismo repositorio. El archivo contiene únicamente metadatos de una release Lite ya publicada; se aplican los mismos filtros de origen, nombre, tamaño y SHA-256. Para actualizarlo después de publicar, guarda los metadatos públicos de la release en un JSON local y ejecuta:

```sh
python lite/tools/update_feed.py --release release.json --apk Vela-Lite-0.3.4-preview.apk
```

La herramienta exige que la release esté publicada y que el tamaño y SHA-256 coincidan con la APK. Revisa y publica el cambio de `lite/updates.json` con cada release Lite.

Las etiquetas de la versión completa se excluyen. Si todavía no hay una versión Lite más reciente publicada, el actualizador lo indica. La clave privada de firma no se incluye en el código ni en la APK.

## Memoria y privacidad

Paquete independiente `tv.vela.launcher.lite`, Android 8.0 / API 26 o posterior. Puede coexistir con Vela completo. No incorpora Plex, Jellyfin, recomendaciones, bibliotecas multimedia ni salvapantallas.

La guía dibuja los programas en un componente Canvas y consulta únicamente los seis o doce canales visibles, con un máximo de 32 programas por canal. La programación se muestra antes de descargar logos, para que una imagen lenta no bloquee la navegación. El panel del reproductor lee hasta seis canales y el canal actual. La base XMLTV está limitada a 8 MiB; los logos en disco, a 2 MiB. Las cachés de imágenes de guía y apps se limitan a 512 KiB cada una, con imágenes de hasta 128 × 128, y se liberan al salir de esas pantallas. Las consultas y observadores se cancelan al abandonar la guía; la sesión del sintonizador se cierra al salir del directo. Hora y progreso se actualizan una vez por minuto.

Solo solicita lectura de canales, Internet e instalación de paquetes. No pide ubicación, accesibilidad, superposición ni arranque. No declara servicios ni receptores permanentes; el único proveedor privado entrega al instalador una APK concreta y solo con permiso de lectura. Las cuentas y claves de la versión completa no se leen ni se empaquetan. Android puede conservar un inicio en memoria para volver a él con rapidez; Lite no promete consumo cero de RAM.

## Compatibilidad y compilación

El firmware debe publicar sus entradas, sintonizador y datos de televisión mediante las API de Android. Instalar Lite en una TV Box no da acceso a los puertos ni a la antena de la pantalla receptora. Encendido, sonido, mando, HDMI-CEC y sintonización continúan a cargo del fabricante. Antes de desactivar un inicio original mediante ADB debe existir una recuperación comprobada; la APK no desinstala ni desactiva aplicaciones por sí sola.

Desde la raíz del repositorio, con Android SDK y JDK 17:

```powershell
./lite/build.ps1 -SdkPath '<Android SDK>' -JdkPath '<JDK 17>'
python lite/tests/check_package.py
python lite/tests/run_device.py --serial '<emulador propio>' --adb-port 5038 --test integration
python lite/tests/run_device.py --serial '<emulador propio>' --adb-port 5038 --test antenna
python lite/tests/run_device.py --serial '<emulador propio>' --adb-port 5038 --test xmltv
python lite/tests/run_device.py --serial '<emulador propio>' --adb-port 5038 --test updates
```

El resultado es `lite/Vela-Lite.apk`. El empaquetado usa una lista explícita de código y recursos y no lee la configuración personal de Vela.

Las pruebas nativas usan un emulador propio, metadatos DVB simulados e iconos instalados. Comprueban mando, CEC, focos, paginación, resintonización simulada, favoritos, límites de EPG, XMLTV, horarios y cambios de zona, importaciones incompletas y canceladas, integridad y firma de APK, retorno desde permisos, proveedor privado, limpieza y liberación de recursos. Los instaladores se interceptan: **no se instalan las APK de prueba**. Los diagnósticos de un televisor real y las capturas de hardware se conservan por separado; no demuestran una resintonización física ni por sí solos la calidad de imagen y sonido.
