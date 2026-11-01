# Quickstart: validar Ajustes e Idiomas (F12)

Guía de validación de extremo a extremo. Los detalles están en los contratos (`contracts/`) y en el modelo de datos.

## Requisitos

**Entorno de compilación:**
- JBR de Android Studio:

  ```
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```

- `adb`: `$HOME/Library/Android/sdk/platform-tools/adb`.

**Emuladores:**
- `MicroRAG_API33` (API 33), para la ruta de Android 13+;
- `Pixel_10` (API 37).

La ruta de Android 12 se prueba en la JVM con Robolectric (`sdk = 32`).

**Escritorio:** `offline/.venv` con los modelos en `offline/models/`, como en F05 y F11.

**Teléfono de referencia** (A063): **solo en la última fase**, con el recorrido acordado antes con el usuario. «Borrar historial» no se confirma en el teléfono sin su permiso expreso: borraría su conversación.

## 1. Escritorio

```
offline/.venv/bin/python -I -m unittest discover -s offline/tests
offline/.venv/bin/python -I offline/run_eval.py language-review
offline/.venv/bin/python -I offline/run_eval.py crosslang-review english_answers=auto
offline/.venv/bin/python -I offline/run_eval.py report
offline/.venv/bin/python -I offline/gen_answer_golden.py --check
```

**Esperado:**
- todas las pruebas pasan;
- las huellas de `informe` y `v4` no cambian (L1);
- el golden sigue al día;
- `build/language-review/result.json` y `build/crosslang-review/result.json` tienen cifras sin juicios pendientes;
- la sección «Idiomas (F12)» está en el informe.

## 2. JVM

```
./gradlew :app:testDebugUnitTest :app:lintDebug
```

**Esperado:**
- **prompt y negativa:** `PromptBuilderTest` con `AUTOMATIC`; `AnswerReadingTest` con la negativa en inglés; `AnswerGoldenTest` sin cambios (426);
- **chat:** `DefaultChatSessionTest` con `deleteAll`, `historyCleared`, el id borrado y el idioma de las respuestas; `ChatViewModelTest` vuelve a un chat vacío tras el borrado;
- **idiomas:**
  - `DataStoreLanguageSettingsStoreTest`: errores de lectura y escritura y valor desconocido;
  - `PlatformAppLanguageControllerTest`, con `sdk = 32` y `sdk = 36`;
  - `AppLocaleTest`: por defecto, fichero y paso a Android 13;
  - `SaveLanguageSettingsUseCaseTest`: orden y vuelta atrás;
- **pantallas:** `SettingsViewModelTest`, `LanguagesViewModelTest`, `SettingsScreenTest` y `LanguagesScreenTest` (Robolectric: estados S1–S11, semántica U2–U5 y U7);
- **resto:**
  - `NavigationTest`;
  - `StringsParityTest`;
  - `LayeringTest`, con la regla nueva de `feature/settings`;
  - `AppModulesTest`.
- **Lint:** sin errores nuevos.

## 3. Emulador

```
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r …; adb shell am instrument -w -e notAnnotation <RequiresModels,RequiresNetwork> …
```

Escribe la salida en un fichero; no la pases por `tail`.

**Esperado:**
- **instrumentadas:**
  - `SqliteChatRepositoryTest`: `conversationCount` y `conversationExists`;
  - `SettingsScreenAccessibilityTest` y `LanguagesScreenAccessibilityTest` (ATF);
  - `SystemBackTest`, ya sin el marcador;
  - las de F01–F11.
- **recorrido en API 33 y en API 37:**
  1. **Ajustes:** las filas de modelo con su estado; «Local» solo si están preparados.
  2. **Ficha de modelo:** fichero, tamaño, SHA-256, dimensiones y procesador.
  3. **Cambio a English y vuelta a Español:**
     - todos los textos cambian;
     - la pestaña Chat conserva su conversación;
     - no hay indexación;
     - la notificación de indexación sale en el idioma nuevo.
  4. **Idioma por app desde los ajustes del sistema** (API 33+): la app y la pantalla Idiomas lo siguen.
  5. **«Automático»** (si se adopta): una pregunta en inglés responde en inglés.
  6. **Borrar historial:**
     - con conversaciones de prueba, sin conversaciones y con una respuesta en curso;
     - el chat queda vacío y la siguiente pregunta se guarda.
  7. **Accesibilidad:** letra al 200 %, horizontal, modo oscuro y TalkBack en las dos pantallas.

## 4. Teléfono de referencia (última fase)

- La batería instrumentada completa, con los APK finales.
- Recorrido acordado con el usuario, con las reglas de privacidad de F11 (capturas recortadas, volcados en `/data/local/tmp` y ajustes restaurados):
  - capturas de Ajustes y de Idiomas, para compararlas con los PNG 09 y 10;
  - cambio de idioma de ida y vuelta;
  - ficha de modelo;
  - el diálogo de borrar el historial se abre y se **cancela**. Confirmarlo solo con el permiso expreso del usuario.
- Sin tráfico de red de la app (`dumpsys netstats`, uid de la app).
- El teléfono queda con los ajustes y el idioma de la app que tenía.
