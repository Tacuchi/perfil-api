---
status: accepted
date: 2026-11-18
---

# Una sola instancia: el candado y el avance de la recolección viven en memoria

Motivo tomado de los comentarios de `CandadoRecoleccion` y de `CorridaLocalServiceImpl`.

## Contexto y problema

Nunca deben correr dos recolecciones a la vez: programada, a demanda o corrida local. Había que decidir dónde vive ese candado y el avance que el panel consulta.

## Opciones consideradas

- Un candado compartido en la base de datos.
- Un candado y un avance en la memoria del proceso.

## Resultado de la decisión

Opción elegida: en memoria. El comentario dice que con una sola instancia de la api basta. El candado es una bandera guardada por un monitor y no un `ReentrantLock`, porque lo toma el hilo que recibe la petición y lo suelta otro hilo, y un `ReentrantLock` solo lo puede soltar el hilo que lo tomó.

### Consecuencias

- La api corre como una sola instancia.
- Un reinicio pierde el avance en curso; el tope diario no se pierde, porque se cuenta en la base de datos.
- La corrida local abierta y la huella de su código también viven en memoria.
