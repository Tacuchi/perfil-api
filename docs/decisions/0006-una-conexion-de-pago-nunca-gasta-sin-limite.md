---
status: accepted
date: 2026-11-18
---

# Una conexión de pago nunca gasta sin límite

Motivo tomado de los comentarios de `Conexion`, `CicloApify` y `RecoleccionServiceImpl`.

## Contexto y problema

LinkedIn, Bumeran y Computrabajo se leen por Apify, que cobra por corrida y por aviso. Había que decidir qué impide que una lectura gaste más de lo que la persona fijó.

## Opciones consideradas

- Leer y revisar el gasto después.
- Exigir clave, tope mensual y llave antes de leer, y pedir a Apify un gasto máximo igual al saldo del tope.

## Resultado de la decisión

Opción elegida: acotar el gasto antes de leer. El comentario dice que una de pago sin clave, sin tope mensual o sin la llave del servidor no se lee y nunca gasta sin límite. Cada lanzamiento va de uno en uno y su cargo queda asentado antes de autorizar el siguiente.

### Consecuencias

- Una conexión sin lo que necesita, o con su tope del mes consumido, se omite sin ninguna consulta al servicio.
- Si el costo de una corrida queda incierto, se cuenta el gasto autorizado y se detienen las búsquedas siguientes.
- Las de pago no tienen tope diario: las acota su tope mensual.
