---
title: Preguntas frecuentes y solución de problemas
description: "Respuestas sobre alertas que no llegan, cuotas diarias, acceso a la ubicación y a la cámara, copias de seguridad y la vista AR."
permalink: /es/faq
translation_key: faq
lang: es
source_hash: e74e7dee3a2bd4e0
---
# Preguntas frecuentes y solución de problemas
{: #faq }

## No recibo alertas
{: #no-alerts }

Comprueba esto en orden:

1. **Notificar está activado.** Los seguimientos nuevos empiezan con las notificaciones desactivadas. Abre el seguimiento y cambia **Notificar: desactivado** a **Notificar: activado**.
2. **Android permite las notificaciones de la app.** Abre los ajustes de Android › **Aplicaciones** › **airplanes.live** › **Notificaciones**, permítelas y comprueba que el canal "Alertas de lista de seguimiento" está activado.
3. **El seguimiento se comprueba.** En la Lista de seguimiento, un seguimiento marcado como "No comprobado, por encima de tu límite" supera el límite del plan gratuito. Consulta [Límites](/es/watchlist#limits). Un seguimiento marcado como "Esperando al servidor", o una lista que dice "Los seguimientos no se vigilan en este momento", no se comprueba hasta que la app llega al servidor.
4. **Ha aparecido algo nuevo.** Una alerta llega cuando un seguimiento pasa de no encontrar nada a encontrar algo. Si una aeronave ya coincidía la primera vez que se comprobó el seguimiento, esa coincidencia no genera aviso. La siguiente alerta llega cuando haya desaparecido y vuelva a aparecer.
5. **Android deja que la app se ejecute en segundo plano.** La app obtiene las alertas cuando se sincroniza con el servidor, cada 60 minutos de forma predeterminada. Los ahorradores de batería pueden retrasar aún más la sincronización. Si las alertas llegan tarde, configura el uso de batería de la app como **Sin restricciones** en los ajustes de aplicaciones de Android.

## ¿Por qué el mapa muestra solo algunas aeronaves?
{: #map-capped }

Con el zoom alejado, el mapa muestra solo una parte de las aeronaves visibles y lo indica en la parte superior. Acerca el zoom para verlas todas. Si el mapa dice "Cuota diaria del mapa agotada", la cuota del plan gratuito para hoy se ha gastado. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).

## ¿Qué significa "Cuota diaria de búsqueda agotada"?
{: #allowance }

El plan gratuito permite un número de búsquedas y consultas al día. El aviso indica cuándo se reinicia. Vincular un alimentador aumenta mucho las cuotas. Consulta [Gratis y Pro](/es/getting-started#free-and-pro).

## ¿Por qué falta una aeronave?
{: #missing-aircraft }

airplanes.live solo ve las aeronaves que están al alcance de un alimentador y emiten ADS-B o se localizan por MLAT. La cobertura es escasa sobre los océanos y en algunas regiones, y las aeronaves en tierra o que vuelan bajo son más difíciles de recibir. Puedes ayudar [montando un alimentador](https://airplanes.live/get-started/).

## ¿Por qué la app pide mi ubicación?
{: #location }

Solo para las funciones que la necesitan: **Mi ubicación** en el mapa, ordenar los resultados de búsqueda por distancia, buscar **Cercanos** y **Usar mi ubicación** al añadir un seguimiento de ubicación usan tu ubicación aproximada. La vista AR necesita tu ubicación precisa y la cámara. Todo lo demás funciona sin acceso a la ubicación.

## ¿Cómo paso mis seguimientos y alimentadores a un teléfono nuevo?
{: #backup }

Abre **Configuración** › **Copia de seguridad y restauración** y toca **Crear copia de seguridad**. Elige qué incluir: **Vigilancias** y **Alimentadores** con sus estadísticas. El historial de las vigilancias no forma parte de la copia de seguridad, y al restaurar una copia de seguridad antigua se omite el historial de comprobaciones que contenga. La app guarda un archivo `.apl.zip`. En el teléfono nuevo, instala la app y usa **Restaurar copia de seguridad** con ese archivo.

El acceso del alimentador no forma parte de la copia de seguridad. En el teléfono nuevo, [vincula tu alimentador](/es/getting-started#link-feeder) de nuevo para obtener Pro.

La restauración solo está garantizada con la misma versión de la app, y la app avisa cuando las versiones no coinciden. Una copia de seguridad hecha con una versión más nueva puede necesitar que actualices la app antes de restaurarla.

## Las etiquetas AR apuntan en la dirección equivocada
{: #ar-calibration }

Tienes que calibrar la brújula del teléfono. En la vista AR, toca **Ayuda** y mueve el teléfono dibujando un 8 varias veces. Mantente lejos de metales e imanes, como un coche o una funda con imán. La [guía de calibración](https://support.google.com/maps/answer/2839911) de Google tiene más consejos.

## Mi alimentador está desconectado pero no recibí ninguna notificación
{: #feeder-offline }

La app avisa cuando airplanes.live lleva 48 horas sin ver el alimentador, así que los cortes breves no la activan. Comprueba también que **Notificar cuando esté desconectado** esté activado para ese alimentador, que la notificación no se haya pospuesto y que Android permita las notificaciones de la app. Consulta [Avisos de desconexión](/es/feeders#offline).
