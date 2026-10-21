# Cambios

## 1.15.0 Preview · 2026-10-21

- Películas y Series empiezan por las últimas incorporaciones del servidor, con consultas independientes para cada tipo.
- Filtros y órdenes nativos publicados por las bibliotecas de Plex, y equivalentes de Jellyfin con valores reales del servidor: género, año, estado de visto y otros metadatos disponibles.
- Búsqueda por título, selección múltiple, combinaciones entre campos y orden ascendente o descendente. Los filtros se aplican a todo el catálogo y reinician la paginación; las bibliotecas conservan su selección durante la navegación.
- Listas de opciones con filas recicladas, búsqueda, páginas de 24 valores y límites de respuesta. La hoja y su trabajo pendiente se liberan al salir, sin añadir trabajadores ni ampliar la caché de imágenes.
- Comprobaciones nativas del mando, cancelación de borradores, restablecimiento, filtros globales, orden y separación de películas/series. Transporte Plex simulado y servidor Jellyfin 12.2 real; pruebas de metadatos y regresión de Seguir viendo.

## 1.14.0 Preview · 2026-10-21

- Menú de Seguir viendo al mantener OK, con Marcar como visto para la película o episodio seleccionado de Plex o Jellyfin. La acción se guarda en el usuario conectado al servidor.
- Próximos episodios de Plex Continue Watching / On Deck y Jellyfin Next Up, con la etiqueta Siguiente episodio. Prioridad para lo empezado y límites de fila existentes.
- Respuestas actualizadas de Plex sustituyen las filas anteriores para evitar resucitar contenido visto. La caché de Jellyfin conserva la distinción entre progreso y próximo episodio.
- Tipos NEXT y NEW de Watch Next para capítulos compartidos por otras apps; su historial se gestiona desde su aplicación.
- Protección ante pulsación larga, repeticiones de OK, doble envío, cambio de cuenta y errores de red. Pruebas nativas en emulador: Plex con transporte HTTPS simulado y Jellyfin con servidor 12.2 real. No se ha modificado una biblioteca personal durante las pruebas.

## 1.13.0 Preview · 2026-10-21

- Integración nativa con Jellyfin: servidor propio, Quick Connect o acceso con usuario y contraseña, nombre del servidor e imagen de perfil cuando está disponible.
- Carteles y fondos originales, Seguir viendo con duración y posición del servidor, Últimas subidas y catálogo de películas y series en páginas de 24 títulos.
- Selector de biblioteca de Inicio entre Plex y Jellyfin, sin desvincular la otra cuenta. Los fondos de biblioteca del salvapantallas siguen la biblioteca elegida.
- Apertura de la ficha del título en Jellyfin para Android TV; la app debe estar configurada con el mismo servidor y usuario.
- HTTPS para servidores remotos y HTTP solo para servidores elegidos en red local. Sin contraseñas persistentes ni credenciales en enlaces de reproducción.
- Se reutilizan los límites de imágenes y trabajadores existentes. Pruebas con servidor Jellyfin 12.2 real y app oficial Android TV 0.19.10 en emulador.


## 1.12.1 Preview · 2026-10-21

- Búsqueda y descarga de nuevas versiones activadas por defecto. Se mantienen la consulta diaria al abrir Vela, los límites de descarga y la verificación de firma e integridad.
- Las personas que hayan desactivado alguna opción conservan su elección. Ambas opciones siguen disponibles en Ajustes.
- Android sigue pidiendo confirmar la instalación. No se añade un servicio permanente ni se cambian cuentas, aplicaciones o ubicación.


## 1.12.0 Preview · 2026-10-21

- Al bajar o subir entre filas, el encabezado y la tarjeta seleccionada permanecen visibles. Los títulos de dos líneas y los textos del bloque principal disponen de espacio y límites explícitos.
- Actualizaciones desde GitHub en Ajustes → Información y actualizaciones → Actualizaciones. Comprobación automática diaria al abrir Vela, aviso discreto en Inicio y descarga automática opcional.
- Selección de versiones estables o preliminares. Descargas acotadas, comprobación SHA-256, paquete, versión y firma antes de entregar el APK al instalador de Android.
- Instalación confirmada por el usuario; las versiones anteriores requieren instalar esta actualización manualmente una vez. No se añade un servicio permanente.
- Pruebas nativas de navegación con mando, títulos largos, transferencias, cancelación, integridad, firma y permisos. Actualización real mediante el instalador de Android comprobada en emulador, conservando preferencias.


## 1.11.0 Preview · 2026-10-21

- Seguir viendo con tarjetas panorámicas uniformes, servicio, episodio, progreso y tiempo restante de Plex y de las apps que lo publiquen en Android TV.
- Últimas subidas de Plex con carteles verticales, orden por incorporación y hasta doce títulos debajo de Seguir viendo.
- Difuminados nativos en los extremos al quedar contenido fuera de cada fila.
- Fondo a toda altura, sin el corte anterior al final del degradado; las filas se recortan dentro de su ventana al desplazarse.
- Caché de imágenes conservada y carga de arte limitada a lo visible. Capturas nativas 4K y pruebas de navegación, formato, progreso, extremos y privacidad de la configuración.

## 1.10.4 Preview · 2026-10-21

- El tiempo restante de los programas se muestra en horas y minutos cuando supera una hora: «2 h 15 min» en lugar de «135 min».
- Las horas exactas se muestran sin «0 min»; los tiempos de hasta una hora conservan los minutos.
- Se mantienen la actualización por minuto y los límites de la pantalla de canales.

## 1.10.3 Preview · 2026-10-21

- Minutos restantes y barra de progreso del programa en emisión en cada tarjeta de canal.
- Cuenta atrás según la guía, actualizada con el temporizador de minuto existente y sin consultas adicionales.
- Cambio al siguiente programa y retirada del contador cuando falta un horario válido.
- Actualizaciones sin reconstruir las tarjetas ni desplazar el foco del mando; se mantienen doce canales por página.
- Pruebas de límites de horario, datos ausentes, último minuto, cambio de programa y cierre de la pantalla.

## 1.10.2 Preview · 2026-10-21

- Nueva biblioteca de apps con iconos cuadrados grandes, nombres debajo y seis columnas.
- Una sola cuadrícula: todas las favoritas primero, respetando el orden de la barra y sin duplicados.
- Iconos adaptativos con sus propias capas y colores; selección discreta y fondo opaco en degradado.
- Carga local en segundo plano, celdas recicladas y recursos liberados al cerrar o pausar.
- Caché de iconos de 1 MiB; el modo ligero reduce resolución y caché a 512 KiB.
- Navegación con mando y orden de más de cuatro favoritas comprobados en emulador.

## 1.10.1 Preview · 2026-10-21

- Tus apps muestra los iconos originales en una cuadrícula con tarjetas recicladas y navegación por mando.
- Iconos locales bajo demanda, caché de 512 KiB y liberación al cerrar, pausar o cambiar de pantalla.
- Navegación entre favoritas y aplicaciones, con límites claros en las filas y columnas.
- Presentación del proyecto renovada y textos de cuentas y salvapantallas más directos.

## Galería 4K · 2026-10-21

- 29 capturas nativas de alta resolución, con carteles y fondos reales de Plex autorizados por el usuario.
- Perfil y servidor anonimizados; progreso, páginas y ciudad de ejemplo.
- Nuevas vistas de Películas y Series. El APK y sus límites de memoria se mantienen.

## 1.10.0 Preview — 2026-10-21

- Seis colores de acento para selección del menú, acción principal, progreso y Ajustes.
- Carteles compactos, equilibrados o grandes, conservando la proporción y mostrando la imagen completa.
- Fondo más oscuro, equilibrado o más visible, sin añadir imágenes ni animaciones continuas.
- Restaurar apariencia conserva las cuentas, aplicaciones, clima y preferencias de rendimiento.
- Publicación con configuración neutra, galería de demostración y créditos de las imágenes.
- Plataforma de compilación 34 coherente en Windows y Linux.

Incluye la integración web de TV preparada en 1.9.1. La página oficial de Cuatro y el vídeo HTML5 de prueba se han comprobado en el emulador; los directos de las plataformas necesitan verificación física.

## Funciones anteriores

Biblioteca de Plex por páginas, Seguir viendo separado, imágenes con cachés y trabajo acotados, avatar y nombre del servidor, accesos configurables y ordenables con el mando, hora y clima, dos estilos de salvapantallas, configuración por instalación y televisión en directo con favoritos y guía.
