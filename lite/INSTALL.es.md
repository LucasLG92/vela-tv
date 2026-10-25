# Instalar Vela Lite

Vela Lite se instala en una televisión Android TV / Google TV con Android 8.0 o posterior. Si usas una TV Box, instálala en la propia pantalla para acceder a sus entradas y antena.

1. Descarga la **APK de Vela Lite** desde la [release oficial](https://github.com/LucasLG92/vela-tv/releases/tag/lite-v0.3.4-preview) y envíala al televisor con el método que utilices para instalar APK.
2. Abre el archivo y confirma la instalación. Android puede pedir permiso a la aplicación desde la que lo abres.
3. Abre **Vela Lite**. En **Ajustes → Sistema → Pantalla de inicio**, elígela como inicio predeterminado cuando el firmware lo permita.
4. Entra en **Televisión** y permite leer la guía. Los canales deben estar previamente sintonizados en la aplicación de TV del fabricante.
5. La programación por Internet y las actualizaciones automáticas vienen activadas. Puedes cambiarlas en sus categorías de Ajustes.

## El mando

| Acción | Control |
| --- | --- |
| Recorrer entradas, apps o programas | Flechas |
| Abrir o elegir | OK |
| Identificar un dispositivo HDMI | Mantener OK sobre su entrada |
| Añadir o quitar un canal de favoritos | Mantener OK sobre el canal |
| Ver canales mientras miras el directo | Derecha |
| Mostrar u ocultar información del directo | OK |
| Cerrar un panel o volver | Atrás |

## Recibir actualizaciones

En **Ajustes → Actualizaciones** puedes buscar una versión de forma manual, cambiar la búsqueda y descarga automáticas e instalar un archivo ya preparado. Android pide confirmar la instalación; si solicita permitir instalaciones a Vela Lite, concede el permiso y vuelve a la app para continuar.

Las versiones Lite utilizan su propio canal y nunca instalan la APK de Vela completa. Mantén la misma firma y paquete al actualizar para conservar tus preferencias. Al abrir la app tras una actualización, se elimina la APK instalada.

## Si faltan entradas, canales o programas

Las entradas y el sintonizador dependen de lo que comparta el fabricante. Sintoniza los canales en la TV original; Lite volverá a leer la lista al regresar. La guía dobleM se relaciona con los nombres de esos canales y la información de antena sirve como alternativa. La primera carga puede tardar unos segundos según la conexión.

Algunos firmwares dan prioridad al inicio del fabricante. Antes de modificarlo mediante ADB, conserva una recuperación comprobada del inicio original. No desactives servicios de TV, sonido, mando, HDMI o actualizaciones del sistema para instalar un launcher.

[Volver a Vela Lite](README.md) · [Detalles de compatibilidad](DEVELOPMENT.es.md)
