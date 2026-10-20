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

## Elegir la pantalla de inicio

En **Vela → Pantalla de inicio** puedes elegir Vela o volver a **Original**. En equipos que necesiten la conexión ADB local, la propia televisión pedirá autorizarla. El cambio guarda primero la recuperación del inicio original. No cambia automáticamente tus ajustes de imagen, sonido, red ni cuentas.

Las compilaciones públicas mantienen desactivada la opción de deshabilitar paquetes. La compatibilidad del cambio de inicio y de las teclas dedicadas del mando depende del fabricante. Prueba el regreso a Original antes de depender de Vela como inicio permanente.

## Tus cuentas

- **Plex:** autoriza el código en `plex.tv/link`. Elige entre tus servidores si aparecen varios. Los carteles, bibliotecas y perfil proceden de esa cuenta.
- **YouTube y Prime Video:** inicia sesión en sus propias aplicaciones. Vela abre esas sesiones y, para YouTube, las filas que la app comparta con Android TV.
- **TV:** el catálogo y la guía proceden de TDTChannels. Puedes añadir una lista M3U HTTPS desde TV → Lista y ajustes.
- **Tiempo:** utiliza la ubicación aproximada, si la autorizas, o busca tu ciudad. No hay una ciudad personal incorporada en el APK.

## Personalizar

**Ajustes → Apariencia y rendimiento → Personalizar apariencia** ofrece color, tamaño de carteles y fondo. Se aplican al volver a Inicio. Restaurar apariencia conserva cuentas, apps y ubicación.

En la barra, mantener OK sobre una app abre **Mover** y **Quitar de la barra**. Quitar no desinstala la aplicación. Puedes añadir más accesos desde Ajustes.
