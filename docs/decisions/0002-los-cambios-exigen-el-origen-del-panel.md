---
status: accepted
date: 2026-11-18
---

# Los cambios solo se aceptan con el origen del panel

Motivo tomado del comentario de la clase `OrigenPanelFilter` y del de `SeguridadConfig` sobre la protección entre sitios.

## Contexto y problema

El proxy de acceso añade su código a cualquier petición del navegador que lleve su cookie, también a una que dispare otra página. La api no usa cookies propias ni sesión. Había que decidir cómo distinguir una petición del panel de una disparada desde otro sitio.

## Opciones consideradas

- Un token CSRF propio con sesión en la api.
- Exigir que la cabecera `Origin` sea la dirección del panel en toda petición que cambie algo.

## Resultado de la decisión

Opción elegida: exigir el origen del panel. El comentario dice que el código de acceso viaja también en peticiones que otra página dispara, y que el origen es lo que las distingue. Sin cookies propias ni sesión, la protección entre sitios la da este filtro.

### Consecuencias

- `GET`, `HEAD` y `OPTIONS` pasan sin mirar el origen; el resto sin el origen del panel recibe 403.
- La dirección del panel es configuración obligatoria.
- Las rutas del lector local quedan fuera de este filtro, porque no las llama un navegador.
