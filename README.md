# Vela TV

Tu biblioteca, tus aplicaciones y tu televisión, en una pantalla pensada para el mando.

Launcher nativo para **Android TV y Google TV · Android 11+**. Configuración por instalación, carteles reales de Plex y una interfaz que puedes personalizar sin añadir fondos animados al inicio.

**[Descargar la preview](https://github.com/LucasLG92/vela-tv/releases/tag/v1.10.0-preview)** · [Instalación](guide/INSTALL.es.md) · [Todas las capturas](gallery/README.md) · [English](guide/EN.md)

![Inicio de Vela en 4K con Oppenheimer y carteles reales de Plex](gallery/01-inicio.webp)

> Capturas nativas en **4K**, con carteles y fondos reales obtenidos de Plex con autorización del usuario. Perfil y servidor anonimizados; ubicación, progreso y paginación de ejemplo. Los originales y las credenciales no se publican ni se incluyen en el APK. [Sobre las imágenes](gallery/CREDITS.md).

## A tu manera

| Menta · carteles compactos | Lavanda · carteles grandes |
| --- | --- |
| ![Aspecto menta y carteles compactos](gallery/02-aspecto-menta-compacto.webp) | ![Aspecto lavanda y carteles grandes](gallery/03-aspecto-lavanda-grande.webp) |

- **Seis colores:** Nieve, Azul, Menta, Lavanda, Arena y Rosa.
- **Tres tamaños de cartel**, conservando la proporción y la imagen completa.
- **Fondo más oscuro, equilibrado o más visible** con las mismas imágenes.
- Restaurar apariencia conserva cuentas, apps, ubicación y rendimiento.
- Barra con más de cuatro accesos, iconos originales y orden mediante pulsación larga de OK. Quitar un acceso no desinstala la app.

## Qué ofrece

| Función | Comportamiento |
| --- | --- |
| Plex | Vinculación mediante código, elección de servidor, avatar y nombre del servidor, carteles y fondos de la cuenta autorizada. |
| Biblioteca | Películas y series por páginas de hasta 24 títulos; solo se conserva una página. Seguir viendo tiene su propia fila. |
| Aplicaciones | Accesos elegidos entre las apps instaladas y vista de todas las aplicaciones. |
| TV | Catálogo y guía de TDTChannels, categorías, favoritos, doce canales por página y listas M3U HTTPS personales. |
| Web integrada | La web oficial de una emisora puede abrirse dentro de Vela, con cursor para el mando y pantalla completa. La compatibilidad depende de la plataforma. |
| Salvapantallas | Vídeos de paisajes o fondos de Plex, reloj y tiempo por ubicación aproximada o ciudad elegida. Las fuentes externas mantienen sus condiciones de uso. |
| Ajustes | Interfaz nativa, asistente inicial, cuentas, aspecto, calidad, movimiento reducido y retorno al inicio original. |

![Ajustes de apariencia](gallery/11-apariencia.webp)

## Cuidar la memoria

El inicio no reproduce vídeos ni dibuja animaciones continuas. Las imágenes se solicitan bajo demanda, se cancelan al cambiar de contenido y se liberan al abrir otra app. La caché de imágenes está limitada a 6 MiB, o 3 MiB en modo ligero; el disco y las colas también tienen límites.

El reproductor nativo y el web se liberan al salir. La web se carga en un proceso separado únicamente al abrir un canal que la necesita, y ese proceso termina al volver a la parrilla. WebView sí consume memoria mientras está abierto. El consumo total depende del dispositivo y no equivale al tamaño de la caché.

## Estado de esta versión

**1.10.0 Preview.** La personalización, la navegación con mando, los tamaños de cartel, los límites de caché y el restablecimiento se han probado en un emulador Android 16. También se han probado catálogo y guía reales, reproducción nativa y controles web. La galería se captura en un emulador sin credenciales personales; las imágenes de Plex se preparan por separado y se anonimiza la información de la cuenta.

Funciones anteriores se han probado en una TCL con Android 11. Esta preview completa todavía necesita validación física: **cargar la página de Cuatro no demuestra que su directo o el de Telecinco funcionen en todos los televisores**. Las plataformas pueden pedir sus cookies, cuenta, región o requisitos de reproducción.

YouTube y Prime Video conservan sus sesiones en sus propias apps. Vela no obtiene el inicio privado ni Ver más tarde de YouTube mediante una integración OAuth propia. Usa las filas que YouTube comparta con Android TV y sus accesos oficiales.

## Empezar

Instala el APK y completa, u omite, el asistente. Vincula tus cuentas y elige tus aplicaciones en la televisión; no hace falta editar código ni solicitar una clave de TMDB. [Guía de instalación y recuperación](guide/INSTALL.es.md).

Para desarrollar: [compilar y ejecutar pruebas](guide/BUILD.md). El proyecto usa Java 8 y el SDK nativo, sin Gradle ni bibliotecas de interfaz externas. El repositorio público contiene configuración neutra y un historial nuevo; no incluye claves de firma ni configuración de una televisión personal.

## Documentación y créditos

- [Galería completa, con una captura de cada sección](gallery/README.md)
- [Funciones en detalle](guide/FEATURES.es.md) · [Cambios](CHANGELOG.md)
- [Privacidad y servicios externos](guide/PRIVACY.es.md) · [Seguridad](SECURITY.md)
- [Contribuir](CONTRIBUTING.md) · [Créditos y licencias](NOTICE.md)

Vela deriva de [Free My TV](https://github.com/simbasjungles/free-my-tv) y conserva sus avisos MIT. Plex, YouTube, Prime Video y las demás marcas pertenecen a sus propietarios. Este proyecto es independiente y no implica su respaldo.
