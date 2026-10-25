# Quickstart — F02 Viabilidad on-device

Guía para validar F02 de principio a fin en el teléfono de referencia. Los detalles están en [research.md](research.md), [data-model.md](data-model.md) y [contracts/](contracts/).

## Requisitos previos

- **Entorno:**
  - Android Studio con JBR 21:
    ```bash
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    ```
  - adb en el `PATH`, o `$ANDROID_HOME/platform-tools/adb`.
- **Teléfono:**
  - Nothing Phone (1), serial `P31281001066`, con depuración USB activada;
  - pantalla desbloqueada durante la sesión.
- **Espacio libre:**
  - ≈ 5,6 GB en el ordenador;
  - ≈ 8,5 GB en el teléfono: los 5,6 GB de modelos más el pico de una copia durante la importación.

## 1. Build y tests sin modelos (cualquier dispositivo o emulador)

```bash
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest          # emulador Pixel_10 o teléfono
```

**Esperado:**
- todo en verde;
- los tests `@RequiresModels` aparecen como *skipped* (asunción no cumplida), no como fallidos.

## 2. Release sin diagnóstico

```bash
./gradlew :app:assembleRelease :app:lintRelease
```

**Esperado** al comprobar el APK release con `apkanalyzer` (mismo procedimiento que F01, P6):
- ninguna clase `…debug.feasibility.*` ni `DevModelImporter`;
- sin permisos `INTERNET` ni `ACCESS_NETWORK_STATE`;
- `lib/arm64-v8a/liblitertlm_jni.so` presente; sin `x86_64`;
- `uses-native-library` `libOpenCL.so` con `required=false`.

## 3. Aprovisionar los modelos

Seguir [contracts/provisioning.md](contracts/provisioning.md):
1. Descargar en `$HOME/MicroRAG-models`.
2. Comprobar con `shasum -a 256` contra research R2.
3. Copiar con `adb push` a `dev-models`.

Antes de copiar los 5,6 GB, probar con un fichero pequeño que el shell puede escribir en `Android/data` (R7):

```bash
adb -s P31281001066 shell 'mkdir -p /sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models && echo ok > /sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models/probe.txt && cat /sdcard/Android/data/com.lisdatasolutions.microrag/files/dev-models/probe.txt'
```

## 4. Importar y verificar (US1)

Abrir «MicroRAG Debug → Viabilidad on-device → Importar modelos de desarrollo».

**Esperado:**
- cada fichero pasa por «Verificando», con progreso real en porcentaje, y termina en «Verificado»;
- la carpeta `dev-models` queda vacía.

**Prueba negativa:**
1. Copiar a `dev-models` un fichero con el nombre de `embeddinggemma-2-text-270m.litertlm` pero con otro contenido. Por ejemplo, los primeros 10 MB del fichero real:
   ```bash
   head -c 10485760 ... > /tmp/x
   ```
2. Pulsar Importar.

**Esperado:**
- aparece «Huella incorrecta» y no hay botón de prueba para ese modelo;
- `no_backup/models` no contiene el fichero;
- después se vuelve a copiar el fichero correcto.

## 5. Ejecutar la batería de pruebas

### Opción A: desde la UI

Con el teléfono enchufado o no, como prefieras (el informe lo registra), pulsar «Batería completa», o cada bloque por separado. Durante la ejecución la pantalla se mantiene encendida.

**Esperado:**
- progreso real por combinación y repetición;
- «Cancelar» detiene la prueba en menos de 1 s;
- al terminar se guarda el informe y se muestra su ruta.

### Opción B: test instrumentado (reproducible, sin tocar el teléfono)

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb -s P31281001066 shell am instrument -w \
  -e annotation com.lisdatasolutions.microrag.debug.feasibility.RequiresModels \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

`am instrument` se usa en lugar de Gradle para evitar los tiempos máximos de Gradle en una sesión larga.

## 6. Recuperar el informe (US4)

```bash
adb -s P31281001066 exec-out run-as com.lisdatasolutions.microrag ls files/reports
adb -s P31281001066 exec-out run-as com.lisdatasolutions.microrag cat files/reports/feasibility-<ts>.md \
  > specs/002-ondevice-feasibility/benchmarks.md
adb -s P31281001066 exec-out run-as com.lisdatasolutions.microrag cat files/reports/feasibility-<ts>.json \
  > specs/002-ondevice-feasibility/benchmarks.json
```

**Esperado:** el informe contiene:
- 6 combinaciones de embeddings;
- 3 de compatibilidad;
- 2 ficheros de Gemma en GPU, más CPU si alguno falló;
- los criterios de salida.

Formato: [contracts/report-format.md](contracts/report-format.md).

## 7. Comprobaciones de recursos (US5)

- **GPU:** durante la batería completa, el registro de debug (`adb logcat -s MicroRAG`) muestra `engine release … GPU` antes de cada `engine load … GPU`. El test JVM del coordinador lo garantiza con motores falsos.
- **Segundo plano:** con una prueba en curso, pulsar Inicio. La prueba queda «Cancelada» y `loaded` vuelve vacío. Se comprueba al volver a la app.
- **Memoria:** el informe incluye la memoria pico por prueba. No debe haber ningún cierre por falta de memoria (`adb logcat -b crash` vacío).

## 8. Cierre de la feature

1. Registrar en `docs/roadmap/DECISIONS.md`, con las cifras de `benchmarks.md`:
   - fichero de Gemma (G35);
   - backend de la consulta;
   - variantes para F05 (G36);
   - compatibilidad CPU↔GPU.
2. Si ninguna variante cumple < 150 ms p95 en CPU (SC-004), **parar y decidir con el usuario**.
