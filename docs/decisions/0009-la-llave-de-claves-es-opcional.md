---
status: accepted
date: 2026-11-18
---

# La llave de claves es opcional: sin ella, la api arranca

Motivo dado por la persona el 2026-11-18, al confirmar el que consta en el plan 005 del proyecto, «Conexiones de fuentes».

## Contexto y problema

Las claves de las conexiones de pago se cifran con una llave maestra del servidor. Había que decidir qué pasa cuando esa llave falta o no es válida.

## Opciones consideradas

- Exigir la llave: sin ella, la api no arranca.
- Hacerla opcional: la api arranca y las conexiones de pago quedan sin configurar.

## Resultado de la decisión

Opción elegida: hacerla opcional. El plan dice que un push a `main` despliega solo, y que así un olvido en la configuración del orquestador ya no tumba producción.

### Consecuencias

- Sin la llave, o con una que no mide 32 bytes en base64, la api arranca y lo avisa en el log.
- Las conexiones de pago quedan «falta configurar» con el motivo «falta la llave de claves del servidor», y no se leen.
- Cargar una clave sin la llave responde 409 con motivo `FALTA_LLAVE`.
