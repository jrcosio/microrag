# 01 — Welcome / Bienvenida

## Objetivo
Comunicar en pocos segundos que LISDATA permite consultar documentos con IA privada, sin nube. Primera pantalla solo durante el onboarding.

## Ruta
`welcome`. CTA principal → `setup`. No barra inferior.

## Composición visual
Pantalla completa con degradado oscuro `#052A39 → #0D3F51 → #004994`. Logo oficial LISDATA en la parte superior con suficiente margen. Ilustración central abstracta: nodo de IA conectado a documento, chat y candado mediante órbitas/Tech Shapes. No usar imágenes genéricas de robots.

Parte inferior:
- Eyebrow: `INTELIGENCIA DOCUMENTAL LOCAL`.
- H1 en dos líneas: `Tu conocimiento. Solo tuyo.`
- Descripción: `Consulta tus documentos con inteligencia artificial, sin enviar tus datos fuera del móvil.`
- CTA de ancho completo: `Comenzar`.
- Pie: `Privado · Sin nube · En tu dispositivo`.

## Interacciones
- `Comenzar` navega a `setup`.
- Atrás desde bienvenida aplica comportamiento estándar de salida; no abrir Home.
- No solicitar permisos en esta pantalla.

## Estados
Solo estado visual estático; si la configuración ya se completó, la navegación de arranque debe saltar a Home. En pantallas estrechas, la ilustración cede espacio al texto y al CTA.

## Accesibilidad
El logo tiene descripción; la ilustración decorativa no debe anunciarse en TalkBack. Contraste alto para texto blanco sobre degradado. Botón con altura mínima 48 dp.

## Criterios de aceptación
- [ ] Logo corporativo real, sin aproximaciones.
- [ ] CTA visible sin recortar en móviles compactos.
- [ ] Sin tabs ni datos de documentos.
- [ ] Solo se muestra durante el primer uso o reinicio de onboarding.
