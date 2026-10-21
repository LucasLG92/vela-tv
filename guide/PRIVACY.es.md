# Datos y servicios

Vela no incluye cuentas, servidores ni ubicaciones del desarrollador. La configuración se guarda en el almacenamiento privado de Android; la copia de seguridad automática de la app está desactivada. No se afirma que las preferencias utilicen un cifrado propio adicional.

| Función | Datos y destino |
| --- | --- |
| Plex | Plex recibe la autorización del dispositivo. El servidor seleccionado recibe las solicitudes de biblioteca e imágenes con su token. La sesión se guarda localmente y se puede desvincular desde Ajustes. |
| Avatar | Se consulta el perfil autorizado. La imagen y sus redirecciones se descargan sin reenviar el token a sus servidores de imágenes. |
| Seguir viendo de otras apps | Con el permiso de TV, se leen títulos, imágenes, enlaces y progreso que las apps instaladas publiquen en Watch Next. Las imágenes se cargan desde sus proveedores o URI locales; no se reenvían credenciales de Plex. No se accede a sus contraseñas ni se importan sus sesiones. |
| Tiempo | Open-Meteo recibe coordenadas aproximadas redondeadas o el nombre de una ciudad buscada. No se solicita ubicación precisa ni acceso en segundo plano. |
| TV | TDTChannels recibe las consultas del catálogo y de la guía; los servidores de cada emisora reciben las solicitudes de reproducción y logotipos. |
| Web integrada | Se abre la web de la emisora dentro de Vela. Esa web gestiona cookies, anuncios, cuentas, restricciones y reproducción protegida según sus propias condiciones. Sus cookies se conservan privadamente en la instalación. No se importan sesiones de otras apps. |
| Actualizaciones | Al abrir Vela, la comprobación diaria consulta las publicaciones públicas de GitHub sin iniciar sesión. GitHub y sus servidores de descarga reciben la conexión (incluida la dirección IP), sin cuentas, tokens, biblioteca ni ubicación de Vela. La búsqueda y la descarga automática vienen activadas por defecto desde 1.12.1 y se pueden desactivar en Ajustes; se conservan las elecciones explícitas anteriores. El APK se guarda en caché privada y se verifica por SHA-256, paquete, versión y firma; solo el instalador obtiene acceso de lectura mediante un permiso temporal. Android pide confirmar la instalación. Se puede desactivar la comprobación. |
| Salvapantallas | Las imágenes proceden del servidor Plex elegido o los vídeos de sus autores. No se incluyen películas ni archivos de vídeo en el APK. |

Vela no añade analítica ni anuncios propios. Esto no implica que las webs y servicios externos carezcan de publicidad o seguimiento. Las políticas de esos proveedores también se aplican a sus contenidos.

Los accesos de la barra, el aspecto y los favoritos de TV se guardan en el dispositivo. No se sincronizan con un servidor de Vela. La configuración es por instalación/perfil de Android; no hay un selector de varias personas dentro del mismo inicio.

El reproductor web utiliza un proceso separado que se carga al abrir un canal web y termina al cerrarlo. Esto reduce su presencia fuera de la reproducción; WebView consume memoria mientras se utiliza y no se promete consumo cero.

Las capturas públicas se generan en un emulador independiente, sin credenciales válidas. Los carteles y fondos se han obtenido de Plex con autorización del usuario y se utilizan únicamente dentro de las capturas. El avatar, nombre del servidor, cuenta, rutas y enlaces privados no se publican. El progreso, la paginación y la ciudad son ejemplos; no revelan el historial de reproducción ni la ubicación del usuario. Consulta [la galería](../gallery/README.md).
