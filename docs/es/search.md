---
title: Búsqueda
description: "Encuentra aeronaves por indicativo, matrícula, código hex, tipo, squawk, número de vuelo o ruta, y acota los resultados con filtros."
permalink: /es/search
translation_key: search
lang: es
source_hash: 9935e0ce8f3fcb13
---
# Búsqueda
{: #search }

La búsqueda encuentra aeronaves que airplanes.live está rastreando ahora mismo. Escribe en el campo de búsqueda y toca **Buscar**.

## Qué puedes buscar
{: #terms }

| Escribes | Encuentra |
|---|---|
| `DLH400` | un indicativo |
| `D-AIBL` | una matrícula |
| `3C6589` | un código hex (la dirección ICAO de 24 bits de la aeronave) |
| `A320` | un tipo de aeronave |
| `7700` | un código squawk (de transpondedor) |
| `FR5769` | la aeronave que opera un número de vuelo de pasajeros |
| `FR EIN FAO` | vuelos por aerolínea, aeropuerto de salida y de llegada |

Cada palabra acota a las demás: `DLH A320` encuentra los A320 de Lufthansa. Separa las alternativas con comas: `7500, 7600, 7700` encuentra los tres códigos squawk.

Muchas aerolíneas emiten un código distinto del número de vuelo de tu billete. Si un número de vuelo no da resultados, prueba la ruta de tu tarjeta de embarque, como `FR EIN FAO`. Los números de vuelo y las rutas funcionan como término independiente; si los combinas con otras palabras o con **Cercanos**, no darán resultados.

## Filtros
{: #filters }

- **Militar**: solo aeronaves militares.
- **Privacidad**: solo aeronaves de los programas de privacidad LADD y PIA.
- **Cercanos**: aeronaves alrededor de un lugar en lugar de un término de búsqueda. Empieza en **Mi ubicación**. Toca **Cambiar** para buscar cerca de un lugar por su nombre, como "Berlín, Alemania". Algunos dispositivos no pueden buscar nombres de lugares; en ese caso solo funciona **Mi ubicación**.

**Militar** y **Privacidad** acotan la búsqueda. **Cercanos** busca alrededor de un lugar.

## Resultados
{: #results }

Si permites el acceso a la ubicación, los resultados se ordenan por su distancia a ti. Los resultados marcados como **En caché** vienen de búsquedas anteriores y pueden estar desactualizados. Toca un resultado para ver sus detalles. Mantén pulsados los resultados para seleccionar varios y mostrarlos juntos en el mapa.

## Límites
{: #limits }

En el plan gratuito, una búsqueda devuelve un número limitado de resultados ("Mostrando X de Y coincidencias") y hay una cuota diaria de búsquedas ("Te quedan X de Y búsquedas hoy"). Pro elimina el límite y aumenta mucho la cuota. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).
