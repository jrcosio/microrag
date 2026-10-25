# Diferencias frente al mockup — F01

Este documento compara los componentes del design system (catálogo de debug) con los PNG de `docs/disegn_app/screens/LISDATA-10-PANTALLAS-PNG/`. F01 no implementa pantallas de producto, así que las diferencias de cada pantalla se documentarán en la feature correspondiente.

| # | Elemento | PNG | Implementación | Motivo |
|---|---|---|---|---|
| 1 | Tipografía | Sans genérica tipo Helvetica en el render del mockup | Google Sans variable (pesos 400–700), escala de `INVENTARIO.md` §2.4 | D-003 (fuente aportada en `Tipografias/`) |
| 2 | Texto secundario | `#658797` | `#54798A`, y `#4D7183` sobre contenedores tintados | WCAG AA (D-015, D-019) |
| 3 | Textos semánticos | Aviso `#A46C19`, éxito `#129D83`, error `#DB5865` | `#8A5A12`, `#087A65`, `#C23F4D`. Rellenos y contenedores sin cambios | WCAG AA (D-015) |
| 4 | Degradado de las CTA | `#02AEE2 → #0072DD` | `#0099D4 → #0072DD` en botones con texto; el original se mantiene en elementos sin texto | Texto blanco ≥ 3:1 en todo el tramo (D-019) |
| 5 | Interruptor | 42×24 dp; apagado = pulgar blanco sobre pista clara | M3 `Switch` de 52×32 dp; apagado = pista neutra con borde y pulgar oscuro | D-018. El estilo apagado de M3 cumple el contraste 3:1 de componentes no textuales (WCAG 1.4.11), que el del PNG no alcanza |
| 6 | Chips de filtro | 27 dp de alto | M3 `FilterChip` de 32 dp visuales y 48 dp de área táctil | D-018, FR-014 |
| 7 | Barra inferior | Indicador de ≈61×33 dp | M3 `NavigationBar` (indicador de 64×32 dp, 80 dp de alto) con los colores del PNG | D-018 |
| 8 | Logo | «lisdata ›» en una sola tinta (aproximación) | Asset oficial `logoLISDATA.png` (degradado y marca) | FR-007; el SVG oficial está pendiente (G20) |
| 9 | Iconos | Trazo lineal | Lucide 1.61.0 equivalentes; Ajustes con sol (G19). «Pausa» de Lucide son dos barras redondeadas en lugar de «‖» | D-008 |
| 10 | Chips de estado | «Por configurar» sin punto | Todos los estados llevan punto o icono además del texto | FR-013 (nunca solo color) |
| 11 | Etiqueta del asistente | «LISDATA» | «MICRORAG» (recurso `assistant_name`) | D-014 |
| 12 | Flecha Atrás en pestañas | Visible en Chat, Biblioteca y Configuración | Visible; vuelve a Inicio | G08 aplicado según la precedencia del PNG |
| 13 | Modo oscuro | Sin mockup | Paleta derivada de §2.2 | D-016 |
| 14 | Pantallas | 10 pantallas de producto | Marcadores «Pendiente · Fxx» con ruta y argumentos | FR-021; se sustituyen en F03–F12 |
| 15 | Idioma | Español | Español por defecto; inglés en dispositivos configurados en inglés (`values-en`) | FR-032 |
| 16 | Icono del lanzador | No existe asset | Icono genérico del scaffold (adaptativo) | G43 pendiente del asset oficial |

Coinciden con el PNG:
- márgenes (20 dp) y radios (tarjeta 16 dp, botón 12 dp, tile 12 dp);
- botones de 52 dp;
- paleta de marca y superficies;
- hero con degradado y órbitas;
- tarjetas de estadística y de acción rápida;
- tarjeta informativa, filas de documento, cola y cita;
- anillo de progreso y filas de etapa;
- burbuja y tarjeta de respuesta;
- campo de búsqueda y zona de importación (sin «Arrastra», G14).
