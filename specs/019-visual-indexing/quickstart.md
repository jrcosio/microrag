# Quickstart: validar la indexación visual (F19)

**Requisitos:**
- `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` para Gradle y `apkanalyzer`;
- los emuladores `Pixel_10` (API 37) y `MicroRAG_API33`;
- el teléfono A063 solo en la última fase, con permiso del usuario.

## 1. Batería sin dispositivo

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
offline/.venv/bin/python -I -m unittest discover -s offline/tests
```

**Esperado:**
- JVM en verde, con las pruebas nuevas de R16;
- lint solo con los 2 avisos de versiones de siempre;
- Python en verde, con la versión 5 del esquema.

## 2. Regresión del texto (FR-025, SC-001)

```sh
cd offline && .venv/bin/python -I run_eval.py ablations && .venv/bin/python -I run_eval.py order-review
```

**Esperado:** las mismas cifras que en F18 (T034), salvo los segundos.

## 3. Instrumentadas en los emuladores

Siempre con `adb -s <emulador>`: el teléfono puede estar conectado y no hay que tocarlo. Se instala con `adb -s … install -r -t` los dos APK y se lanza:

```sh
adb -s emulator-5554 shell am instrument -w -r \
  -e notAnnotation com.lisdatasolutions.microrag.data.download.RequiresNetwork \
  com.lisdatasolutions.microrag.test/androidx.test.runner.AndroidJUnitRunner
```

**Esperado:**
- en `Pixel_10`, todo en verde;
- en API 33, solo los 4 fallos de siempre de Atrás desde la raíz;
- entre las pruebas, la migración 4 → 5, `switchTextEmbedder`, las páginas, el conversor y la accesibilidad.

## 4. Recorrido en el emulador

1. **Modo apagado:**
   - Ajustes muestra el interruptor apagado y las filas de Gemma y la 270M;
   - la Ingesta no muestra el modo;
   - un JPG se rechaza con «Activa el modo multimodal…».
2. **Activar** con modelos de desarrollo (`dev-models`, procedimiento de F02, con la 270M, la 440M y Gemma):
   - el aviso;
   - la validación de identidad y el cambio;
   - la fila de la 440M en lugar de la 270M.
3. **Importar** un PDF en Ambos, un escaneado y un PNG: la cola muestra el progreso en páginas, y la Biblioteca y el detalle, el modo.
4. **Reindexar** un PDF de Texto a Ambos, y después «Quitar del índice»: en `pages/` no quedan ficheros de ese documento.
5. **Desactivar y reactivar:** reactivar es inmediato.
6. **Al 200 % y en oscuro,** con TalkBack: Ajustes, «Modo al importar PDF», la Ingesta y el detalle.

## 5. Teléfono de referencia (última fase, con permiso)

**Antes de empezar:**
- anotar los ajustes del teléfono;
- `wm dismiss-keyguard`;
- la salida, siempre en `/data/local/tmp`.

**Pasos:**
1. **Activar el modo de verdad.** Descarga la 440M (369 MB), la valida, cambia el modelo y **borra la 270M**: antes, el usuario lo aprueba.
   - Se mide cuánto tarda cada paso;
   - la búsqueda de texto se comprueba antes y después, solo con etiquetas concretas y sin abrir los datos del usuario.
2. **Un PDF de 200 páginas en Visual,** con la app matada a mitad: se anotan las páginas por minuto y el espacio por página.
3. **Un escaneado, un JPG y un PNG.**
4. **La batería completa,** sin `@RequiresNetwork`.
5. **Limpieza:**
   - se borran los documentos de prueba;
   - el modo se deja como diga el usuario;
   - se restauran los ajustes.
