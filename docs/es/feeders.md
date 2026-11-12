---
title: Alimentadores
description: "Añade tu alimentador de airplanes.live, sigue sus tasas de mensajes y recibe un aviso cuando se desconecte."
permalink: /es/feeders
translation_key: feeders
lang: es
source_hash: 6e4f9db68d11263d
---
# Alimentadores
{: #feeders }

Un alimentador es un receptor, normalmente una Raspberry Pi con una antena, que envía a airplanes.live las señales de aeronaves que recibe. La pestaña **Alimentador** sigue tus alimentadores y te avisa cuando uno se desconecta. Para montar uno, consulta la [guía de configuración de airplanes.live](https://airplanes.live/get-started/).

Añadir un alimentador aquí y [vincularlo](/es/getting-started#link-feeder) para obtener Pro son pasos independientes. Puedes hacer uno sin el otro.

## Encuentra el ID de tu alimentador
{: #feeder-id }

Cada alimentador tiene un ID, un UUID como `3f2c8a1e-9b7d-4e2a-8c1f-5d6e7a8b9c0d`. En un alimentador configurado con el script de alimentación de airplanes.live, muéstralo con:

```
cat /usr/local/share/airplanes/airplanes-uuid
```

En la imagen de alimentador de airplanes.live, el archivo es `/boot/airplanes-uuid`.

## Añadir un alimentador
{: #add }

<a class="guide-shot" href="/assets/images/feeders/add-feeder.webp"><img src="/assets/images/feeders/add-feeder.webp" width="540" height="1200" loading="lazy" alt="La pantalla para añadir un alimentador, con los campos de ID, etiqueta, dirección IP y posición"></a>

Toca el botón **+** en la pestaña Alimentador. Hay tres formas de rellenar **Nuevo alimentador**:

- **Detectar Alimentador Local**: encuentra alimentadores en la misma red que tu teléfono y rellena los campos. Si encuentra varios, elige uno.
- **Escanear código QR**: escanea un código QR de alimentador generado por la app en otro dispositivo, consulta [Compartir un alimentador](#share).
- Escribe tú mismo el **ID del alimentador**.

Campos opcionales:

- **Etiqueta**: un nombre para el alimentador. Si no pones ninguno, la app usa el nombre MLAT del alimentador en airplanes.live o los últimos cinco caracteres de su ID.
- **Dirección IP**: donde se ejecuta tar1090 o graphs1090 para este alimentador, como `192.168.0.42`. La app muestra entonces botones para abrirlos.
- **Posición**: latitud y longitud, como `50.8006, 6.0619`.

## Lista de alimentadores
{: #list }

La cabecera muestra si todos los alimentadores están conectados. Cada alimentador muestra su estado. Cuando el servidor de la app supervisa tus alimentadores, cada alimentador muestra además su tasa de mensajes Beast (MSG/s), su tasa de datos (KBit/s) y sus estadísticas de MLAT con la proporción de valores atípicos y el número de pares. Sin la supervisión del servidor, un alimentador muestra "Monitoring unavailable" y ninguna tasa. Ordena la lista por **Etiqueta** o **Tasa de mensajes**.

Cuando el servidor de la app supervisa tus alimentadores, el estado viene del servidor, por ejemplo "Esperando la primera comprobación", "Desconectado desde …" o "Posible caída de airplanes.live". Los problemas que encontró el servidor aparecen debajo, como "falta el cliente MLAT". El plan gratuito supervisa menos alimentadores que con un alimentador vinculado. Los que superan ese número muestran "No supervisado, límite de feeders alcanzado" y un botón **Aumentar límite** para [vincular tu alimentador](/es/getting-started#link-feeder).

Toca un alimentador para ver más:

- **Mostrar alimentación en el mapa**: abre globe.airplanes.live en tu navegador y muestra lo que recibe este alimentador.
- **Renombrar** y **Establecer dirección IP**.
- **TAR1090** y **GRAPHS1090**: abren las páginas web propias del alimentador. Aparecen cuando hay una dirección IP configurada.
- **Monitoreo**: el estado y los problemas de este alimentador según el servidor, y las comprobaciones que aún no tienen suficientes datos. Sin la supervisión del servidor, indica "Monitoring unavailable".
- **Estadísticas**: gráficos de la tasa de mensajes, los pares MLAT y los valores atípicos MLAT del historial por horas del servidor. Solo aparece cuando el servidor supervisa tus alimentadores.
- **Generar código QR**, ver más abajo.
- **Notificar cuando esté desconectado**, ver más abajo.
- **Eliminar alimentador**.

## Avisos de desconexión
{: #offline }

**Notificar cuando esté desconectado** define cuánto tiempo puede estar desconectado un alimentador antes de que recibas una notificación: **Desactivado**, 6, 12, 24 o 48 horas. Los alimentadores nuevos empiezan con 48 horas. Al activarlo, se pide el permiso de notificaciones en Android 13 y versiones posteriores.

Cuando el servidor de la app lo ofrece, supervisa tus alimentadores, también con la app cerrada. Un alimentador se considera desconectado cuando no ha tenido conexión con airplanes.live durante el tiempo elegido, confirmado por al menos dos comprobaciones. El servidor no da alimentadores por desconectados mientras airplanes.live tiene problemas o muchos alimentadores se caen a la vez. Recibes entonces "Alimentador desconectado", y "Alimentador conectado de nuevo" cuando vuelve.

El servidor también busca problemas y envía "Problemas del alimentador" cuando encuentra uno:

- falta el cliente MLAT
- sin pares MLAT
- errores de sincronización MLAT
- muchos valores atípicos MLAT
- recibe muchos menos mensajes de lo habitual
- se reconecta repetidamente
- ID de alimentador usado por varios alimentadores

Desactívalos en **Configuración** › **Alimentador** › **Alertas de problemas**.

Las notificaciones del servidor llegan con la sincronización en segundo plano de la app, así que pueden tardar tanto como indique el nivel elegido en **Configuración** › **General** › **Sincronización en segundo plano**. Consulta [Con qué frecuencia se sincroniza la app](/es/watchlist#interval).

Toca **Posponer** en una notificación para silenciar ese alimentador hasta que vuelva a estar conectado. Para los problemas de un alimentador conectado, la pausa dura hasta que se resuelven.

Sin la supervisión del servidor no hay notificaciones de desconexión, y tus alimentadores muestran "Monitoring unavailable".

## Compartir un alimentador
{: #share }

**Generar código QR** muestra un código QR con la configuración del alimentador. Escanéalo con **Escanear código QR** en la app de otro dispositivo para añadir allí el mismo alimentador.
