---
title: Primeros pasos
description: "Instala la app de airplanes.live, aprende a moverte por sus pestañas y descubre qué significan Gratis y Pro."
permalink: /es/getting-started
translation_key: getting-started
lang: es
source_hash: 4c729d906546b537
---
# Primeros pasos
{: #getting-started }

La app oficial de airplanes.live muestra el tráfico aéreo en directo de [airplanes.live](https://airplanes.live), una comunidad de personas que reciben señales de aeronaves (ADS-B y MLAT) con sus propias antenas y las comparten. Funciona en Android 8.0 y versiones posteriores.

## Instalar
{: #install }

Descarga el APK desde [GitHub releases](https://github.com/d4rken-org/airplanes-live-app/releases/latest) o instálalo desde [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.darken.apl/) con un cliente de F-Droid.

Las versiones de GitHub pueden buscar nuevas versiones por sí mismas. Activa **Verificación de actualizaciones** durante la configuración inicial o más tarde en **Configuración** › **General**. Viene desactivada de forma predeterminada.

## Primer inicio
{: #first-launch }

La app muestra una breve bienvenida y su resumen de privacidad: al aceptar, la app registra esta instalación en el servidor del desarrollador con un ID aleatorio y le envía lo que pides, como la zona del mapa que miras, tus búsquedas, vigilancias y alimentadores, y tu ubicación cuando una función la necesita. Necesita acceso a internet. Toca **Aceptar** para continuar. Puedes leer la [política de privacidad](/privacy) completa en cualquier momento desde **Configuración** › **Política de privacidad**.

## Moverse por la app
{: #tabs }

La barra inferior cambia entre:

- **Mapa**: aeronaves en directo a tu alrededor o en cualquier parte del mundo. Consulta [Mapa](/es/map).
- **Búsqueda**: encuentra aeronaves por indicativo, matrícula, código hex, tipo o squawk. Consulta [Búsqueda](/es/search).
- **Lista de seguimiento**: las aeronaves, vuelos, códigos squawk y áreas que sigues, con alertas opcionales. Consulta [Lista de seguimiento y alertas](/es/watchlist).
- **Alimentador**: los receptores con los que alimentas airplanes.live. Consulta [Alimentadores](/es/feeders). Si no tienes ninguno, puedes ocultar esta pestaña con **Mostrar la pestaña Alimentador** en **Configuración** › **Alimentador**. Ocultarla no detiene la supervisión de los alimentadores.
- **Configuración**: tema, unidades, estilo de mapa, sincronización en segundo plano, copias de seguridad y soporte.

Una barra roja con el texto "Internet no está disponible." significa que la app no puede conectarse a la red. Las posiciones y los resultados de búsqueda se quedan entonces en su último estado.

## Gratis y Pro
{: #free-and-pro }

La app es gratuita. airplanes.live lo gestiona la comunidad: su cobertura viene de personas que instalan una antena y ayudan a pagar los servidores, así que el acceso completo es para ellas. La etiqueta junto al nombre de la app en el mapa muestra **GRATIS** o **PRO**. Tócala para abrir **Estado de la mejora**.

En el plan gratuito se aplican algunos límites, por ejemplo una cuota diaria de búsquedas y datos del mapa y un número limitado de seguimientos. La app te avisa cuando llegas a uno.

Pro desbloquea:

- Resultados de búsqueda sin límite
- Cuotas diarias mucho más altas para búsquedas y datos de aeronaves
- Actualizaciones más rápidas, en el mapa y en los seguimientos
- Seguimientos ilimitados
- Seguimientos de squawk y de ubicación
- Un radio mayor para la búsqueda cercana y la vista AR
- Historial de aeronaves
- Modo, estilo y colores del tema, incluidos los tuyos

### Vincula tu alimentador
{: #link-feeder }

Si alimentas airplanes.live, vincula tu alimentador y esta instalación pasa a Pro:

1. Conecta tu teléfono a la misma red que tu alimentador, por ejemplo el Wi-Fi de tu casa.
2. Abre **Estado de la mejora** y toca **Detectar** para encontrar el alimentador en esta red, o **Introducir ID** para escribir su [ID del alimentador](/es/feeders#feeder-id).
3. Toca **Vincular**.

Solo tienes que vincularlo una vez desde la red de tu alimentador. Después, Pro funciona en cualquier lugar. La vinculación necesita una conexión IPv4, porque así se reconoce tu alimentador. Si falla con "Ese alimentador no está en esta red", comprueba que tu teléfono no esté usando datos móviles o una VPN.

La vinculación es por instalación. Si reinstalas la app o cambias de teléfono, vuelve a vincularlo; una [copia de seguridad](/es/faq#backup) no la conserva.

¿Todavía no alimentas? **Cómo alimentar** en la pantalla de mejora te lleva a la [guía de configuración de airplanes.live](https://airplanes.live/get-started/).

## Configuración
{: #settings }

- **General**: modo, estilo y color del tema (Pro), unidades de aeronaves y de distancia, **Sincronización en segundo plano** y la verificación de actualizaciones en las versiones de GitHub.
- **Mapa**: **Restaurar última vista** y el **Estilo de mapa**.
- **Alimentador**: cuando el servidor de la app supervisa tus alimentadores, **Alertas de problemas** activa o desactiva los avisos de problemas del servidor. **Mostrar la pestaña Alimentador** muestra u oculta la pestaña Alimentador.
- **Copia de seguridad y restauración**: exporta e importa tus seguimientos y alimentadores. Consulta [Copias de seguridad](/es/faq#backup).
- **Soporte**: estas guías, el gestor de incidencias, Discord y el **Registro de depuración**. Consulta [Informar de problemas](/es/bugs).
