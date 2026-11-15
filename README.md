# perfil-api

API del CV de perfil.tacuchi.net: guarda las versiones del CV y entrega la vigente en `/cv`.

## Rutas

| Ruta | Acceso | Qué hace |
|---|---|---|
| `GET /cv` | pública | El PDF vigente, para verlo en el navegador. Sin vigente: 404 con la página de error del sitio. |
| `GET /api/versiones` | persona | Historial, de la más reciente a la más antigua. |
| `POST /api/versiones` | persona | Sube `pdf` y, opcional, `word` (multipart). La nueva queda vigente. |
| `GET /api/versiones/{id}/pdf` · `/word` | persona | Descarga una versión o su Word. |
| `PUT /api/vigente` | persona | `{"version": "<id>"}` deja vigente una versión anterior. |

«Persona» = petición con el código de Cloudflare Access (`Cf-Access-Jwt-Assertion`) válido para la aplicación y el correo configurados. Los cambios además exigen `Origin` igual a la dirección del panel.

## Configuración

Todo llega por variables de entorno; si falta una, el servicio no arranca y la nombra.

| Variable | Valor |
|---|---|
| `PERFIL_ACCESS_EMISOR` | `https://<equipo>.cloudflareaccess.com` |
| `PERFIL_ACCESS_AUD` | Audience tag de la aplicación de Access |
| `PERFIL_ACCESS_CORREO` | Correo permitido |
| `PERFIL_ALMACEN_ENDPOINT` | `https://<cuenta>.r2.cloudflarestorage.com` |
| `PERFIL_ALMACEN_BUCKET` | Bucket de R2 |
| `PERFIL_ALMACEN_ACCESO` · `PERFIL_ALMACEN_SECRETO` | Credencial de R2 limitada al bucket (secreta) |
| `PERFIL_CV_ARCHIVO` | Nombre con que se guarda el PDF público, p. ej. `Nombre-Apellido-CV.pdf` |
| `PERFIL_SITIO_URL` | Sitio público, de donde sale la página 404 |
| `PERFIL_PANEL_ORIGEN` | `https://perfil.tacuchi.net` |

## Desarrollo

Requiere JDK 25. `./mvnw verify` corre todas las pruebas. El contrato del almacén corre también contra R2 cuando están definidas las cuatro variables `PERFIL_ALMACEN_*`.

## Despliegue

`.github/workflows/deploy.yml` prueba, publica la imagen con la etiqueta del commit y redespliega en Coolify. Las rutas del proxy están en `deploy/traefik-dynamic.yml`. La imagen limita la JVM a 192 MB de heap; en Coolify fija además el límite de memoria del contenedor.
