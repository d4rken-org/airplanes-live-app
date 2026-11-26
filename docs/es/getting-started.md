---
title: Primeros pasos
description: "Instala la app de airplanes.live, aprende a moverte por sus pestañas y descubre qué significan Gratis y Pro."
permalink: /es/getting-started
translation_key: getting-started
lang: es
source_hash: bfac366b301cb8e3
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

La app se abre con tres páginas breves: qué es airplanes.live, cómo funcionan Gratis y Pro, y un resumen de privacidad. Al aceptar, la app registra esta instalación en el servidor del desarrollador con un ID aleatorio y le envía lo que pides, como la zona del mapa que miras, tus búsquedas, vigilancias y receptores, y tu ubicación cuando una función la necesita. Necesita acceso a internet. Toca **Aceptar** para continuar. Puedes leer la [política de privacidad](/privacy) completa en cualquier momento desde **Configuración** › **Política de privacidad**.

## Moverse por la app
{: #tabs }

La barra inferior cambia entre:

- **Mapa**: aeronaves en directo a tu alrededor o en cualquier parte del mundo. Consulta [Mapa](/es/map).
- **Búsqueda**: encuentra aeronaves por indicativo, matrícula, código hex, tipo o squawk. Consulta [Búsqueda](/es/search).
- **Seguimiento**: las aeronaves, vuelos, códigos squawk y áreas que sigues, con alertas opcionales. Consulta [Lista de seguimiento y alertas](/es/watchlist).
- **Receptor**: los receptores con los que alimentas airplanes.live. Consulta [Receptores](/es/feeders). Si no tienes ninguno, puedes ocultar esta pestaña con **Mostrar la pestaña Receptor** en **Configuración** › **Receptor**. Ocultarla no detiene la supervisión de los receptores.

Toca el engranaje de la barra superior de la pestaña Mapa para abrir **Configuración**: tema, unidades, estilo de mapa, sincronización en segundo plano, copias de seguridad y soporte. Consulta [Configuración](#settings).

Una barra roja con el texto "Internet no está disponible." significa que la app no puede conectarse a la red. Las posiciones y los resultados de búsqueda se quedan entonces en su último estado.

## Gratis y Pro
{: #free-and-pro }

La app es gratuita. airplanes.live lo gestiona la comunidad: su cobertura viene de personas que instalan una antena y ayudan a pagar la infraestructura, así que el acceso completo es para ellas. En la versión de Google Play también puedes obtener Pro con una [suscripción](#subscribe). La etiqueta junto al nombre de la app en el mapa muestra **GRATIS** o **PRO**. Tócala para abrir **Estado de la mejora**.

En el plan gratuito se aplican algunos límites, por ejemplo una cuota diaria de búsquedas y datos del mapa y un número limitado de seguimientos. La app te avisa cuando llegas a uno.

Pro desbloquea:

- Resultados de búsqueda sin límite y muchas más búsquedas y consultas al día
- Actualizaciones más rápidas, en el mapa y en los seguimientos
- Muchos más seguimientos, incluidos los de squawk y de ubicación
- Un radio mayor para la búsqueda cercana y la vista AR
- Historial de aeronaves
- Modo, estilo y colores del tema, incluidos los tuyos
- Estilos de mapa adicionales
- Capas de mapa adicionales

### Vincula tu receptor
{: #link-feeder }

Si alimentas airplanes.live, vincula tu receptor y esta instalación pasa a Pro:

1. Conecta tu teléfono a la misma red que tu receptor, por ejemplo el Wi-Fi de tu casa.
2. Abre **Estado de la mejora** y toca **Detectar** para encontrar el receptor en esta red, o **Introducir ID** para escribir su [ID del alimentador](/es/feeders#feeder-id).
3. Toca **Vincular**.

Solo tienes que vincularlo una vez desde la red de tu receptor. Después, Pro funciona en cualquier lugar. La vinculación necesita una conexión IPv4, porque así se reconoce tu receptor. Si falla con "Ese receptor no está en esta red", comprueba que tu teléfono no esté usando datos móviles o una VPN.

La vinculación es por instalación. Si reinstalas la app o cambias de teléfono, vuelve a vincularlo; una [copia de seguridad](/es/faq#backup) no la conserva.

¿Todavía no alimentas? **Cómo alimentar** en la pantalla de mejora te lleva a la [guía de configuración de airplanes.live](https://airplanes.live/get-started/).

### Suscribirse
{: #subscribe }

En la versión de Google Play también puedes obtener Pro con una suscripción mensual. Ayuda a cubrir los costes de infraestructura.

1. Abre **Estado de la mejora**. La tarjeta **Get Pro** muestra el precio y las condiciones de la suscripción, y una prueba gratuita cuando Google Play te la ofrece.
2. Toca **Subscribe**, o **Start free trial** cuando se ofrece una prueba, y confirma en Google Play.

En otro dispositivo o después de reinstalar la app, toca **Restore** en la misma tarjeta, con la misma cuenta de Google en Google Play. **Manage** abre Google Play, donde puedes cambiar o cancelar la suscripción.

Un receptor vinculado y una suscripción pueden estar activos a la vez. Con un receptor vinculado, esta instalación mantiene Pro sin la suscripción.

## Configuración
{: #settings }

- **General**: modo, estilo y color del tema (Pro), unidades de aeronaves y de distancia, **Sincronización en segundo plano** y la verificación de actualizaciones en las versiones de GitHub.
- **Mapa**: **Restaurar última vista** y el **Estilo de mapa** (Pro, excepto **Automático (claro u oscuro)**).
- **Receptor**: cuando el servidor de la app supervisa tus receptores, **Alertas de problemas** activa o desactiva los avisos de problemas del servidor. **Mostrar la pestaña Receptor** muestra u oculta la pestaña Receptor.
- **Copia de seguridad y restauración**: exporta e importa tus seguimientos y receptores. Consulta [Copias de seguridad](/es/faq#backup).
- **Soporte**: estas guías, el gestor de incidencias, Discord y el **Registro de depuración**. Consulta [Informar de problemas](/es/bugs).
