# Ajustes dentro de Vela

Los ajustes y la configuración de Vela comparten el inicio: barra lateral, cabecera, reloj y color elegido. Las cuentas Plex y Jellyfin, las fuentes M3U y Xtream, las actualizaciones, tus aplicaciones y las entradas tienen su sección dentro de ese mismo espacio.

- **OK sobre una app anclada:** abre esa aplicación.
- **→ sobre YouTube:** muestra sus vídeos disponibles dentro de Vela.
- **Mantener OK:** gestiona el acceso anclado como antes.
- **← desde la configuración:** vuelve a la barra lateral. En los campos de texto conserva el movimiento del cursor.
- **Atrás:** vuelve a la página anterior; desde la raíz de Ajustes, Apps o Entradas selecciona su icono en la barra.
- **Inicio del mando:** vuelve al inicio de Vela.

Los permisos, el instalador de Android, los ajustes del fabricante y la reproducción de una entrada pertenecen al sistema y conservan su interfaz nativa. Las aplicaciones externas conservan sus propias cuentas y pantallas.

## Recursos y privacidad

Solo se mantiene una pantalla interna. El historial conserva hasta ocho rutas y su posición, sin vistas, contraseñas ni URL de listas. Los trabajadores de cada pantalla se cancelan al abandonarla; la cuadrícula de apps recicla filas y libera su caché al salir. Los carteles ocultos del inicio se liberan al entrar en la configuración.

Los formularios de Jellyfin, Xtream y listas usan una ventana protegida. Los campos privados no entran en el estado guardado y se vacían al ocultarse. Las cuentas siguen guardándose con los mecanismos existentes de cada proveedor en el dispositivo.

## Comprobación de la vista previa

Compila un APK neutro e instálalo en un emulador propio. Las pruebas `integrated-settings`, `apps-grid`, `plex-link`, `personalization`, `updates` e `integrated-update-permission` del ejecutor `tests/test_artwork_device.py` cubren navegación, liberación de recursos y permisos. Las pruebas de actualizaciones requieren generar primero `tests/build_update_fixtures.py` después de compilar. Las capturas de `integrated-settings` usan un código Plex ficticio DEMO y ninguna cuenta personal.

Los cambios de la versión 1.22 se distribuyen desde las releases de GitHub y pueden buscarse en los ajustes de Vela.
