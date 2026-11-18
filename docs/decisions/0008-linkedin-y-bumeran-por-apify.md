---
status: accepted
date: 2026-11-18
---

# LinkedIn y Bumeran se leen por Apify, desde el servidor

Motivo dado por la persona el 2026-11-18, al confirmar el que consta en la spec 011 del proyecto, «LinkedIn y Bumeran por Apify».

## Contexto y problema

LinkedIn y Bumeran se leían desde la máquina de la persona, con el lector local. Había que decidir desde dónde se leen ahora.

## Opciones consideradas

- Seguir leyéndolas desde la máquina de la persona con el lector local.
- Leerlas desde el servidor por el servicio de pago, Apify, a demanda, igual que Computrabajo.

## Resultado de la decisión

Opción elegida: leerlas por Apify desde el servidor. La spec dice que LinkedIn dejó de leerse: su página de empleos cambió y el lector local ya no encuentra los avisos. Con Apify, la máquina de la persona deja de hacer falta para leer ofertas, y las tres conexiones de Apify comparten una sola clave.

### Consecuencias

- LinkedIn y Bumeran son conexiones de pago: necesitan la clave y un tope mensual, y no tienen tope diario.
- La clave de Apify se guarda una sola vez y la usan las tres conexiones de pago.
- LinkedIn y Bumeran empiezan con un tope mensual de 1 USD; una migración lo completa sin pisar el que la persona ya eligió.
- Ninguna fuente declara hoy la vía de la máquina. Sus rutas siguen en el código y una corrida local se rechaza con `SIN_FUENTES`.
