# Plan de Solución: Errores de Navegación y Cierre de App

Este plan aborda los cierres inesperados (crashes) que ocurren al navegar por la aplicación en el emulador de Android TV.

## Problemas Identificados

### Crash `IllegalStateException: LayoutCoordinate operations are only valid when isAttached is true`
- **Causa**: El sistema de foco de Compose intenta buscar el siguiente elemento (traversal) mientras una pantalla se está desmontando durante una transición de `AnimatedContent`.
- **Efecto**: La aplicación se cierra inmediatamente.
- **Ubicación**: Principalmente en transiciones entre `Home`, `Search` y `Detail`.

### Conflictos de `requestFocus`
- Múltiples `LaunchedEffect` en diferentes pantallas intentan robar el foco al mismo tiempo, especialmente durante los 200ms iniciales de carga.

## Cambios Propuestos

### Arquitectura de Navegación

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Lenovo/OneDrive/Documentos/Proyectos/APK-AnimeTV/android-app/app/src/main/java/com/animetv/app/MainActivity.kt)
- Utilizar `AnimatedContent` con una propiedad de foco controlada.
- Aplicar `Modifier.focusProperties { canFocus = isVisible }` a las pantallas dentro de la transición para que el sistema de foco ignore las pantallas que están saliendo.
- Limpiar el stack de navegación para evitar bucles de foco.

### Estabilización de Pantallas

#### [MODIFY] [HomeScreen.kt](file:///C:/Users/Lenovo/OneDrive/Documentos/Proyectos/APK-AnimeTV/android-app/app/src/main/java/com/animetv/app/ui/screens/HomeScreen.kt)
- Añadir verificaciones de seguridad antes de ejecutar `animateScrollToItem`.
- Coordinar el foco inicial con `MainActivity` para evitar "guerras de foco".

#### [MODIFY] [DetailScreen.kt](file:///C:/Users/Lenovo/OneDrive/Documentos/Proyectos/APK-AnimeTV/android-app/app/src/main/java/com/animetv/app/ui/screens/DetailScreen.kt)
- Ajustar el tiempo de `delay` en el foco inicial para asegurar que la transición haya terminado.

## Plan de Verificación

### Pruebas Automatizadas
- Ejecutar `gradlew app:assembleDebug` para asegurar que no hay errores de sintaxis tras los cambios de arquitectura.

### Verificación Manual
- Navegar rápidamente entre las pestañas `Inicio`, `Buscar` y `Mi Lista`.
- Entrar y salir repetidamente de la pantalla de detalles de varios animes.
- Verificar que el control remoto (D-Pad) responde siempre de forma predecible sin cerrar la app.
