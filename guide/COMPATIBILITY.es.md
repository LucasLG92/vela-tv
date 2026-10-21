# Compatibilidad y servicios

Vela funciona en dispositivos con Android TV o Google TV y Android 11 o posterior. Los televisores con otro sistema necesitan un dispositivo Android TV externo por HDMI.

## Plex

Autoriza tu cuenta en plex.tv/link y elige tu servidor. Los títulos y la calidad de los carteles dependen de tu biblioteca y de las imágenes disponibles en el servidor. Vela abre el contenido en la aplicación de Plex.

## YouTube y Prime Video

Gestionan sus cuentas dentro de sus propias aplicaciones. Vela abre esas sesiones y muestra las filas que YouTube comparta con Android TV, si autorizas el acceso correspondiente. No incluye una integración OAuth para obtener el inicio privado ni Ver más tarde de YouTube.

## Televisión y webs de las emisoras

El catálogo y la guía proceden de TDTChannels. La disponibilidad de las señales depende de su emisor, región y compatibilidad de reproducción. Puedes añadir tu propia lista M3U HTTPS.

Las webs oficiales pueden abrirse dentro de Vela. Pueden exigir cookies, una cuenta o requisitos de DRM. Que se cargue una web no certifica la reproducción de su directo. La compatibilidad de cada emisora debe comprobarse en el dispositivo.

## Rendimiento

La caché de imágenes del inicio se limita a 6 MiB, o 3 MiB en modo ligero. La biblioteca de aplicaciones recicla sus celdas y carga iconos locales de hasta 192 × 192, con una caché de 1 MiB. En modo ligero utiliza 128 × 128 y 512 KiB. Las celdas visibles también mantienen su propia referencia a los iconos; cerrar el panel libera esas vistas y la caché. Estos límites no representan la memoria total de la aplicación.

La cuadrícula libera su cargador y sus iconos al cerrarse. Los reproductores se liberan al salir; las webs utilizan un proceso separado. El consumo real depende del dispositivo y de la función abierta.

## Preview y pantalla de inicio

La versión 1.10.4 se ha comprobado en un emulador Android 16 y aún requiere validación completa en televisores físicos. La compatibilidad con el botón de inicio y los accesos dedicados del mando depende del fabricante. Sigue la [guía de instalación y recuperación](INSTALL.es.md) y comprueba el regreso a Original antes de usar Vela como inicio permanente.

[Volver a Vela](../README.md)
