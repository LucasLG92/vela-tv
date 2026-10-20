# Contribuir a Vela

Usa Java 8 y las APIs nativas de Android. El mínimo es Android 11; aísla y comprueba las APIs posteriores. Mantén los textos nuevos en portugués, inglés y español mediante `Ui.tr`.

El inicio debe permanecer sin vídeo automático, sondeos de aplicaciones ni tareas continuas de dibujo. Limita imágenes, páginas y colas; cancela el trabajo obsoleto y libera imágenes y reproductores al ocultar la pantalla.

Antes de enviar cambios:

1. Compila con la configuración neutra siguiendo [la guía](guide/BUILD.md).
2. Ejecuta las pruebas relacionadas con el cambio. Los cambios de recuperación requieren `python tests/test_home_mode.py`.
3. Ejecuta `python tools/audit_publication.py` sobre los archivos que vayas a publicar.
4. Revisa las capturas y los avisos de licencia. No utilices una cuenta personal para las capturas públicas.

Describe el problema, el comportamiento resultante y las pruebas realizadas. No incluyas datos de un servidor real en código, pruebas, commits, issues ni imágenes.

La galería se genera en un emulador limpio mediante `--test gallery`. Sus datos de Plex son una simulación identificada como Demo y sus carteles tienen licencias abiertas. No debe ejecutarse en una televisión personal. Las aplicaciones de prueba usadas para mostrar accesos no son versiones funcionales de los servicios.
