---
title: Receptores
description: "Añade tu receptor para airplanes.live, sigue sus tasas de mensajes y recibe un aviso cuando se desconecte."
permalink: /es/feeders
translation_key: feeders
lang: es
source_hash: 6e4f9db68d11263d
---
# Receptores
{: #feeders }

Un receptor, normalmente una Raspberry Pi con una antena, capta las señales de aeronaves y las envía a airplanes.live. La pestaña **Receptor** sigue tus receptores y te avisa cuando uno se desconecta. Para montar uno, consulta la [guía de configuración de airplanes.live](https://airplanes.live/get-started/).

Añadir un receptor aquí y [vincularlo](/es/getting-started#link-feeder) para obtener Pro son pasos independientes. Puedes hacer uno sin el otro.

## Encuentra el ID de tu alimentador
{: #feeder-id }

Cada receptor tiene un ID de alimentador, un UUID como `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. En un receptor configurado con el script de alimentación de airplanes.live, muéstralo con:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

En la imagen de alimentador de airplanes.live, el archivo es `/boot/airplanes-uuid`.

## Añadir un receptor
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="La pantalla para añadir un receptor, con los campos de ID, etiqueta, dirección IP y posición"></a>

Toca el botón **+** en la pestaña Receptor. Hay tres formas de rellenar **Nuevo receptor**:

- **Detectar Receptor Local**: encuentra receptores en la misma red que tu teléfono y rellena los campos. Si encuentra varios, elige uno.
- **Escanear código QR**: escanea un código QR de receptor generado por la app en otro dispositivo, consulta [Compartir un receptor](#share).
- Escribe tú mismo el **ID del alimentador**.

Campos opcionales:

- **Etiqueta**: un nombre para el receptor. Si no pones ninguno, la app usa el nombre MLAT del receptor en airplanes.live o los últimos cinco caracteres de su ID.
- **Dirección IP**: donde se ejecuta tar1090 o graphs1090 para este receptor, como `192.168.0.42`. La app muestra entonces botones para abrirlos.
- **Posición**: latitud y longitud, como `50.8006, 6.0619`.

## Lista de receptores
{: #list }

La cabecera muestra si todos los receptores están conectados. Cada receptor muestra su estado. Cuando el servidor de la app supervisa tus receptores, cada receptor muestra además su tasa de mensajes Beast (MSG/s), su tasa de datos (KBit/s) y sus estadísticas de MLAT con la proporción de valores atípicos y el número de pares. Sin la supervisión del servidor, un receptor muestra "Monitoring unavailable" y ninguna tasa. Ordena la lista por **Etiqueta** o **Tasa de mensajes**.

Cuando el servidor de la app supervisa tus receptores, el estado viene del servidor, por ejemplo "Esperando la primera comprobación", "Desconectado desde …" o "Posible caída de airplanes.live". Los problemas que encontró el servidor aparecen debajo, como "falta el cliente MLAT". El plan gratuito supervisa menos receptores que Pro. Los que superan ese número muestran "No supervisado, límite de receptores alcanzado" y un botón **Aumentar límite** para [vincular tu receptor](/es/getting-started#link-feeder).

Toca un receptor para ver más:

- **Mostrar alimentación en el mapa**: abre globe.airplanes.live en tu navegador y muestra lo que recibe este receptor.
- **Renombrar** y **Establecer dirección IP**.
- **TAR1090** y **GRAPHS1090**: abren las páginas web propias del receptor. Aparecen cuando hay una dirección IP configurada.
- **Monitoreo**: el estado y los problemas de este receptor según el servidor, y las comprobaciones que aún no tienen suficientes datos. Sin la supervisión del servidor, indica "Monitoring unavailable".
- **Estadísticas**: gráficos de la tasa de mensajes, los pares MLAT y los valores atípicos MLAT del historial por horas del servidor. Solo aparece cuando el servidor supervisa tus receptores.
- **Generar código QR**, ver más abajo.
- **Notificar cuando esté desconectado**, ver más abajo.
- **Eliminar receptor**.

## Avisos de desconexión
{: #offline }

**Notificar cuando esté desconectado** define cuánto tiempo puede estar desconectado un receptor antes de que recibas una notificación: **Desactivado**, 6, 12, 24 o 48 horas. Los receptores nuevos empiezan con 48 horas. Al activarlo, se pide el permiso de notificaciones en Android 13 y versiones posteriores.

Cuando el servidor de la app lo ofrece, supervisa tus receptores, también con la app cerrada. Un receptor se considera desconectado cuando no ha tenido conexión con airplanes.live durante el tiempo elegido, confirmado por al menos dos comprobaciones. El servidor no da receptores por desconectados mientras airplanes.live tiene problemas o muchos receptores se caen a la vez. Recibes entonces "Receptor desconectado", y "Receptor conectado de nuevo" cuando vuelve.

El servidor también busca problemas y envía "Problemas del receptor" cuando encuentra uno:

- falta el cliente MLAT
- sin pares MLAT
- errores de sincronización MLAT
- muchos valores atípicos MLAT
- recibe muchos menos mensajes de lo habitual
- se reconecta repetidamente
- ID de alimentador enviado por varios receptores

Desactívalos en **Configuración** › **Receptor** › **Alertas de problemas**.

Las notificaciones del servidor llegan con la sincronización en segundo plano de la app, así que pueden tardar tanto como indique el nivel elegido en **Configuración** › **General** › **Sincronización en segundo plano**. Consulta [Con qué frecuencia se sincroniza la app](/es/watchlist#interval).

Toca **Posponer** en una notificación para silenciar ese receptor hasta que vuelva a estar conectado. Para los problemas de un receptor conectado, la pausa dura hasta que se resuelven.

Sin la supervisión del servidor no hay notificaciones de desconexión, y tus receptores muestran "Monitoring unavailable".

## Compartir un receptor
{: #share }

**Generar código QR** muestra un código QR con la configuración del receptor. Escanéalo con **Escanear código QR** en la app de otro dispositivo para añadir allí el mismo receptor.
