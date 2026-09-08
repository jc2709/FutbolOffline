# Futbol Offline ⚽

Juego de fútbol 2D para Android pensado para funcionar completamente **sin internet**.

## Controles
- **Joystick táctil**: mover al jugador controlado.
- **TIRO**: disparar al arco cuando tienes la pelota o estás muy cerca de ella.
- **PASE**: enviar la pelota a un compañero. Cuando el compañero recibe el pase, el control cambia automáticamente a ese jugador.
- **FUERZA**: empujar al rival cercano. Si tenía la pelota, puede perderla y queda desestabilizado durante un instante. El botón tiene cooldown para evitar uso continuo.

## Posesión y juego
- Cuando un jugador obtiene la posesión, la pelota queda delante de él y se mueve con su conducción.
- La pelota se suelta al tirar, pasar, recibir una entrada o perder la posesión.
- Los compañeros se colocan de forma automática y pueden ir a buscar balones divididos.
- Los rivales tienen IA básica para presionar, conducir hacia tu arco y disparar.
- El jugador controlado se marca con un aro blanco.

## Configuración del partido
Antes de jugar aparece un menú donde puedes modificar:
- **Tiempo**: entre 30 y 300 segundos, en pasos de 30 segundos.
- **Prórroga**: si hay empate, añade un tiempo extra de al menos 30 segundos.
- **Gol de oro**: el primer gol durante la fase de gol de oro termina el partido. Si activas prórroga y gol de oro, el primer gol de la prórroga decide el partido.
- **Jugadores de campo**: de 1 a 5 por equipo. Los arqueros son automáticos y no cuentan dentro de ese límite.

## Incluye
- Hasta 5 jugadores de campo por equipo.
- Arqueros automáticos.
- Posesión y conducción de balón.
- Pase y cambio automático de jugador receptor.
- Acción de fuerza/desestabilización.
- IA rival básica.
- Física simple de pelota y protección contra atascos en las esquinas.
- Detección de goles, marcador, cronómetro, prórroga y gol de oro.
- Funcionamiento offline, sin cuentas ni servidor.

## Generar APK
Cada push a `main` ejecuta GitHub Actions. Al finalizar:
1. Abre la pestaña **Actions** del repositorio.
2. Entra al último workflow `Build APK`.
3. Descarga el artefacto **FutbolOffline-debug-apk**.
4. Descomprime el ZIP y encontrarás `app-debug.apk`.

También puede compilarse localmente con Gradle mediante `gradle :app:assembleDebug`.

> El proyecto sigue siendo una versión ligera 2D y está preparado para continuar mejorando IA, animaciones, sonido, formaciones, faltas y gráficos.
