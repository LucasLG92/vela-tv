# Compatibilidad y servicios

Vela funciona en dispositivos con Android TV o Google TV y Android 11 o posterior. Los televisores con otro sistema necesitan un dispositivo Android TV externo por HDMI.

## Plex

Autoriza tu cuenta en plex.tv/link y elige tu servidor. Los títulos y la calidad de los carteles dependen de tu biblioteca y de las imágenes disponibles en el servidor. Vela abre el contenido en la aplicación de Plex. Últimas subidas utiliza la lista de incorporaciones del servidor y su fecha addedAt; si una serie llega como temporada, muestra el nombre y cartel de la serie. El inicio limita cada fila a doce títulos.

Desde 1.14 puedes mantener OK en Seguir viendo y marcar una película o un episodio como visto en el servidor Plex conectado. La acción afecta al usuario de esa conexión y solo al título seleccionado. La fila utiliza Continue Watching / On Deck del servidor, incluidos los siguientes capítulos pendientes; su disponibilidad y los periodos de seguimiento dependen de la biblioteca y ajustes de Plex. Una respuesta vacía actualizada sustituye los datos anteriores para evitar que reaparezcan episodios ya vistos.

## Jellyfin

Conecta la dirección completa de tu servidor, incluido su prefijo de URL si utiliza uno. Quick Connect se autoriza desde otra sesión de Jellyfin; si el servidor lo desactiva, utiliza usuario y contraseña. No necesitas una clave de API de administrador. HTTPS funciona con certificados válidos para Android; no se aceptan certificados inválidos mediante una excepción de la app. HTTP se admite para servidores en red local, sin cifrado, y se rechaza para direcciones públicas.

El inicio usa la API del servidor para carteles, últimos añadidos, bibliotecas y progreso. Cada fila se limita a doce títulos y las páginas a 24. Las temporadas recientes se representan por la serie. Los tiempos de reproducción ausentes no se inventan. El salvapantallas de biblioteca utiliza el proveedor elegido. Solo una biblioteca personal está activa en cada momento; no se mezclan los catálogos de Plex y Jellyfin.

Next Up aporta el siguiente episodio pendiente de series con historial de reproducción, sin repetir una serie que ya tenga un capítulo a medias en la fila. Al marcar un episodio como visto se actualiza el usuario conectado en el servidor y se solicita la fila de nuevo. No es un calendario de estrenos: solo aparecen episodios ya disponibles en la biblioteca que el servidor devuelva.

Para abrir la ficha de un título necesitas Jellyfin para Android TV, configurado con el mismo servidor y usuario. Vela transmite únicamente el identificador del título a esa app; no le entrega el token ni copia su sesión. El control de reproducción y la compatibilidad de formatos corresponden a Jellyfin. Se han comprobado la API y el recorrido de Vela con Jellyfin Server 12.2 y el destino de apertura de la app oficial Android TV 0.19.10, en emulador Android 16. Los endpoints antiguos tienen fallback, pero no se afirma haber validado todas las versiones o fabricantes.

## YouTube y Prime Video

Gestionan sus cuentas dentro de sus propias aplicaciones. Vela abre esas sesiones y muestra las filas que YouTube comparta con Android TV, si autorizas el acceso correspondiente. Seguir viendo puede reunir títulos de las apps instaladas que publiquen su progreso en Watch Next y permitan leerlo. También admite los tipos NEXT y NEW cuando la app comparte un próximo capítulo de una serie. Vela no modifica el historial de estas otras apps; el menú ofrece abrirlas para gestionarlo allí. La presencia de YouTube, Prime u otro servicio no está garantizada: depende de esa app y del dispositivo. Si no comparte duración o posición, Vela muestra Continuar sin inventar un tiempo. No incluye una integración OAuth para obtener el inicio privado ni Ver más tarde de YouTube.

## Televisión y webs de las emisoras

El catálogo y la guía proceden de TDTChannels. La disponibilidad de las señales depende de su emisor, región y compatibilidad de reproducción. Puedes añadir tu propia lista M3U HTTPS.

Las webs oficiales pueden abrirse dentro de Vela. Pueden exigir cookies, una cuenta o requisitos de DRM. Que se cargue una web no certifica la reproducción de su directo. La compatibilidad de cada emisora debe comprobarse en el dispositivo.

## Rendimiento

La caché de imágenes del inicio se limita a 6 MiB, o 3 MiB en modo ligero. La biblioteca de aplicaciones recicla sus celdas y carga iconos locales de hasta 192 × 192, con una caché de 1 MiB. En modo ligero utiliza 128 × 128 y 512 KiB. Las celdas visibles también mantienen su propia referencia a los iconos; cerrar el panel libera esas vistas y la caché. Estos límites no representan la memoria total de la aplicación.

La cuadrícula libera su cargador y sus iconos al cerrarse. Los reproductores se liberan al salir; las webs utilizan un proceso separado. El consumo real depende del dispositivo y de la función abierta.

## Preview y pantalla de inicio

La integración de Jellyfin de 1.13.0 se ha comprobado en un emulador Android 16 y aún requiere validación completa en televisores físicos. La compatibilidad con el botón de inicio y los accesos dedicados del mando depende del fabricante. Sigue la [guía de instalación y recuperación](INSTALL.es.md) y comprueba el regreso a Original antes de usar Vela como inicio permanente.

[Volver a Vela](../README.md)
