# Futbol Offline ⚽

Juego de fútbol 2D para Android pensado para funcionar completamente **sin internet**.

## Controles
- **Joystick táctil**: mover al jugador controlado.
- **TIRO / DESPEJE**: en la mitad rival funciona como tiro al arco; en tu propia mitad funciona como despeje fuerte hacia adelante.
- **PASE**: envía la pelota a un compañero aunque esté separado. El receptor corre hacia la trayectoria del balón y, cuando controla el pase, el control cambia automáticamente a él.

## Posesión y juego
- Cuando un jugador obtiene la posesión, la pelota queda delante de él y se mueve con su conducción.
- Llevar la pelota reduce la velocidad del portador al 78% de su velocidad normal.
- Jugadores propios y rivales comparten la misma velocidad base.
- Ya no existe un botón de fuerza: los choques físicos entre rivales separan a los jugadores y pueden provocar que el portador pierda la pelota.
- Existe una protección breve después de obtener la posesión para evitar pérdidas instantáneas y rebotes infinitos de balón.
- Los compañeros se posicionan automáticamente y el receptor seleccionado persigue la trayectoria de un pase.
- La IA rival orienta al portador hacia tu arco al recibir la pelota, incluso si la recibe de espaldas, y avanza antes de decidir el disparo.
- El jugador controlado se marca con un aro blanco.

## Configuración del partido
Antes de jugar aparece un menú donde puedes modificar:
- **Tiempo**: entre 30 y 300 segundos, en pasos de 30 segundos.
- **Prórroga**: si hay empate, añade tiempo extra.
- **Gol de oro**: el siguiente gol decide el partido. Si activas prórroga y gol de oro, el primer gol de la prórroga termina el encuentro.
- **Jugadores de campo**: de 1 a 5 por equipo. Los arqueros son automáticos y no cuentan dentro de ese límite.

## Incluye
- Hasta 5 jugadores de campo por equipo.
- Arqueros automáticos.
- Posesión y conducción de balón.
- Pase con receptor automático y cambio de jugador.
- Choques y pérdida de posesión por contacto.
- Tiro en campo rival y despeje en campo propio.
- Velocidad equilibrada entre ambos equipos y penalización al conducir.
- IA rival básica con corrección de orientación al recibir.
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
