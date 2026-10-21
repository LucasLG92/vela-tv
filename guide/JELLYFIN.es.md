# Conectar Jellyfin

1. En Vela, abre **Ajustes → Cuentas y servicios → Jellyfin**.
2. Escribe la dirección completa de tu servidor. Por ejemplo, `https://media.example.org/jellyfin` si está publicado con ese prefijo. Para un servidor de casa puedes usar su dirección HTTP local; esa conexión no está cifrada.
3. Pulsa **Conectar**. Autoriza el código desde **Conexión rápida / Quick Connect** en otra sesión de Jellyfin donde ya hayas iniciado sesión. La opción debe estar habilitada en el servidor.
4. Si prefieres o necesitas otro método, elige **Usar usuario y contraseña**. La contraseña se utiliza para iniciar sesión y no se guarda. No hace falta una clave de API.
5. Pulsa **Usar en Inicio**. Vela muestra el servidor, sus carteles, Seguir viendo con el tiempo restante disponible, Últimas subidas y las bibliotecas de películas y series.

Instala **Jellyfin para Android TV** en la televisión e inicia sesión allí con el mismo servidor y usuario. Al elegir un título en Vela, se abre su ficha en esa app; la reproducción se controla desde Jellyfin. Vela no comparte su token con la app ni importa sus contraseñas.

Puedes mantener Plex y Jellyfin conectados y cambiar entre ellos desde **Ajustes → Cuentas y servicios → Biblioteca de Inicio**. El proveedor elegido se usa también para los fondos del salvapantallas de biblioteca. Cambiar de biblioteca no desvincula la otra cuenta.

**Desvincular de Vela** elimina la sesión y los metadatos locales de Jellyfin e intenta cerrar esa sesión en el servidor. Conserva los contenidos del servidor, la configuración de Plex y la sesión de la app Jellyfin.

No hay detección automática ni lectura de las cuentas de otras aplicaciones. El servidor y la cuenta son los que autorice cada persona. Consulta [compatibilidad](COMPATIBILITY.es.md) y [privacidad](PRIVACY.es.md).

Referencias del proyecto Jellyfin: [Quick Connect](https://jellyfin.org/docs/general/post-install/quick-connect/) y [configuración de red y prefijos de URL](https://jellyfin.org/docs/general/post-install/networking/).
