# Contrato de servicios base — F01

## Aplicación y DI (Koin 4.2.2)

```text
class MicroRagApplication : Application   // declarada en el manifest; startKoin { androidContext(this); modules(appModules) }

val appModules: List<Module> = listOf(coreModule, domainModule, dataModule, featureModule)
```

| Módulo | Paquete | Contenido en F01 | Quién lo amplía |
|---|---|---|---|
| `coreModule` | `core.di` | `AppDispatchers`, `AppLogger` | Cualquier feature que necesite un servicio transversal |
| `domainModule` | `domain.di` | Vacío | Casos de uso (F03+) |
| `dataModule` | `data.di` | Vacío | Repositorios, base de datos y descargas (F03+) |
| `featureModule` | `feature.di` | Vacío | ViewModels (`viewModelOf`) (F03+) |

**Regla:** `AppModulesTest` ejecuta `verify()` (`koin-test`) sobre `appModules` y tiene que pasar en cada feature. Un módulo nuevo se añade a `appModules` y queda cubierto automáticamente.

## Dispatchers

```text
class AppDispatchers(
  val main: CoroutineDispatcher,        // Dispatchers.Main
  val io: CoroutineDispatcher,          // Dispatchers.IO (ficheros, red de F03)
  val default: CoroutineDispatcher,     // Dispatchers.Default (CPU general)
  val database: CoroutineDispatcher,    // 1 hilo "microrag-db"
  val llm: CoroutineDispatcher,         // 1 hilo "microrag-llm"
  val embeddings: CoroutineDispatcher,  // 1 hilo "microrag-embed"
)
```

**Garantía:** dos tareas enviadas al mismo dispatcher de un solo hilo nunca se ejecutan en paralelo, y siempre corren en el hilo con nombre. Lo comprueba `AppDispatchersTest`.

**Uso:** la base de datos solo se usa en `database`, LiteRT-LM solo en `llm` o `embeddings`, y nada pesado en `main` (constitución IV).

## Logger

```text
interface AppLogger { fun d(tag: String, msg: () -> String); fun i(...); fun w(..., t: Throwable? = null); fun e(..., t: Throwable? = null) }
```

- La implementación por defecto solo emite si `BuildConfig.DEBUG`.
- **Prohibido** registrar prompts, texto de documentos, respuestas o nombres de archivo del usuario (constitución I).

## Depuración (solo `src/debug`)

| Pieza | Responsabilidad |
|---|---|
| `DebugLauncherActivity` | Segundo icono de lanzador «MicroRAG Debug» con las entradas «Catálogo de componentes» y «Menú de rutas» |
| `ComponentCatalogScreen` | Lista seccionada de `CatalogEntry`, con un conmutador de tema claro/oscuro y de escala de fuente para revisión |
| `RouteMenuScreen` | Un botón por destino (con argumentos de prueba, incluidos los inválidos para N8) que lanza `MainActivity` con `EXTRA_ROUTE` |
| `DebugRouting` (debug) | `initialRoute(intent)`: del extra al tipo de ruta |
| `DebugRouting` (release, `src/release`) | `initialRoute(intent) = null` |
