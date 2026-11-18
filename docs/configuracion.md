# Configuración de la api

Todo llega por variables de entorno; si falta una, el servicio no arranca y la nombra.

| Variable | Valor |
|---|---|
| `PERFIL_ACCESS_EMISOR` | `https://<equipo-de-access>`, la dirección del equipo del proxy de acceso |
| `PERFIL_ACCESS_AUD` | Audience tag de la aplicación de Access |
| `PERFIL_ACCESS_CORREO` | Correo permitido |
| `PERFIL_ALMACEN_ENDPOINT` | `https://<cuenta>.r2.cloudflarestorage.com` |
| `PERFIL_ALMACEN_BUCKET` | Bucket de R2 |
| `PERFIL_ALMACEN_ACCESO` · `PERFIL_ALMACEN_SECRETO` | Credencial de R2 limitada al bucket (secreta) |
| `PERFIL_CV_ARCHIVO` | Nombre con que se guarda el PDF público, p. ej. `Nombre-Apellido-CV.pdf` |
| `PERFIL_SITIO_URL` | Sitio público, de donde sale la página 404 |
| `PERFIL_PANEL_ORIGEN` | `https://<tu-dominio-del-panel>` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:5432/<base>` (PostgreSQL 18) |
| `SPRING_DATASOURCE_USERNAME` · `SPRING_DATASOURCE_PASSWORD` | Usuario de la base (secreto) |
| `PERFIL_AVISO_ACTIVO` | `true` o `false`: el correo diario de ofertas. Se declara siempre |
| `PERFIL_AVISO_REMITENTE` | Remitente del correo diario. Obligatorio con el aviso encendido |
| `PERFIL_CLAVES_LLAVE` | 32 bytes en base64 (`openssl rand -base64 32`): cifra las claves de las conexiones de pago (secreta). Opcional: sin ella, o con otra longitud, la API arranca y las de pago quedan «falta configurar» |
| `SPRING_MAIL_HOST` · `SPRING_MAIL_PORT` · `SPRING_MAIL_USERNAME` · `SPRING_MAIL_PASSWORD` | Servidor de envío (SMTP) del proveedor elegido. Obligatorio con el aviso encendido; la clave es secreta |
| `PERFIL_LECTOR_TOKENID` | Identificador (`common_name`) del token de servicio del lector local. Opcional: sin él, la api arranca y rechaza toda entrega del lector |
| `PERFIL_LECTOR_AUD` | Audience tag de la aplicación de Access que protege las rutas del lector. Opcional: sin ella, se usa `PERFIL_ACCESS_AUD` |

El correo diario va solo a `PERFIL_ACCESS_CORREO`. Las direcciones de las tres fuentes de ofertas y el horario de la recolección (`perfil.ofertas.*`) tienen su valor en `application.properties`; se cambian con su variable (`PERFIL_OFERTAS_CRON`, ...) solo para pruebas. La recolección programada viene apagada (`perfil.ofertas.cron=-`): cada lectura la lanza la persona. Para encenderla, un cron en `PERFIL_OFERTAS_CRON` (por ejemplo `0 0 * * * *`). Los términos de búsqueda de Computrabajo y su máximo de avisos por corrida (`perfil.computrabajo.*`) también están en `application.properties`; LinkedIn y Bumeran buscan esos mismos términos. Los actores de Apify de LinkedIn y de Bumeran están en `perfil.apify.*`.
