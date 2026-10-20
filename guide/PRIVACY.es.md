# Datos y servicios

Vela no incluye cuentas, servidores ni ubicaciones del desarrollador. La configuración se guarda en el almacenamiento privado de Android; la copia de seguridad automática de la app está desactivada. No se afirma que las preferencias utilicen un cifrado propio adicional.

| Función | Datos y destino |
| --- | --- |
| Plex | Plex recibe la autorización del dispositivo. El servidor seleccionado recibe las solicitudes de biblioteca e imágenes con su token. La sesión se guarda localmente y se puede desvincular desde Ajustes. |
| Avatar | Se consulta el perfil autorizado. La imagen y sus redirecciones se descargan sin reenviar el token a sus servidores de imágenes. |
| Tiempo | Open-Meteo recibe coordenadas aproximadas redondeadas o el nombre de una ciudad buscada. No se solicita ubicación precisa ni acceso en segundo plano. |
| TV | TDTChannels recibe las consultas del catálogo y de la guía; los servidores de cada emisora reciben las solicitudes de reproducción y logotipos. |
| Web integrada | Se abre la web de la emisora dentro de Vela. Esa web gestiona cookies, anuncios, cuentas, restricciones y reproducción protegida según sus propias condiciones. Sus cookies se conservan privadamente en la instalación. No se importan sesiones de otras apps. |
| Salvapantallas | Las imágenes proceden del servidor Plex elegido o los vídeos de sus autores. No se incluyen películas ni archivos de vídeo en el APK. |

Vela no añade analítica ni anuncios propios. Esto no implica que las webs y servicios externos carezcan de publicidad o seguimiento. Las políticas de esos proveedores también se aplican a sus contenidos.

Los accesos de la barra, el aspecto y los favoritos de TV se guardan en el dispositivo. No se sincronizan con un servidor de Vela. La configuración es por instalación/perfil de Android; no hay un selector de varias personas dentro del mismo inicio.

El reproductor web utiliza un proceso separado que se carga al abrir un canal web y termina al cerrarlo. Esto reduce su presencia fuera de la reproducción; WebView consume memoria mientras se utiliza y no se promete consumo cero.

Las capturas públicas se generan en un emulador independiente, sin credenciales válidas. Los carteles y fondos se han obtenido de Plex con autorización del usuario y se utilizan únicamente dentro de las capturas. El avatar, nombre del servidor, cuenta, rutas y enlaces privados no se publican. El progreso, la paginación y la ciudad son ejemplos; no revelan el historial de reproducción ni la ubicación del usuario. Consulta [la galería](../gallery/README.md).
