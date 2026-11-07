---
title: Lista de seguimiento y alertas
description: "Sigue aeronaves, vuelos, códigos squawk y áreas, y recibe una notificación cuando aparezcan."
permalink: /es/watchlist
translation_key: watchlist
lang: es
source_hash: 18a704dfde28d617
---
# Lista de seguimiento y alertas
{: #watchlist }

Un seguimiento vigila algo por ti: una aeronave, un vuelo, un código squawk o un área. La pestaña **Lista de seguimiento** muestra cada seguimiento con lo que encontró la última comprobación. Activa su notificación y la app te avisará cuando aparezca.

## Añadir un seguimiento
{: #add }

<a class="guide-shot" href="/assets/images/watchlist/add-watch.webp"><img src="/assets/images/watchlist/add-watch.webp" width="540" height="1200" loading="lazy" alt="El diálogo para añadir un seguimiento, con los cuatro tipos"></a>

Toca el botón **+** en la pestaña Lista de seguimiento y elige un tipo:

- **Aeronave (código hex)**: una aeronave por su código hex, la dirección ICAO de 24 bits, como `3C6589`. También puedes tocar **Añadir a lista de seguimiento** en los detalles de una aeronave en el mapa.
- **Vuelo (indicativo)**: un vuelo por el indicativo que emite, como `DLH400`.
- **Código de transpondedor (SQUAWK)**: cualquier aeronave que transmita un código squawk, como `7700` para emergencias.
- **Ubicación (radio)**: aeronaves cerca de un lugar, ver más abajo.

Cada seguimiento puede tener una nota, para que recuerdes por qué lo añadiste.

Los seguimientos de squawk y de ubicación forman parte del plan Pro. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).

### Seguimientos de ubicación
{: #location }

Un seguimiento de ubicación cuenta las aeronaves dentro de un radio alrededor de un punto. Introduce coordenadas o el nombre de un lugar y toca **Resolver**, o toca **Usar mi ubicación**. Después ponle una etiqueta y elige el radio. El radio mínimo es de 2 km; el máximo depende de tu plan.

También puedes mantener pulsado el mapa para seguir el área alrededor de ese punto. Si más tarde mueves el área o cambias su tamaño, el historial del seguimiento empieza de cero.

## Recibir notificaciones
{: #notifications }

Las notificaciones están desactivadas en un seguimiento nuevo. Abre el seguimiento y toca **Notificar: desactivado** para cambiarlo a **Notificar: activado**.

Recibes una notificación cuando un seguimiento pasa de no encontrar nada a encontrar algo: se detecta una aeronave, un indicativo está en el aire, se transmite un código squawk o aparecen aeronaves en tu área. La notificación solo se repite si el seguimiento no ha visto nada entre medias. Si algo ya coincide la primera vez que se comprueba el seguimiento, esa primera coincidencia no genera aviso.

Cuando llegan muchas alertas a la vez, la app muestra unas cuantas y resume el resto como "Alertas de seguimiento perdidas". Toca una notificación para abrir su seguimiento.

Las alertas usan el canal de notificaciones "Alertas de lista de seguimiento". Android debe permitir las notificaciones de la app. Consulta [No recibo alertas](/es/faq#no-alerts).

## Con qué frecuencia se comprueban los seguimientos
{: #interval }

La app comprueba tus seguimientos en segundo plano cuando tiene conexión a la red. Elige la frecuencia en **Configuración** › **Lista de seguimiento** › **Intervalo de verificación**. El valor predeterminado es cada 60 minutos. Gratis permite como mínimo 60 minutos, Pro 15 minutos.

La app también comprueba al abrir la Lista de seguimiento si la última comprobación tiene más de 15 minutos. Desliza la lista hacia abajo para comprobar al instante.

## Detalles del seguimiento
{: #details }

Toca un seguimiento para ver:

- **Aeronaves coincidentes**: lo que encontró la última comprobación. Toca una para ver sus detalles.
- **Actividad, últimos 7 días**: una cuadrícula por horas de cuándo el seguimiento vio algo.
- **Aeronaves en cada comprobación, 30 días**: los recuentos de las comprobaciones que hizo esta app.

El menú **Más** tiene **Editar nota**, **Editar área** para los seguimientos de ubicación y **Eliminar seguimiento**.

## Límites
{: #limits }

El plan gratuito comprueba un número limitado de seguimientos, que se muestra como "X de Y seguimientos gratuitos usados". Los seguimientos que superan el límite se quedan en tu lista como "No comprobado, por encima de tu límite". Se comprueban los primeros seguimientos que añadiste. Pro los comprueba todos.
