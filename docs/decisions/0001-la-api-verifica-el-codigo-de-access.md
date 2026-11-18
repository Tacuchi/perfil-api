---
status: accepted
date: 2026-11-18
---

# La api verifica el código del proxy de acceso aunque Cloudflare ya filtre

Motivo tomado del comentario de la clase `SeguridadConfig` y del comentario de cabecera de `deploy/traefik-dynamic.yml`.

## Contexto y problema

El proxy de acceso protege el panel y añade un código firmado a cada petición. Había que decidir si la api confía en que la petición pasó por ese proxy o si comprueba el código por su cuenta.

## Opciones consideradas

- Confiar en el borde: aceptar toda petición que llegue al servidor.
- Verificar en la api la firma, el emisor, la vigencia, la aplicación y el correo de cada código.

## Resultado de la decisión

Opción elegida: verificar en la api. El comentario dice que la IP del servidor es pública y que alguien podría llegar sin pasar por Cloudflare. Por la misma razón, el enrutador del servidor solo acepta los rangos de Cloudflare.

### Consecuencias

- Toda ruta, salvo `GET /cv`, exige el código en la cabecera `Cf-Access-Jwt-Assertion`.
- La api descarga las claves de firma del emisor configurado.
- Las rutas del lector local aceptan solo el código del token de servicio, sin correo; las demás, solo el de la persona.
- Los rangos de Cloudflare del enrutador se copian a mano y hay que actualizarlos cuando Cloudflare los cambie.
