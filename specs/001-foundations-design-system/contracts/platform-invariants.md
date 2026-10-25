# Invariantes de plataforma — F01

Estas propiedades se fijan en F01 y **ninguna feature posterior puede romperlas** sin enmendar la constitución. La excepción es el permiso de red, que llegará en F03 aislado en el componente de descarga (constitución I).

| ID | Invariante | Requisito | Verificación |
|---|---|---|---|
| P1 | `minSdk = 31`, `targetSdk = 36`, `compileSdk = 37.1` | FR-029, D-017, D-020 | `apkanalyzer manifest min-sdk` + revisión de `app/build.gradle.kts` |
| P2 | `android:allowBackup="false"` | FR-026 | Manifiesto fusionado |
| P3 | `dataExtractionRules`: `<cloud-backup>` y `<device-transfer>` excluyen todos los dominios | FR-026, R9 | Manifiesto + `res/xml/data_extraction_rules.xml` + `adb shell bmgr backupnow` sin datos |
| P4 | Sin permisos de red ni permisos normales o peligrosos. Solo `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (nivel firma, AndroidX) | FR-025, R10 | Test instrumentado `ManifestInvariantsTest` (lista blanca de `requestedPermissions`) + `apkanalyzer manifest permissions` |
| P5 | Sin `<intent-filter>` con `VIEW`/`BROWSABLE` ni `deepLinks` | FR-023 | `ManifestInvariantsTest` sobre las actividades exportadas. En debug también existe `DebugLauncherActivity`, que solo tiene `MAIN`/`LAUNCHER` |
| P6 | Release sin catálogo, sin menú de rutas y sin `DebugLauncherActivity` | FR-011, FR-024, R7 | `apkanalyzer dex packages` del APK de release no contiene `…debug.catalog` + manifiesto de release |
| P7 | Release sin logs de depuración (`AppLogger` sujeto a `BuildConfig.DEBUG`) | FR-027, R11 | Test unitario de `AppLogger` + revisión. Las reglas de R8 se añaden en F15 |
| P8 | `abiFilters`: `arm64-v8a` (+ `x86_64` solo en debug) | FR-028, R12 | Revisión de la configuración en F01. Contenido del APK en F02 |
| P9 | Tema sin color dinámico; fondo de ventana y splash con `background` en claro y oscuro | FR-002, R16 | Test de tema + revisión visual |
| P10 | `app_name` = «MicroRAG» (es/en) | FR-033, D-014 | `ManifestInvariantsTest` (etiqueta) |
