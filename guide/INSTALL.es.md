# Instalar Vela

Necesitas un dispositivo con **Android TV o Google TV, Android 11 o posterior**. El sistema propio de Samsung o LG requiere un dispositivo Android TV externo conectado por HDMI.

## Instalación

1. Descarga el APK de [Releases](https://github.com/LucasLG92/vela-tv/releases). La versión actual es una preview.
2. Transfiérelo a la televisión e instálalo mediante el gestor que utilices normalmente. Si Android lo pide, autoriza la instalación para ese gestor.
3. Abre Vela. El asistente ofrece cuentas, aplicaciones y ubicación; puedes omitir cualquier paso.
4. Para probarlo, navega primero sin cambiar el inicio predeterminado. Comprueba mando, apps, imágenes y reproducción en tu dispositivo.

Si ya utilizas ADB, la actualización conserva los datos con:

```sh
adb -s <TV_IP>:5555 install -r Vela.apk
```

No borres los datos de la app para actualizarla. Las actualizaciones deben utilizar la misma clave de firma.

## Actualizar desde la televisión

A partir de **1.12.0 Preview**, entra en **Ajustes → Información y actualizaciones → Actualizaciones**. Vela comprueba las publicaciones públicas de GitHub una vez al día al abrirse; también puedes pulsar **Buscar ahora**. El aviso aparece en Inicio si hay una versión nueva.

Desde **1.12.1 Preview**, **Comprobar automáticamente** y **Descargar automáticamente** vienen activados por defecto. Puedes desactivarlos en esta pantalla; si ya elegiste desactivarlos, esa preferencia se conserva al actualizar. La descarga queda en la caché privada y Vela comprueba su integridad, identidad y firma. Android puede pedir que autorices a Vela a instalar aplicaciones; después debes confirmar cada instalación con el mando. Las cuentas, aplicaciones y preferencias se conservan.

Desde **1.15.1 Preview**, al conceder el permiso de instalación y volver a Vela se retoma el APK pendiente. Si cancelas o no concedes el permiso, puedes pulsar **Instalar** para volver a intentarlo. Si estás en una versión anterior y se queda en esa pantalla tras autorizar el permiso, vuelve a Vela y pulsa **Instalar** otra vez; no hace falta desinstalar ni borrar datos.

Las versiones preview incluyen por defecto las próximas versiones preliminares. Desactiva esa opción para recibir solo versiones estables. Puedes desactivar la comprobación automática.

Si tienes una versión anterior a 1.12.0, descarga e instala esta versión desde Releases una vez. Las versiones antiguas no pueden incorporar el actualizador por sí mismas. No desinstales Vela ni borres sus datos. Las compilaciones firmadas con otra clave no se pueden actualizar con el APK oficial.

## Elegir la pantalla de inicio

En **Vela → Pantalla de inicio** puedes elegir Vela o volver a **Original**. En equipos que necesiten la conexión ADB local, la propia televisión pedirá autorizarla. El cambio guarda primero la recuperación del inicio original. No cambia automáticamente tus ajustes de imagen, sonido, red ni cuentas.

Las compilaciones públicas mantienen desactivada la opción de deshabilitar paquetes. La compatibilidad del cambio de inicio y de las teclas dedicadas del mando depende del fabricante. Prueba el regreso a Original antes de depender de Vela como inicio permanente.

## Tus cuentas

- **Plex:** autoriza el código en `plex.tv/link`. Elige entre tus servidores si aparecen varios. Los carteles, bibliotecas y perfil proceden de esa cuenta.
- **Jellyfin:** abre Ajustes → Cuentas y servicios → Jellyfin, introduce la dirección completa de tu servidor y autoriza Quick Connect desde otra sesión de Jellyfin. También puedes usar usuario y contraseña. Instala y configura Jellyfin para Android TV con ese mismo servidor y usuario para abrir los títulos. [Guía de Jellyfin](JELLYFIN.es.md).
- **YouTube y Prime Video:** inicia sesión en sus propias aplicaciones. Vela abre esas sesiones y, para YouTube, las filas que la app comparta con Android TV.
- **TV:** el catálogo público y la guía proceden de TDTChannels. Puedes añadir una lista M3U HTTPS o conectar tu servicio Xtream desde TV → Lista y ajustes → IPTV · Xtream. Introduce los datos de la cuenta directamente en la televisión. [Configurar IPTV](IPTV.es.md).
- **Tiempo:** utiliza la ubicación aproximada, si la autorizas, o busca tu ciudad. No hay una ciudad personal incorporada en el APK.

## Personalizar

**Ajustes → Apariencia y rendimiento → Personalizar apariencia** ofrece color, tamaño de carteles y fondo. Se aplican al volver a Inicio. Restaurar apariencia conserva cuentas, apps y ubicación.

En la barra, mantener OK sobre una app abre **Mover** y **Quitar de la barra**. Quitar no desinstala la aplicación. Puedes añadir más accesos desde Ajustes.
