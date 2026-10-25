# Sistema de diseño — LISDATA

## Origen
Basado en la paleta suministrada, el logo principal y los kits de iconografía y formas de LISDATA. Los estilos de interacción, espaciados y tipografía siguientes son propuestas de implementación, no normas corporativas confirmadas.

## Paleta corporativa
| Token | Hex | Uso |
|---|---|---|
| `brandCyan` | `#02B0E2` | Marca, detalles, acentos gráficos |
| `brandBlue` | `#0070DD` | Botones primarios y enlaces |
| `navy900` | `#052A39` | Texto principal, fondos oscuros |
| `navy700` | `#0D3F51` | Superficies oscuras secundarias |
| `blue800` | `#004994` | Títulos o degradados oscuros |
| `ice200` | `#A8ECFD` | Resaltes claros |
| `ice100` | `#C9F6FF` | Fondos y acentos suaves |

Tokens UI propuestos: `surface=#FFFFFF`, `background=#F7FBFD`, `surfaceAlt=#EAF8FD`, `outline=#DCEAF0`, `textSecondary=#54798A`. Validar contrastes en ambos temas.

## Uso de color
- CTA principal: fondo `brandBlue`, texto blanco.
- Texto de lectura: `navy900`; evitar `brandCyan` para textos pequeños sobre blanco.
- Estado listo: verde semántico propuesto, nunca comunicar éxito solo mediante color.
- Errores: rojo semántico Material 3; no reemplazarlo por azul.
- Hero: degradado `navy900 → blue800 → brandBlue`, con acentos `brandCyan`.
- Dark mode: derivar roles semánticos; no invertir todos los hex automáticamente.

## Logo y recursos
Usar `LisdataPrincipal.png` o preferentemente el logo vectorial oficial si se facilita. Mantener proporciones y área de seguridad. No dibujar un logo aproximado en Compose. Usar las Tech Shapes / Driven Shapes del Kit Web como elementos decorativos discretos, sin tapar texto.

## Iconos
Referencia corporativa: Rounded Lineal de Magnific, con variante Basic Gradient para usos destacados. Comprobar licencia y obtener SVG originales antes de integrarlos. Para prototipado, usar iconos lineales de Material Symbols o Material Icons, consistentes y de 20–24 dp. No mezclar estilos en una misma pantalla.

## Tipografía
La documentación de marca aportada no define de forma inequívoca una familia tipográfica para la app. Propuesta técnica: Roboto con escala Material 3; reemplazable por la fuente oficial cuando se confirme. Títulos 24–30 sp, subtítulos 17–20 sp, cuerpo 14–16 sp, etiquetas 12–14 sp. Respetar escalado del sistema y evitar fijar alturas de texto.

## Espaciado y geometría
- Unidad base: 4 dp; separación habitual: 8, 12, 16, 24 y 32 dp.
- Padding horizontal: 20 dp, adaptable a anchura disponible.
- Tarjetas: radio 16 dp; botones: 12 dp; chips: píldora.
- Altura táctil mínima: 48 dp para acciones.
- Elevación discreta; preferir borde suave sobre sombras intensas.
- Edge-to-edge y tratamiento correcto de barras del sistema/teclado.

## Componentes
Definir `LisdataTheme`, `LisdataPrimaryButton`, `LisdataTopBar`, `LisdataStatusChip`, `LisdataDocumentRow`, `LisdataEmptyState`, `LisdataSectionHeader`, `LisdataConfirmationDialog`, `LisdataProgressCard` y `LisdataSourceCitation`. Contratos detallados en `docs/03-components.md`.

## Accesibilidad
Contraste WCAG AA para texto, etiquetas de TalkBack, orden de foco, estado accesible de switches, tamaño de fuente dinámico, no depender solo de iconos, animaciones reducidas si el usuario lo solicita. Localizar todos los textos mediante recursos `strings.xml`.
