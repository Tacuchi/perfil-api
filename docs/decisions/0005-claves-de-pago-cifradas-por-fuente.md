---
status: accepted
date: 2026-11-18
---

# La clave de pago se guarda cifrada con AES-GCM y la fuente como dato asociado

Motivo tomado de los comentarios de `CifradoClaves`, `Conexion` y `ClaveRequest`.

## Contexto y problema

Las conexiones de pago necesitan una clave del servicio que la persona carga desde el panel. Había que decidir cómo se guarda y qué vuelve al panel.

## Opciones consideradas

- Guardar la clave en claro en la base de datos.
- Cifrarla con una llave maestra del servidor, con un vector propio por clave y la fuente como dato asociado.

## Resultado de la decisión

Opción elegida: cifrarla con AES-GCM del JDK. El comentario dice que, con la fuente como dato asociado, una clave guardada para una fuente no se descifra como de otra. La clave cifrada nunca sale de la api: solo consta cuándo se cargó.

### Consecuencias

- La llave maestra llega por `PERFIL_CLAVES_LLAVE`, 32 bytes en base64, y nunca se registra.
- Una clave que la llave no puede leer pide volver a cargarla; el mensaje no lleva nada de la clave.
- La petición que carga la clave no la imprime.
