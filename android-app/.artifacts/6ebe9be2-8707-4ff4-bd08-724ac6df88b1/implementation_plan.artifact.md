# Plan de Solución de Errores - Anime TV

Este plan aborda los errores de compilación introducidos por el nuevo componente de navegación `NetflixSideNav`.

## Problemas Identificados

### Errores de Referencia en `NetflixSideNav.kt`
- El componente utiliza iconos de Material Design (`Tv` y `BookmarkBorder`) que no están incluidos en la librería base de iconos.
- Falta la dependencia `androidx.compose.material:material-icons-extended` en el proyecto.

## Cambios Propuestos

### Configuración de Dependencias

#### [MODIFY] [libs.versions.toml](file:///C:/Users/Lenovo/OneDrive/Documentos/Proyectos/APK-AnimeTV/android-app/gradle/libs.versions.toml)
- Añadir la entrada para `material-icons-extended` en la sección de librerías.

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/Lenovo/OneDrive/Documentos/Proyectos/APK-AnimeTV/android-app/app/build.gradle.kts)
- Incluir la nueva dependencia de iconos extendidos.

## Plan de Verificación

### Pruebas Automatizadas
- Ejecutar `gradlew app:assembleDebug` para confirmar que todos los errores de referencia se han resuelto.

### Verificación Manual
- Abrir el archivo `NetflixSideNav.kt` en el editor para asegurar que el IDE ya no marca errores en las importaciones de iconos.
