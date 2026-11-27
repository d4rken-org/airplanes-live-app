---
title: Mapa
description: "Usa el mapa en directo: detalles de aeronaves, etiquetas y filtros, seguir una aeronave, reproducir el historial y la vista AR."
permalink: /es/map
translation_key: map
lang: es
source_hash: 44db13a43bbb6642
---
# Mapa
{: #map }

El mapa muestra las aeronaves que airplanes.live recibe en este momento. Con el zoom alejado, la app muestra solo una parte de las aeronaves visibles y lo indica ("Mostrando X de Y aeronaves. Acerca el zoom para ver más."). Acerca el zoom para verlas todas.

Los botones del borde del mapa:

- **Controles del mapa**: opciones de visualización y de etiquetas, ver más abajo.
- **Lista de aeronaves**: una lista de las aeronaves visibles. Fíltrala por texto, por aeronaves **Seguidas**, por **Militar**, **Categoría** y **Seleccionadas y fijadas**, y ordénala por indicativo, tipo, altitud o velocidad. Consulta los filtros más abajo.
- **Mi ubicación**: mueve el mapa hasta donde estás. La primera vez, la app te pide tu ubicación aproximada.
- **Vista AR**: consulta [Vista AR](#ar-view). Solo aparece en dispositivos con los sensores de movimiento necesarios.
- **Pantalla completa**: el mapa ocupa toda la pantalla.

## Detalles de la aeronave
{: #aircraft-details }

Toca una aeronave para abrir sus detalles: ruta (origen y destino), matrícula, código hex, modelo, operador, país, altitud, velocidad, squawk y más, con una foto de Planespotters.net cuando la hay. Un aviso alerta de los códigos squawk de emergencia 7500, 7600 y 7700.

<a class="guide-shot" href="/assets/images/map/aircraft-details.webp"><img src="/assets/images/map/aircraft-details.webp" width="540" height="1200" loading="lazy" alt="Detalles de una aeronave en el mapa, con sus datos de vuelo y su recorrido reciente"></a>

Desde los detalles puedes:

- **Seguir** la aeronave, para que el mapa la mantenga a la vista, hasta que toques **Dejar de seguir**.
- **Añadir a lista de seguimiento**. Cambia el nuevo seguimiento a **Notificar: activado** para recibir una alerta cuando la aeronave vuelva a ser detectada. Consulta [Lista de seguimiento y alertas](/es/watchlist).
- **Mostrar en búsqueda**, o compartir un enlace a la aeronave en globe.airplanes.live.
- Abrir su **Historial**, ver más abajo.

## Controles del mapa
{: #map-controls }

<a class="guide-shot" href="/assets/images/map/map-controls.webp"><img src="/assets/images/map/map-controls.webp" width="540" height="1200" loading="lazy" alt="Los controles del mapa con opciones de visualización y de etiquetas"></a>

**Pantalla**

- **Todas las trayectorias**: muestra el recorrido reciente de cada aeronave.
- **Resaltar militares**: marca las aeronaves militares con una insignia.
- **Resaltar seguidas**: marca con una insignia las aeronaves que coinciden con tus seguimientos y resalta el contorno de las áreas que sigues.

**Etiquetas**

- **Indicativo** (o el código hex si la aeronave no envía ninguno), **Tipo de aeronave**, **Altitud** y **Velocidad** eligen lo que muestran las etiquetas. Cada una se activa o desactiva por separado; en una instalación nueva todas empiezan desactivadas.
- Con todas desactivadas, el mapa no muestra etiquetas, y una aeronave que toques sigue mostrando su indicativo.

**Selección**

- **Selección múltiple**: al tocar aeronaves, se fijan en lugar de abrir sus detalles. Puedes fijar hasta 100 aeronaves. Toca la etiqueta "fijadas" para quitarlas.

Abajo, **Abrir en airplanes.live** abre la misma vista en globe.airplanes.live en tu navegador, y **Restablecer mapa** quita las aeronaves fijadas y la selección y aleja el zoom hasta mostrar el mundo entero.

**Filtros de la lista de aeronaves**

- **Militar**: oculta las aeronaves que no son militares. Las aeronaves seleccionadas y fijadas siguen visibles.
- **Categoría**: un menú con **Aviones**, **Helicópteros**, **Planeadores**, **Globos**, **Drones**, **Vehículos terrestres** y **Desconocido**; el mapa muestra solo los tipos elegidos. **Todas las categorías** desactiva este filtro. Las aeronaves seleccionadas y fijadas siguen visibles.
- **Seleccionadas y fijadas**: oculta todas las demás aeronaves. Primero selecciona o fija aeronaves.

Cada filtro activo aparece como una etiqueta en el mapa; toca la etiqueta para desactivar el filtro. Si ninguna aeronave coincide, **Borrar filtros** en la lista los desactiva todos. **Militar** y **Categoría** se mantienen al reiniciar la app; el filtro de texto y **Seguidas** se restablecen.

El estilo base del mapa se elige en **Configuración** › **Mapa** › **Estilo de mapa**. **Automático (claro u oscuro)** sigue el tema de la app. Los demás estilos forman parte del plan Pro. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).

## Historial
{: #history }

**Historial** reproduce por dónde voló una aeronave durante los últimos 7 días. Avanza por los días con **Día anterior** y **Día siguiente**, arrastra el control **Hora del día** o toca **Reproducir** y elige una velocidad de reproducción.

El historial forma parte del plan Pro. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).

## Seguir un área
{: #watch-area }

Mantén pulsado el mapa para seguir el área alrededor de ese punto. La app abre **Seguir esta área** con la ubicación ya rellenada. Para cambiar un área que ya sigues, activa **Resaltar seguidas** y mantén pulsado el punto del centro del área. Los seguimientos de ubicación forman parte del plan Pro. Consulta [Lista de seguimiento y alertas](/es/watchlist#location).

## Vista AR
{: #ar-view }

La vista AR muestra la imagen de la cámara con etiquetas para las aeronaves en esa dirección. Necesita acceso a la cámara para mostrar el cielo y acceso a la ubicación para encontrar aeronaves cerca de ti. Toca una etiqueta para abrir esa aeronave en el mapa.

Usa el control de alcance para elegir hasta qué distancia mirar, de 1 a 50 NM. Pro permite un alcance mayor que Gratis.

Si las etiquetas aparecen en la dirección equivocada, tienes que calibrar la brújula: toca **Ayuda** en la vista AR y mueve el teléfono dibujando un 8. Consulta [Las etiquetas AR apuntan en la dirección equivocada](/es/faq#ar-calibration).
