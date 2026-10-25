# 08 — Indexación / Estado del procesamiento

## Objetivo
Dar visibilidad y control sobre trabajos que pueden durar bastante con 10.000–100.000 fragmentos.

## Ruta
`index`. Accesible desde Home, Ingesta y Ajustes. Sin barra inferior.

## Layout
- Top bar: `Estado de indexación`.
- Hero oscuro: estado global, cantidad procesada/total y barra de progreso solo si es calculable.
- Lista de etapas: extracción, fragmentación, embeddings y persistencia/indexación.
- Sección de recursos: preferencia `Solo mientras carga`, espacio usado y, si se implementa, estado térmico.
- Acción contextual `Pausar`, `Reanudar` o `Reintentar`.

## Contrato de progreso
El ViewModel recibe trabajos persistidos y calcula progreso real. Si no hay total conocido, mostrar `Procesando...` con barra indeterminada. No interpolar porcentajes falsos. Si varias tareas están activas, mostrar progreso global ponderado solo cuando el cálculo sea fiable.

## Ejecución
La UI observa el estado de un coordinador de trabajos. El equipo técnico debe decidir entre WorkManager y ejecución foreground según duración, requisitos del sistema y versión Android. Pausa/reanudación solo se ofrece si el pipeline tiene checkpoints persistidos; de lo contrario usar `Cancelar` con explicación.

## Estados
- Sin tareas: `Todo actualizado`.
- En curso: etapa actual, documentos en cola y progreso.
- Pausado: causa y botón `Reanudar`.
- Error: documento afectado, causa y `Reintentar`.
- Batería baja/temperatura elevada: explicar reducción o suspensión si se aplica.
- Almacenamiento insuficiente: detener de forma segura y orientar al usuario.

## Criterios de aceptación
- [ ] No aparecen números de demostración en producción.
- [ ] Progreso restaurado tras reabrir app.
- [ ] Cancelación/pausa coherente con backend real.
- [ ] Índice nunca queda parcialmente publicado como listo.
- [ ] UI no bloquea hilo principal.
