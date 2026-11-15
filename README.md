# perfil-api

API de perfil.tacuchi.net: guarda las versiones del CV y entrega la vigente en `/cv`, y recoge, clasifica y lista ofertas de trabajo.

## Rutas

| Ruta | Acceso | Qué hace |
|---|---|---|
| `GET /cv` | pública | El PDF vigente, para verlo en el navegador. Sin vigente: 404 con la página de error del sitio. |
| `GET /api/versiones` | persona | Historial, de la más reciente a la más antigua. |
| `POST /api/versiones` | persona | Sube `pdf` y, opcional, `word` (multipart). La nueva queda vigente. |
| `GET /api/versiones/{id}/pdf` · `/word` | persona | Descarga una versión o su Word. |
| `PUT /api/vigente` | persona | `{"version": "<id>"}` deja vigente una versión anterior. |
| `GET /api/ofertas` | persona | Lista de ofertas. Sin filtros: nuevas de encaje alto y medio. Filtros: `encaje`, `estado`, `fuente`, `modalidad` (uno o varios, separados por coma), `tecnologia`, `desde` y `hasta` (`AAAA-MM-DD`, días de Lima). |
| `GET /api/ofertas/{id}` | persona | Una oferta con su texto y, si lo trae, el correo de contacto. |
| `PATCH /api/ofertas/{id}` | persona | `{"estado": "GUARDADA" \| "DESCARTADA" \| "POSTULADA"}`. La postulada guarda su fecha. |
| `GET /api/fuentes` | persona | Estado de cada fuente: `CORRECTA`, `FALLO`, `BLOQUEO` o `SIN_LEER`, con su última lectura correcta. |
| `GET /api/tecnologias` | persona | Tecnologías del CV con las que se clasifica, principales y secundarias. |

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
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/<base>` (PostgreSQL 18) |
| `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` | Usuario de la base (secreto) |
| `PERFIL_AVISO_ACTIVO` | `true` o `false`: el correo diario de ofertas. Se declara siempre |
| `PERFIL_AVISO_REMITENTE` | Remitente del correo diario. Obligatorio con el aviso encendido |
| `SPRING_MAIL_HOST` · `SPRING_MAIL_PORT` · `SPRING_MAIL_USERNAME` · `SPRING_MAIL_PASSWORD` | Servidor de envío (SMTP) del proveedor elegido. Obligatorio con el aviso encendido; la clave es secreta |

El correo diario va solo a `PERFIL_ACCESS_CORREO`. Las direcciones de las tres fuentes de ofertas y el horario de la recolección (`perfil.ofertas.*`) tienen su valor en `application.properties`; se cambian con su variable (`PERFIL_OFERTAS_CRON`, ...) solo para pruebas.

## Monitor de ofertas

- **Fuentes:** Get on Board, Remotive y Remote OK, por la API pública que cada una declara. Cada hora se intenta la recolección, pero cada fuente se lee una sola vez por día de Lima, contado en la base de datos. Ante un fallo o un bloqueo la fuente queda marcada y no se prueba otra vía.
- **Clasificación:** reglas fijas en `clasificacion/Clasificador.java`. Las tecnologías del CV están en `src/main/resources/ofertas/tecnologias.json`, copiadas a mano del hub; si el CV cambia, hay que copiarlas de nuevo.
- **Conservación:** las nuevas y las descartadas se eliminan 60 días después de vistas por última vez; las guardadas y las postuladas se conservan.
- **Esquema:** `src/main/resources/db/migration/`, aplicado por Flyway al arrancar.

## Desarrollo

Requiere JDK 25. `./mvnw verify` corre todas las pruebas, sobre un PostgreSQL 18 real que se levanta solo y sin Docker. Las fuentes se prueban contra sus respuestas reales guardadas (`src/test/resources/fuentes/`), con los correos de terceros reemplazados por direcciones de ejemplo; el correo diario, contra un servidor SMTP de prueba.

`./mvnw spring-boot:test-run` levanta la API en `http://localhost:8080` para recorrerla con el panel: almacén en memoria, un sitio de prueba para la página 404, un Access de prueba que publica sus claves en local, un PostgreSQL de pruebas, las tres fuentes servidas desde sus respuestas guardadas y un servidor de correo de prueba con el aviso encendido. El código firmado para la persona queda en `target/codigo-local.jwt`; el panel lo envía con `PERFIL_CODIGO_LOCAL="$(cat target/codigo-local.jwt)" npm start`. La recolección no corre por horario: se dispara con los mandos locales, que exigen el mismo código y el origen del panel:

| Mando | Qué hace |
|---|---|
| `POST /api/local/recoger` | Recoge ahora (cada fuente, una vez por día del reloj local). |
| `POST /api/local/dia-siguiente` | Adelanta el reloj un día y recoge. |
| `PUT /api/local/fuentes/{fuente}?modo=BIEN\|FALLA\|BLOQUEA` | Hace que una fuente responda bien, falle o bloquee. |
| `GET /api/local/correos` | Asunto y destinatarios de los correos recibidos. |

Nada de esto entra al jar. El contrato del almacén corre también contra R2 cuando están definidas las cuatro variables `PERFIL_ALMACEN_*`.

## Despliegue

`.github/workflows/deploy.yml` prueba, publica la imagen con la etiqueta del commit y redespliega en Coolify. Las rutas del proxy están en `deploy/traefik-dynamic.yml`. La imagen limita la JVM a 224 MB de heap; en Coolify fija además el límite de memoria del contenedor en 512 MB. La base de datos es un PostgreSQL 18 aparte en el mismo servidor.
