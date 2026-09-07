# Futbol Offline ⚽

Juego de fútbol 2D para Android pensado para funcionar completamente **sin internet**.

## Controles
- Joystick táctil: mover al jugador.
- Botón **TIRO**: patear cuando estás cerca de la pelota.
- **REINICIAR**: comenzar otro partido.

## Incluye
- Jugador controlable.
- Rival con IA básica.
- Arquero automático.
- Física simple de pelota.
- Detección de goles.
- Marcador y cronómetro de 90 segundos.
- Funcionamiento offline, sin cuentas ni servidor.

## Generar APK
Cada push a `main` ejecuta GitHub Actions. Al finalizar:
1. Abre la pestaña **Actions** del repositorio.
2. Entra al último workflow `Build APK`.
3. Descarga el artefacto **FutbolOffline-debug-apk**.
4. Descomprime el ZIP y encontrarás `app-debug.apk`.

También puede compilarse localmente con Gradle mediante `gradle :app:assembleDebug`.

> Primera versión funcional; el proyecto está preparado para seguir agregando equipos, pases, sprint, penales, sonido y mejores gráficos.
