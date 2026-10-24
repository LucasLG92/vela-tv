# Tus dispositivos HDMI, dentro de Vela

Entradas utiliza la misma barra lateral, cabecera y paleta grafito que Inicio. El dispositivo seleccionado tiene un icono destacado, y las conexiones se recorren en una fila con el mando.

- **OK:** abre la entrada mediante Android.
- **Mantener OK:** elige la familia o el modelo, o vuelve a Automático.
- **Izquierda desde el primer acceso:** regresa a la barra lateral.

## Qué detecta Vela

Vela lee los puertos, sus etiquetas y las entradas lógicas HDMI-CEC que la televisión publica mediante `TvInputManager`. Agrupa el dispositivo y su puerto para no duplicarlos y actualiza la sección con los avisos del sistema mientras está visible. No escanea la red ni identifica equipos por una dirección IP.

El nombre y el icono del modelo solo se muestran cuando Android informa de **conexión activa**. Sin señal activa, en espera o con estado desconocido, aparece HDMI y su número, aunque haya quedado guardado un nombre antiguo. Un dispositivo activo sin identificar también conserva su nombre de puerto. Las etiquetas de conexión reflejan la información del fabricante; algunas televisiones no publican todos sus puertos o dispositivos.

Se reconocen nombres explícitos de Nintendo Switch, Switch OLED y Switch 2; Wii, Wii U, GameCube, Nintendo 64, NES y Super Nintendo; PlayStation y PS1–PS5; Xbox, 360, One, One S/X y Series S/X; Sega; Steam Deck y equipos de juego; PC y Mac; Apple TV, Fire TV, Chromecast, Google TV, Shield, Roku y Android TV; reproductores Blu-ray/DVD, decodificadores, receptores AV y barras de sonido. Otros equipos pueden utilizarse como entrada HDMI genérica.

La identificación depende del nombre que la televisión comparta. Un adaptador HDMI de una consola antigua puede no indicar qué consola hay detrás; en ese caso puedes asignarle el modelo con el mando. Una asignación manual nunca convierte un puerto sin señal en una consola conectada. Vela no puede distinguir de forma fiable todos los modelos ni certifica la presencia física del equipo más allá del estado publicado por Android.

## Iconos y recursos

Los iconos de las marcas mantienen sus contornos originales, convertidos a vectores nativos y presentados en blanco sobre el color de su marca; el resto de la interfaz mantiene la paleta grafito de Vela. Las fuentes y los avisos se incluyen en [los créditos](../app/assets/licenses/device-icons.txt). Cada variante se identifica por su nombre; algunas familias comparten el símbolo de su marca. Los equipos sin un símbolo de marca utilizan un icono genérico del tipo de conexión.

No se incluyen fotografías, no se decodifican imágenes de producto ni se añade un trabajador de imágenes para esta sección. Solo hay una animación breve al mover el foco. Al salir se sueltan los iconos y se deja de escuchar los cambios HDMI; no hay un servicio permanente, vídeo de fondo ni animaciones en reposo.

La apertura de la señal y los ajustes del fabricante siguen usando la interfaz del sistema Android.
