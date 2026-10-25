# Cambios

## Vela Lite 0.3.2-preview

- Compatibilidad de la verificación de firma con televisores Android 9 y 10 que no devuelven `SigningInfo` para APK descargadas. Se mantiene la comprobación de integridad, paquete, versión y coincidencia exacta de firma mediante el gestor de paquetes de Android.
- Instalación inicial o actualización manual desde la APK para las versiones Lite anteriores afectadas; las siguientes actualizaciones utilizan el flujo automático.

## Vela Lite 0.3.1-preview

Primera distribución pública de Vela Lite para la televisión que acompaña a una TV Box, consola u otro equipo HDMI. Incluye entradas con perfiles de dispositivo, antena con guía horizontal y programación dobleM, favoritos, cajón de apps y ajustes integrados.

- Búsqueda y descarga automáticas de versiones Lite, con verificación de integridad, paquete y firma.
- Continuación de la instalación al volver de los permisos de Android y limpieza de la APK instalada.
- Guía por páginas, logos, horarios, progreso y tiempo restante; caché en disco y liberación de recursos al salir.
- Preferencias de aspecto y pantalla inicial; navegación directa entre Aplicaciones y Ajustes.
- Ficha, instalación y galería propias de Lite.


## 1.22.0 Preview · 2026-10-24

- Paleta grafito común en Inicio, Ajustes y TV: superficies, campos, guía y progreso acordes con la barra lateral y un único acento elegido por el usuario.
- Entradas HDMI rediseñadas con un dispositivo destacado, accesos compactos e iconos con los colores de las marcas como vectores nativos. Sin fotografías, descargas ni trabajador de imágenes en la sección.
- Agrupación de puertos y dispositivos HDMI-CEC, cambios de conexión por eventos y perfiles para consolas Nintendo, PlayStation, Xbox y Sega, equipos de juego, ordenadores, streaming, vídeo y audio. Mantener OK permite asignar un modelo sin cambiar el nombre del puerto en la TV.
- Un dispositivo solo muestra su nombre e icono con conexión activa compartida por Android. En espera, sin señal o sin identificar se muestra HDMI y su número; un nombre antiguo o una asignación manual no simulan una conexión.
- OK sobre cualquier aplicación anclada abre su aplicación. La flecha derecha sobre YouTube conserva sus vídeos dentro de Vela; mantener OK sigue permitiendo ordenar y ocultar accesos.
- Ajustes, cuentas, actualizaciones, fuentes de TV, aplicaciones y entradas se muestran dentro del inicio, con la misma barra lateral, cabecera y tema. La vista previa del salvapantallas usa ese mismo espacio y devuelve el foco al cerrarse.
- Una sola pantalla interna montada y un historial limitado de rutas sin vistas ni credenciales. Los trabajadores, iconos y carteles ocultos se liberan al salir de su sección; se restaura la página de ajustes si Android recrea el inicio.
- Se conserva la continuación de una instalación pendiente después del permiso de Android y del cierre del proceso. Los formularios de cuenta y listas se protegen contra capturas y no guardan campos privados en el estado de navegación.
- El instalador, los permisos, la reproducción de entradas y los ajustes del fabricante siguen siendo pantallas del sistema. Se conserva el acceso independiente de recuperación.

## 1.21.0 Preview · 2026-10-22

- TV en directo integrada en Inicio: comparte barra lateral, reloj y tiempo, con su acceso justo debajo de Inicio. Al salir de TV se liberan el trabajo pendiente y los logos; durante el directo se libera el arte de navegación.
- Guía como vista inicial para todas las instalaciones. La elección Guía/Canales se guarda solo desde Ajustes → TV en directo; se retiran el selector de vista y los puntos de opciones de la cabecera.
- Guía ampliada con seis filas, categorías a la izquierda, controles de horario y Ahora debajo, y pie con logo, programa, horario, tiempo restante y progreso. Las categorías largas conservan la ayuda visible.
- Canales ocupa todo el ancho sin columna de categorías; arriba/abajo cambia de lista conservando la posición horizontal cuando es posible.
- Reproductor con logo original, programa actual/siguiente, horarios y progreso. El aviso de directo y la resolución real se ocultan tras cuatro segundos; la información, tras seis. OK vuelve a mostrarla.
- Derecha abre los canales sobre el vídeo, con su programa y tiempo restante en horas y minutos. Explorar o paginar conserva el mismo decodificador; OK cambia de canal y Atrás/Izquierda cierra el panel. Se retiran los botones Canales, Favorito y Otra señal del reproductor. Tras agotar las señales alternativas, OK reintenta sin un bucle automático.
- Páginas de doce registros, filas recicladas, seis logos de guía y caché de 768 KiB. Sin tarjetas de canales ocultas en Guía, miniaturas de vídeo ni un servicio adicional. La actualización de los tiempos conserva el foco del mando.
- Pruebas nativas con M3U y XMLTV públicos, cambio de canal real, ajustes persistentes, 180 categorías, navegación, límites de vistas y liberación. Regresión del cambio automático entre señales de prueba, con un único reproductor.
- Nuevas capturas nativas en 4K desde un emulador independiente y documentación renovada. Sin cuentas IPTV, sesiones Plex/Jellyfin ni ubicación personal en las imágenes o en el instalador.

## 1.20.2 Preview · 2026-10-22

- Conexión de Plex rediseñada con el tema de Vela: logo original, código destacado y tarjetas de servidor con foco del color elegido.
- Servidor seleccionado identificado y listas largas dentro de una zona con desplazamiento; las acciones y la ayuda del mando permanecen visibles.
- Una conexión fallida conserva la sesión y devuelve el foco al servidor para reintentar con OK. Si falla la consulta de servidores tras autorizar el código, se muestra Reintentar sin descartar la autorización.
- Recursos vectoriales y vistas nativas, sin imágenes de red, vídeo, desenfoques ni servicios nuevos. El trabajador de conexión se libera al salir.
- Pruebas nativas con código y servidores ficticios: caducidad, errores, nueve servidores con el mando, selección y liberación de recursos. Dos capturas nuevas en 4K sin datos personales.

## 1.20.1 Preview · 2026-10-22

- El APK descargado por Vela se elimina al confirmar Android que su versión ya está instalada. La limpieza utiliza el aviso de actualización de la app y se recupera al abrir Vela si ese aviso no llegó.
- Funciona sin red y con las comprobaciones y descargas automáticas desactivadas. Se conservan los APK de versiones pendientes, también al cancelar o fallar una instalación.
- Se retiran los temporales abandonados del actualizador. Un bloqueo de archivo protege las descargas activas entre el proceso de Inicio y el receptor del sistema; no se añade un servicio ni una comprobación periódica.
- Solo se gestionan los dos archivos privados del actualizador. Las cuentas, la personalización y los archivos ajenos permanecen intactos.
- Prueba nativa con sustitución real del paquete Android, recuperación al abrir sin conexión, descargas bloqueadas, conservación de la instalación pendiente y regresión del permiso con cierre/recreación del proceso.

## 1.20.0 Preview · 2026-10-22

- Cambio automático de señal en M3U y Xtream: si una emisión falla, se prueban sus otros formatos y variantes del mismo canal, priorizando la misma calidad y después las inferiores.
- Margen de 20 segundos para iniciar una señal y detectar bloqueos prolongados de reproducción; comprobación de progreso cada cinco segundos y respeto de la pausa con el mando.
- Se conserva la calidad preferida para la próxima vez. Cambiar de canal, salir o volver a la lista cancela los intentos pendientes. Si fallan todas las señales disponibles, se permite reintentar con Otra señal.
- Un único reproductor; hasta ocho variantes y cuatro enlaces por variante, sin repetir URL ni realizar descargas de prueba. Sin búsquedas entre otras cuentas, países, regiones o emisiones +1. La antena mantiene su comportamiento propio.
- Pruebas nativas con señales locales de prueba: errores HTTP, vídeo HLS y MPEG-TS decodificado, salto de FHD a HD/SD, pausa, cambio de canal, liberación y agotamiento de alternativas. Regresión M3U y Xtream con 10.001 canales.

## 1.19.1 Preview · 2026-10-22

- Guía horizontal junto a la cuadrícula: logos, horarios, línea de hora actual, tiempo restante y botón Ahora. Navegación con el mando entre programas, canales, bloques y páginas; se recuerda la vista elegida.
- Las categorías se recortan dentro de una zona fija para que su desplazamiento no tape la ayuda del mando, también en listas largas.
- Tres fuentes nativas: M3U, Xtream y canales de antena que comparta Android. Se retiran las rutas web y la integración con TiviMate de TV en directo.
- Logos grandes y una entrada por canal y categoría. Mantener OK permite gestionar favoritos y elegir entre hasta ocho variantes de calidad. Los países, regiones y emisiones +1 se mantienen separados.
- Catálogos M3U/Xtream procesados por flujo e indexados en disco. EPG dobleM como XMLTV externo inicial; índice de 48 horas y programación de la fuente como complemento. La guía horizontal recupera seis canales, tres horas y hasta 32 programas por canal.
- Se mantienen los límites de logos, se dibujan los programas sin crear una vista por celda y se libera el reproductor al salir. No se añade un servicio permanente ni vídeo de fondo.
- Pruebas nativas en emulador con 180 categorías y comprobación visual en TCL, navegación de la guía, calidad, reproducción FHD y conservación de cuentas. Las nuevas capturas públicas proceden de un emulador independiente sin cuentas personales.


## 1.16.0 Preview · 2026-10-21

- IPTV por Xtream dentro de TV: conexión privada en la televisión, categorías del proveedor, páginas de doce canales, logotipos, favoritos y guía actual/siguiente con tiempo restante.
- Cuenta y favoritos cifrados con AES-GCM y Android Keystore. Formulario protegido contra capturas, sin campos guardados ni cuentas incorporadas al APK público. HTTP requiere una elección explícita.
- Reproducción nativa HLS/MPEG-TS, cambio de canal entre páginas, liberación del decodificador al salir y acceso separado a TiviMate cuando está instalado.
- Procesamiento de categorías por flujo con límites de datos y tiempo. Se conserva el presupuesto de logotipos y se consulta solo la guía visible, sin servicio permanente.
- Pruebas con una API local propia y vídeo sintético: 10.001 canales conservando doce registros por página, cifrado, autenticación, guía, favoritos, reproducción real, cambio de canal, pausa y eliminación de cuenta. Las credenciales de una cuenta personal no se han utilizado en las pruebas.

## 1.15.1 Preview · 2026-10-21

- La instalación continúa al regresar del permiso de fuentes desconocidas, también si Android recrea Vela. La petición explícita se guarda durante una hora y se vincula al APK exacto.
- El APK vuelve a comprobarse antes de abrir el instalador del sistema. Denegar el permiso o cancelar permite reintentar sin bucles ni nuevas descargas. Las descargas automáticas nunca inician una instalación.
- La petición caduca si cambia la versión, desaparece el archivo o falla la verificación. Sin servicios ni consultas permanentes.
- Pruebas en emulador con la pantalla real de permisos, cierre del proceso por Android, recuperación y confirmación del sistema; pruebas de cancelación, reintento, caducidad, firma y alteración del APK.

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
