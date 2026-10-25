# 02 — Onboarding / Preparación local

## Objetivo
Guiar al usuario hasta un dispositivo realmente preparado para trabajar offline. No simular modelos instalados.

## Ruta
`setup`, desde `welcome`; salida a `home` únicamente cuando los requisitos mínimos estén satisfechos.

## Jerarquía
Top: indicador `PRIMER USO` y progreso de pasos. H1 `Prepara tu espacio inteligente`. Texto introductorio. Tarjeta azul muy clara sobre privacidad. Sección `Modelos del dispositivo` con filas separadas:
- Gemma 4 — generación de respuestas.
- EmbeddingGemma 2 — representación semántica.

Cada fila muestra estado real: `No instalado`, `Importando`, `Validando`, `Listo`, `Incompatible` o `Error`.

## Flujo recomendado
1. Comprobar espacio disponible, compatibilidad y artefactos de modelos.
2. Si falta un modelo, ofrecer `Seleccionar archivo del dispositivo` mediante SAF o instrucciones para aprovisionamiento local.
3. Validar integridad y formato antes de marcar `Listo`.
4. Preparar almacenamiento local e índice.
5. Habilitar `Entrar en LISDATA` cuando los componentes obligatorios estén listos.

No incorporar descargas de Internet como requisito de uso. Si el producto decide distribuir modelos dentro de la app, documentar el tamaño y la estrategia de instalación por separado.

## Estados y errores
- Falta de espacio: mensaje claro y acción para liberar espacio.
- Modelo incompatible: explicar que debe seleccionarse otro artefacto.
- Validación en curso: indicador indeterminado si no hay porcentaje fiable.
- Fallo recuperable: `Reintentar`.
- Modelos listos: mostrar confirmación y CTA.

## Accesibilidad
Progreso con descripción verbal; estados legibles sin color; selector de archivos accesible.

## Criterios de aceptación
- [ ] Nunca aparece `Listo` sin verificación real.
- [ ] No se requiere conexión a Internet.
- [ ] El onboarding puede recuperarse tras cierre de app.
- [ ] El usuario conoce por qué no puede entrar si falta un componente.
