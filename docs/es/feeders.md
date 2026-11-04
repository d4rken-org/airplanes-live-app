---
title: Alimentadores
description: "Añade tu alimentador de airplanes.live, sigue sus tasas de mensajes y recibe un aviso cuando se desconecte."
permalink: /es/feeders
translation_key: feeders
lang: es
source_hash: a9b4816daaa62c7b
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

Toca el botón **+** en la pestaña Alimentador. Hay tres formas de rellenar **Nuevo alimentador**:

- **Detectar Alimentador Local**: encuentra alimentadores en la misma red que tu teléfono y rellena los campos. Si encuentra varios, elige uno.
- **Escanear código QR**: escanea un código QR de alimentador generado por la app en otro dispositivo, consulta [Compartir un alimentador](#share).
- Escribe tú mismo el **ID del alimentador**.

Campos opcionales:

- **Etiqueta**: un nombre para el alimentador. Si no pones ninguno, la app usa el nombre del alimentador en airplanes.live.
- **Dirección IP**: donde se ejecuta tar1090 o graphs1090 para este alimentador, como `192.168.0.42`. La app muestra entonces botones para abrirlos.
- **Posición**: latitud y longitud, como `50.8006, 6.0619`.

## Lista de alimentadores
{: #list }

La cabecera muestra si todos los alimentadores están conectados. Cada alimentador muestra su tasa de mensajes Beast (MSG/s), su tasa de datos (KBit/s) y sus estadísticas de MLAT con la proporción de valores atípicos y el número de pares. Ordena la lista por **Etiqueta** o **Tasa de mensajes**.

Toca un alimentador para ver más:

- **Mostrar alimentación en el mapa**: abre globe.airplanes.live en tu navegador y muestra lo que recibe este alimentador.
- **Renombrar** y **Establecer dirección IP**.
- **TAR1090** y **GRAPHS1090**: abren las páginas web propias del alimentador. Aparecen cuando hay una dirección IP configurada.
- **Estadísticas**: gráficos de 30 días de las tasas de mensajes y los valores atípicos de MLAT.
- **Generar código QR**, ver más abajo.
- **Notificar cuando esté desconectado**, ver más abajo.
- **Eliminar alimentador**.

## Avisos de desconexión
{: #offline }

**Notificar cuando esté desconectado** está activado en los alimentadores nuevos. Un alimentador se considera desconectado cuando airplanes.live no lo ha visto durante 48 horas. La app muestra entonces "Alimentadores desconectados detectados" con el nombre del alimentador. Al activar este ajuste, se pide el permiso de notificaciones en Android 13 y versiones posteriores.

Toca **Posponer** en la notificación para silenciarla hasta que ese alimentador vuelva a enviar datos. La notificación desaparece sola cuando el alimentador vuelve.

La app comprueba tus alimentadores en segundo plano. Elige la frecuencia en **Configuración** › **Alimentador** › **Intervalo de verificación**, desde 15 minutos hasta un día. El valor predeterminado es cada 60 minutos.

## Compartir un alimentador
{: #share }

**Generar código QR** muestra un código QR con la configuración del alimentador. Escanéalo con **Escanear código QR** en la app de otro dispositivo para añadir allí el mismo alimentador.
