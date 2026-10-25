# Contrato de navegación — F01

Este contrato vale para todas las features: cada una sustituye el marcador de sus destinos sin cambiar las rutas, los argumentos ni las reglas de pila descritos aquí. Para cambiarlo hay que enmendarlo en la feature que lo necesite y registrarlo en DECISIONS.

## Rutas tipadas

Paquete: `com.lisdatasolutions.microrag.core.navigation`. Las rutas son tipos `@Serializable`.

| Tipo | Forma | Argumentos | Restricciones |
|---|---|---|---|
| `Welcome` | `data object` | — | — |
| `Setup` | `data object` | — | — |
| `Home` | `data object` | — | **Destino inicial del `NavHost`** (hasta F03, que añade la puerta de onboarding) |
| `Chat` | `data class` | `conversationId: Long? = null` | `null` = conversación nueva |
| `Ingest` | `data object` | — | — |
| `Library` | `data object` | — | — |
| `Document` | `data class` | `documentId: Long`, `chunkId: Long? = null` | `chunkId` solo cuando se llega desde una cita (F11) |
| `Index` | `data object` | — | — |
| `Settings` | `data object` | — | — |
| `Languages` | `data object` | — | — |

**Regla:** solo se pasan identificadores. Documentos, mensajes y vectores se recuperan siempre desde repositorios (`01-navigation.md`).

## Destinos de nivel superior

```text
enum TopLevelDestination(route, icon, label):
  HOME     → Home,     ic_home,     R.string.nav_home     ("Inicio" / "Home")
  CHAT     → Chat(),   ic_chat,     R.string.nav_chat     ("Chat" / "Chat")
  LIBRARY  → Library,  ic_folder,   R.string.nav_library  ("Biblioteca" / "Library")
  SETTINGS → Settings, ic_settings, R.string.nav_settings ("Ajustes" / "Settings")
```

## Comportamiento de la pila

Cada regla es verificable con un test en `app/src/test/.../core/navigation/`.

| ID | Regla | Test |
|---|---|---|
| N1 | Tocar una pestaña no activa: `navigate(route) { popUpTo<Home> { saveState = true }; launchSingleTop = true; restoreState = true }` | La pila contiene como mucho `Home` + 1 raíz de pestaña |
| N2 | Atrás en la raíz de una pestaña distinta de Inicio: vuelve a `Home` | Tras Chat → Atrás, el destino actual es `Home` |
| N3 | Atrás en `Home`: la Activity termina | `popBackStack()` devuelve `false` y el shell delega en el sistema |
| N4 | Reseleccionar la pestaña activa: vuelve a la raíz de esa pestaña, descartando sus destinos secundarios | Library → Document → tocar Biblioteca deja el destino en `Library` |
| N5 | La flecha Atrás de la barra superior en Chat, Biblioteca y Ajustes hace lo mismo que el Atrás del sistema (G08, FR-018) | Mismo resultado que N2 |
| N6 | Barra inferior visible ⇔ destino ∈ {`Home`, `Chat`, `Library`, `Settings`} **y** el teclado no está visible | Visibilidad por destino; con el IME visible se oculta |
| N7 | El destino, la pestaña y el estado guardable se conservan al recrear la Activity o el proceso | `StateRestorationTester` y recreación con Robolectric |
| N8 | Un argumento ausente o inválido no provoca un cierre: el marcador muestra el valor recibido | `Document` con id `-1` renderiza el marcador |
| N9 | Sin enlaces profundos externos: ningún `<intent-filter>` con `VIEW`/`BROWSABLE` y ningún `deepLinks` en el grafo | Inspección del manifiesto fusionado (`platform-invariants.md`) |

## Marcador de destino pendiente

Composable `PendingDestination(destinationName: String, args: Map<String, String>, ownerFeature: String)`:
- `LisdataTopBar` con el nombre lógico del destino (y flecha Atrás si no es raíz).
- `LisdataEmptyState` con:
  - título «Pendiente · {ownerFeature}»;
  - descripción con la ruta y los argumentos;
  - sin CTA de producto.
- Textos en recursos: `R.string.pending_destination_title` / `_body`.

## Entrada de depuración

Solo existe en la variante `debug`. Ver [base-services.md](base-services.md).

`DebugLauncherActivity` abre `MainActivity` con el extra `com.lisdatasolutions.microrag.debug.EXTRA_ROUTE`, cuyo valor es el nombre de la ruta más los argumentos de prueba. En debug, `DebugRouting.initialRoute(intent)` lo traduce a un destino. En release, el *stub* devuelve `null` y el extra se ignora.
