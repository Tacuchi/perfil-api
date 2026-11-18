---
status: accepted
date: 2026-11-18
---

# El lector local solo corre cuando la persona lo inicia desde el panel

Motivo dado por la persona el 2026-11-18, al confirmar el que consta en la spec 006 del proyecto, «Conexiones de fuentes».

## Contexto y problema

Algunas fuentes podían leerse desde la máquina de la persona con el lector local. Había que decidir cuándo corre ese lector.

## Opciones consideradas

- Que el lector corra solo, por horario.
- Que corra solo cuando la persona lo inicia desde el panel.

## Resultado de la decisión

Opción elegida: solo cuando la persona lo inicia desde el panel. La spec lo registra como decisión de la persona: con su máquina apagada, o sin su acción, solo corre lo que el servidor puede leer.

### Consecuencias

- Una corrida local solo se abre con `POST /api/recoleccion/maquina`, que exige el código de la persona y el origen del panel.
- La api entrega un código de un solo uso; el lector lo envía en cada paso y tiene 2 min para reportarse y 15 min en total.
- La recolección programada y la lectura general omiten las conexiones de la máquina con el motivo `DESDE_MAQUINA`.
- Hoy ninguna fuente declara la vía de la máquina, así que la corrida se rechaza con `SIN_FUENTES`.
