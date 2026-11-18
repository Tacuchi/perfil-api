---
status: accepted
date: 2026-11-18
---

# Cada lectura se cuenta en la base de datos antes de pedirla al sitio

Motivo tomado del comentario de `RecoleccionServiceImpl` y del de la migración `V2__lecturas_del_dia.sql`.

## Contexto y problema

Cada bolsa pública admite como máximo 3 lecturas por día de Lima. Había que decidir cuándo se cuenta una lectura: al pedirla o al terminarla.

## Opciones consideradas

- Contar al terminar, solo las lecturas correctas.
- Contar antes de pedirla al sitio, termine bien o no.

## Resultado de la decisión

Opción elegida: contar antes de pedirla. El comentario dice que así ni un reinicio ni un fallo dejan pasar el tope. La migración que crea la cuenta siembra la última lectura de cada fuente como una lectura de su día, para que el día del despliegue no se lea de más.

### Consecuencias

- Una lectura que falla igual gasta una de las 3 del día.
- La cuenta se reserva con una sola sentencia que no supera el tope.
- En la corrida local la lectura se gasta cuando el lector avisa que empieza a consultar el sitio, no al abrir la corrida: un lector que no responde no la gasta.
