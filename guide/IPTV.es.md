# Tu IPTV en Vela

Conecta tu servicio **Xtream** para ver sus canales, categorías, logotipos y programación dentro de la sección TV. Puedes usar los mismos datos con los que configuraste TiviMate. La función viene sin ninguna cuenta ni lista personal incorporada.

## Conectar en la televisión

1. Abre **TV → Lista y ajustes → IPTV · Xtream**.
2. Introduce la dirección del servidor, el usuario y la contraseña de tu servicio. Estos datos se introducen directamente en la televisión.
3. Utiliza HTTPS si tu proveedor lo ofrece. Si la dirección empieza por HTTP, activa **Mi servidor usa HTTP sin cifrado** para utilizar esa conexión.
4. Pulsa **Conectar y usar en Vela** y después **Ver mis canales**.

La cuenta se valida antes de guardarla. Elige una categoría a la izquierda y pasa de página con el mando. Mantén OK sobre un canal para añadirlo a favoritos o quitarlo. Los favoritos pertenecen a esta cuenta de Vela y se guardan en el dispositivo.

## Ver los canales

Pulsa OK para abrir el canal en el reproductor nativo de Vela. Usa las teclas de canal para cambiar de emisión; al llegar al final de una página se carga la siguiente. Atrás vuelve a la lista. Al salir de la reproducción se libera el decodificador y el audio.

La guía muestra el programa actual, el siguiente y el tiempo restante cuando el proveedor envía horarios válidos. La compatibilidad depende de la señal y de los códecs de la televisión. Esta integración cubre el directo; películas a la carta, series del proveedor, grabaciones y programas anteriores quedan fuera de esta versión.

**Abrir TiviMate** está disponible cuando la app está instalada. Abre su propia lista para elegir el canal allí. Es un acceso a la aplicación; las cuentas, favoritos e historial de TiviMate se gestionan dentro de TiviMate.

## Datos privados

- Servidor, usuario y contraseña se guardan cifrados con AES-GCM y una clave de Android Keystore de esa instalación.
- La pantalla de acceso bloquea capturas y no guarda los campos en el estado de la ventana ni en el autocompletado.
- Los datos se envían al servidor Xtream elegido para autenticar, consultar canales y reproducirlos. Vela no los envía a GitHub ni a un servidor propio.
- El APK público contiene la función genérica. Las cuentas se configuran después de instalarlo, por persona y dispositivo.
- **Eliminar cuenta de este dispositivo** borra la cuenta y sus favoritos de Vela. Puedes volver a TDTChannels o a tu M3U desde Lista y ajustes conservando la conexión Xtream para otra ocasión.

La instalación limita las copias de seguridad de Android. Los registros del sistema o del proveedor dependen de esos componentes; evita compartirlos sin revisar sus direcciones autenticadas.

## Consumo

Se conservan hasta doce canales por página, hasta 512 categorías y hasta 128 favoritos. La lista de la categoría se procesa como un flujo, con límites de respuesta y tiempo; no se crea un catálogo completo en RAM. Los logotipos reutilizan el presupuesto existente de 768 KiB en memoria y 4 MiB en disco. Solo se consulta la guía de la página visible mientras TV está abierta. No se añade un servicio permanente ni se reproducen vistas previas al navegar.

Las consultas de una categoría se limitan a 16 MiB y 100.000 registros. Si un proveedor supera esos límites o no implementa las operaciones habituales, puede requerir categorías más pequeñas o su aplicación habitual.

Referencias del protocolo: [API Xtream de XUI.live](https://xui.live/docs/api/xtream/). Aplicación: [TiviMate en Google Play](https://play.google.com/store/apps/details?id=ar.tvplayer.tv). Vela implementa su propio cliente y no está afiliado a TiviMate.
