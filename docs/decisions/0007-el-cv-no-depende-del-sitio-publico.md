---
status: accepted
date: 2026-11-18
---

# `/cv` no depende de que el sitio público esté arriba

Motivo tomado del comentario de `PaginaErrorSitio`.

## Contexto y problema

Sin una versión vigente, `/cv` responde 404. Había que decidir qué página muestra: la del sitio público o una propia.

## Opciones consideradas

- Mostrar siempre una página propia.
- Pedir la página 404 del sitio público y, si no llega, mostrar una mínima.

## Resultado de la decisión

Opción elegida: la página del sitio, con una mínima de respaldo. El comentario dice que así `/cv` responde igual que el resto del sitio, y que `/cv` no depende de que el sitio esté arriba.

### Consecuencias

- La api espera al sitio como máximo 2 s para conectar y 2 s para leer.
- Un fallo del sitio se registra como aviso y la respuesta sigue siendo 404.
