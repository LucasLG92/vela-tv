# Tu televisión, a tu manera

**M3U, Xtream y Antena**, con una misma pantalla para buscar canales, recorrer categorías, consultar la programación y guardar favoritos. El selector compacto de la cabecera permite cambiar de fuente; el engranaje reúne los ajustes y la actualización. Vela recuerda tu última categoría y página, y reproduce solo cuando eliges un canal.

## Tu lista M3U

Abre **Engranaje → Configurar lista M3U**. Introduce la URL de tu lista y, si lo necesitas, una URL XMLTV para la guía. Si la propia lista declara su XMLTV, Vela lo utiliza automáticamente. Pulsa **Guardar y usar M3U**.

HTTPS es la opción habitual. Para una lista o señales HTTP, activa **Mi lista o sus señales usan HTTP**. Esa conexión no cifra el tráfico. Las URL con credenciales se introducen en la pantalla protegida de la televisión y se guardan cifradas en el dispositivo.

Se admiten metadatos EXTINF, categorías EXTGRP, logos, identificadores de guía y hasta cuatro señales por canal. Las entradas con el mismo identificador se reúnen como señales alternativas. Solo se incluyen enlaces de reproducción directa: no se abren webs ni se ejecutan reglas de extracción. La lista pública inicial procede de TDTChannels y puede sustituirse por la tuya.

## Tu cuenta Xtream

Abre **Engranaje → Cuenta Xtream**. Introduce el servidor, el usuario y la contraseña de tu servicio y pulsa **Conectar y usar en Vela → Ver mis canales**. Cada instalación se configura con su propia cuenta; el APK viene sin cuentas personales.

Utiliza HTTPS cuando tu proveedor lo ofrezca. Si necesita HTTP, activa su opción en la pantalla de conexión. La cuenta se valida antes de guardarla. Se muestran sus categorías y sus horarios cuando el servidor los proporciona.

La integración cubre televisión en directo. Las películas a la carta, series del proveedor, grabaciones y programas anteriores no forman parte de esta función.

## Los canales de tu antena

Selecciona **Antena**. Vela consulta los canales que ya tiene sintonizados el televisor, ordenados por su número, y los reproduce con su sintonizador. También utiliza los logos y los horarios que Android permita leer. Los canales bloqueados por la TV no se incluyen.

Hace falta un televisor con sintonizador compatible y un cable de antena con señal. En una caja o dispositivo HDMI sin sintonizador, esta opción informa de su indisponibilidad. Vela no resintoniza ni cambia el orden de canales de la TV. **Engranaje → Ajustes de antena** abre la configuración que facilite el fabricante; solo se inicia al elegirla.

Si hay canales guardados pero falta el cable o la señal, la lista permanece disponible y el reproductor muestra **Sin señal de antena**. La compatibilidad depende del fabricante y de cómo exponga su sintonizador a Android.

## Encuentra, guarda y reproduce

- **Buscar** localiza canales por nombre. En Xtream consulta el catálogo general del servicio y lo conserva en la caché local.
- **Mantener OK** abre las opciones del canal para añadirlo a favoritos, quitarlo o elegir su calidad. Los favoritos pertenecen a su fuente y cuenta; admiten hasta 128 entradas.
- **OK** abre el reproductor de Vela. **CH+ / CH−** cambia de canal, también entre páginas. **Atrás** vuelve a la lista y libera la reproducción.
- En IPTV, **Otra señal** permite alternar entre los formatos o señales disponibles. Xtream utiliza HLS primero y MPEG-TS como alternativa cuando el servicio los ofrece.
- Si una señal IPTV falla, Vela prueba automáticamente otra del mismo canal. Primero intenta el otro formato o una señal de la misma calidad; después, las calidades inferiores más cercanas. Tu calidad elegida sigue guardada para la próxima vez que abras el canal.
- La guía muestra el programa actual, el siguiente y el tiempo que queda. Los horarios caducados o desconocidos no generan un contador.

### Si una señal deja de funcionar

El cambio automático está activo para M3U y Xtream cuando la lista incluye señales o calidades alternativas del mismo canal. Un error de reproducción permite pasar a la siguiente señal; una conexión que no arranca tiene un margen de 20 segundos. Durante la reproducción, una comprobación cada cinco segundos detecta si la posición lleva al menos 20 segundos sin avanzar. Las pausas con el mando se respetan.

Vela libera el reproductor anterior antes de abrir la siguiente señal. No hace descargas de prueba ni mantiene reproductores en segundo plano. Cada sesión recorre como máximo ocho variantes y cuatro enlaces por variante, sin repetir la misma URL ni guardar automáticamente la calidad inferior. Si fallan todas, se detiene y permite volver a intentarlo con **Otra señal**. Cambiar de canal, volver a la lista o salir cancela los intentos pendientes.

Las alternativas proceden de la misma fuente y cuenta; se mantienen separados países, regiones y emisiones +1. Si el proveedor no facilita otra señal del canal, Vela muestra que no está disponible. La antena conserva su aviso de falta de señal y no cambia a una fuente IPTV.

## Ligero al navegar

Las listas M3U y las categorías Xtream se procesan como un flujo y se indexan en disco. Cambiar de página, filtrar o buscar en un catálogo ya actualizado utiliza esa caché y recupera doce canales agrupados. Cada uno conserva como máximo ocho variantes ligeras de calidad; solo se cargan doce logos. Las categorías se muestran con filas recicladas. No se cargan todos sus logos ni se reproducen vídeos de fondo.

La caché de catálogos tiene un límite de 16 MiB y elimina catálogos antiguos. Las direcciones de sus registros se cifran con una clave de datos protegida por Android Keystore; los nombres se indexan localmente. La caché de logos mantiene el presupuesto de 768 KiB en memoria y 4 MiB en disco, con imágenes decodificadas de hasta 128 píxeles. No se añade un servicio permanente.

La caché se renueva al actualizar o cuando caduca: cuatro horas para M3U y quince minutos para categorías Xtream. Si falla una actualización, se conserva el catálogo anterior válido hasta siete días y no se sustituye por una descarga incompleta. La disponibilidad del vídeo sigue dependiendo del proveedor y de la conexión.

Los límites de entrada son 100.000 registros y 32 MiB por M3U, 16 MiB por categoría Xtream, hasta 512 categorías y 5.000 filas de antena. La capacidad efectiva depende también del tamaño de los registros y del presupuesto de disco. La guía XMLTV admite archivos XML o gzip, hasta 8 MiB de descarga y 32 MiB descomprimidos; se conserva únicamente el ahora/siguiente de los doce canales visibles en memoria.

## Tus datos se quedan contigo

La cuenta Xtream, las URL M3U/XMLTV y las direcciones de señales de la caché se guardan cifradas. Las pantallas de conexión bloquean capturas y autocompletado. Tus conexiones no se incorporan al código ni se envían a GitHub o a un servidor de Vela.

El proveedor elegido recibe las solicitudes necesarias para autenticar, consultar y reproducir. No se adjuntan credenciales a las peticiones de logos públicos ni se siguen redirecciones en las consultas autenticadas. Los logos públicos pueden usar HTTP aunque la cuenta use HTTPS; en Xtream se rechazan, además, las direcciones de imagen que reenviarían la cuenta a otro origen.

Referencias: [TV Input Framework de Android](https://developer.android.com/reference/android/media/tv/TvView), [API Xtream de XUI.live](https://xui.live/docs/api/xtream/) y [TDTChannels](https://github.com/LaQuay/TDTChannels).

## Logos grandes y canales agrupados

Desde 1.18, M3U y Xtream presentan una sola tarjeta por canal y categoría, con las versiones FHD, HD y SD dentro de **mantener OK → Calidad**. La elección se guarda por canal. La selección automática prioriza FHD y después HD; las variantes 4K/UHD siguen disponibles cuando las ofrece la lista. Se mantienen separados los prefijos de país, regiones y emisiones +1. No se agrupan canales entre distintas fuentes ni se modifican las listas del proveedor. Se conservan los favoritos anteriores de sus variantes.

Los fondos toman un tono del propio logo mediante una pequeña muestra de píxeles. Son degradados dibujados, sin desenfoques ni capas de vídeo. Los límites de imágenes permanecen en 128 píxeles, 768 KiB de caché de memoria y 4 MiB en disco. La agrupación se consulta en el índice local, antes de paginar.

## EPG dobleM y XMLTV externo

La guía externa inicial es [guiatv.xml de EPG dobleM](https://raw.githubusercontent.com/davidmuma/EPG_dobleM/master/guiatv.xml), de [davidmuma](https://github.com/davidmuma/EPG_dobleM). **Engranaje → Guía de programación** permite actualizarla, introducir otra URL HTTPS o utilizar únicamente la guía de la fuente. El XML no se incluye en Vela.

La descarga y el análisis trabajan por partes en segundo plano. El índice privado en disco guarda los programas vigentes y las siguientes 48 horas, con un límite de 24 MiB. En la cuadrícula se recuperan el programa actual y el siguiente de los doce canales visibles. La vista horizontal consulta seis canales y una ventana de tres horas, con un máximo de 32 programas por canal. La guía se renueva al utilizar TV cuando han pasado cuatro horas; no hay un servicio permanente y una importación fallida conserva el índice anterior. Se limitan la descarga y los datos descomprimidos a 64 MiB, 4.096 canales y 150.000 programas de entrada; se guardan como máximo 100.000 programas dentro de la ventana de tiempo.

La coincidencia utiliza el identificador XMLTV, sus nombres publicados y nombres de canal normalizados. Las coincidencias ambiguas se descartan y un prefijo de otro país impide usar una coincidencia española solo por nombre. Cuando no existe un programa actual válido en la guía externa, se intenta la programación de M3U, Xtream o antena. Su disponibilidad y precisión dependen de cada guía.

Los límites de 8/32 MiB descritos para la guía XMLTV propia de una lista M3U siguen correspondiendo a ese lector; la guía externa de dobleM utiliza el índice y los límites anteriores.

Si falta un logo válido del proveedor, Vela intenta el icono original declarado por la guía XMLTV seleccionada. Sus direcciones se guardan cifradas en el índice privado, y se aplican los mismos límites de descarga, tamaño y caché de logos. No se generan logos ni se añade un catálogo completo de imágenes en memoria.

## Guía horizontal

Elige **Guía** en la cabecera para recorrer la programación con los canales y sus logos a la izquierda. **Canales** recupera la cuadrícula; Vela recuerda la vista elegida.

- **Arriba / abajo:** recorrer canales, también entre bloques y páginas.
- **Izquierda / derecha:** elegir programas. Al llegar al borde, la ventana avanza o retrocede 90 minutos. Los controles de la cabecera permiten hacer lo mismo.
- **Ahora:** volver al horario actual. Una línea marca la hora en la programación.
- **OK en el programa actual:** ver el canal. En uno futuro, consultar su horario y, si quieres, abrir el directo del canal.
- **Mantener OK:** favoritos y calidad. **Atrás** desde la reproducción vuelve a la guía y libera el reproductor.

La programación completa procede del XMLTV externo configurado o de los datos de antena que comparta Android. Cuando una fuente solo facilita el programa actual y el siguiente, se muestran esos datos. Los huecos se indican como **Sin programación**; no se inventan horarios ni se añaden reproducción de programas pasados, grabaciones o recordatorios.

La guía muestra seis filas y tres horas por ventana, hasta las próximas 48 horas disponibles en el índice. Consulta el disco en segundo plano y retiene como máximo 192 programas por bloque. Los programas se dibujan en una sola vista, con seis logos de hasta 128 píxeles y la misma caché de 768 KiB. La hora se actualiza por minuto. No hay vistas de vídeo en segundo plano ni un servicio adicional.
