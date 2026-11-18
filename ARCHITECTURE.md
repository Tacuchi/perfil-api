# Arquitectura de perfil-api

Revisado el 2026-11-18 contra la versión 0.0.1-SNAPSHOT (commit `53fedef`).

Este documento da la vista general: qué partes existen, cómo se hablan y qué reglas no se rompen. Describe la api del sistema del perfil profesional: el historial del CV y el monitor de ofertas. No describe el sitio público, el panel ni el lector local, que viven en otros repositorios, ni la configuración del proxy de acceso fuera del código. Todos los valores son los de por defecto; nada aquí se midió en una máquina concreta.

## 1. Propósito y metas de calidad

perfil-api guarda las versiones del CV de la persona y entrega la vigente en `/cv`, la única ruta pública. Además recoge ofertas de trabajo de seis fuentes, las clasifica contra las tecnologías del CV y las lista para el panel. La usan la persona, desde el panel, y cualquier visitante que abre el CV.

Qué no es:

- No es multiusuario: acepta un solo correo, el de la persona.
- No corre en varias instancias: el candado y el avance de la recolección viven en memoria.
- No lee nada solo: la recolección programada viene apagada y cada lectura la lanza la persona.
- No escribe a terceros: el único destinatario del correo es la persona.
- No lee hoy ninguna fuente desde la máquina de la persona: las rutas del lector local existen, pero ninguna fuente las declara.

| Meta | Escenario |
|---|---|
| Nadie entra sin el código de acceso | Toda ruta salvo `GET /cv` exige un código firmado del proxy de acceso con la aplicación y el correo configurados. Un cambio exige además el origen del panel. |
| El gasto nunca pasa el tope | Una conexión de pago sin clave, sin tope o con su tope del mes consumido no consulta al servicio. Cada corrida pide un gasto máximo igual al saldo. |
| Un fallo no deja pasar el tope diario | Cada lectura de una bolsa pública se cuenta antes de pedirla. Admite 3 por día de Lima, terminen bien o no. |
| Una carga rechazada no deja rastro | Los archivos se validan por su contenido antes de guardar: PDF hasta 10 MB y Word `.docx` opcional. |
| Un dato que falta se nombra | Si falta una variable obligatoria, la api no arranca y nombra la que falta. Sin la llave de claves, arranca y las de pago quedan «falta configurar». |

## 2. Contexto y alcance

![Diagrama de contexto de perfil-api](docs/architecture/context.svg)

[Versión interactiva](docs/architecture/context.html): descarga el archivo y ábrelo en un navegador.

| Interfaz externa | Dirección | Cuándo ocurre |
|---|---|---|
| Visitante | Entrante, `GET /cv` | Cuando alguien abre el CV público. |
| Panel | Entrante, `/api/**` | En cada pantalla del panel y en cada cambio que hace la persona. |
| Lector local | Entrante, `/api/lector/**` | Solo con una corrida local abierta desde el panel. Hoy ninguna fuente la usa. |
| Orquestador | Entrante, despliegue | En cada push a `main` que no sea solo de documentación. |
| Proxy de acceso | Saliente, HTTPS | Al verificar códigos: la api descarga las claves de firma del emisor. |
| Base de datos | Saliente, JDBC | En cada consulta de ofertas, conexiones y lecturas, y al arrancar, para migrar. |
| Almacén R2 | Saliente, API compatible con S3 | Al subir, descargar o elegir una versión del CV y al servir `/cv`. |
| Sitio público | Saliente, `GET /404.html` | Al pedir `/cv` sin una versión vigente. |
| Bolsas públicas: Get on Board, Remotive, Remote OK | Saliente, HTTPS | Cuando la persona lanza su lectura, o con la programada encendida. |
| Apify: LinkedIn, Bumeran, Computrabajo | Saliente, HTTPS | Cuando la persona lanza la lectura de una conexión de pago configurada. |
| Servidor de correo | Saliente, SMTP | Al terminar una recolección, solo con el aviso encendido y ofertas nuevas de encaje alto. |

## 3. Bloques de construcción

![Diagrama de contenedores de perfil-api](docs/architecture/containers.svg)

[Versión interactiva](docs/architecture/containers.html): descarga el archivo y ábrelo en un navegador.

| Bloque | Responsabilidad | Código |
|---|---|---|
| Configuración | Lee las variables de entorno, las valida al arrancar y arma los clientes. | `src/main/java/net/tacuchi/perfil/config/` |
| Seguridad | Verifica el código de acceso de la persona y el del lector, y exige el origen del panel en los cambios. | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java`, `src/main/java/net/tacuchi/perfil/config/OrigenPanelFilter.java` |
| Rutas HTTP | Traducen cada petición a un servicio y cada error a una respuesta RFC 9457 sin detalle interno. | `src/main/java/net/tacuchi/perfil/controller/`, `src/main/java/net/tacuchi/perfil/exception/` |
| Versiones del CV | Valida, guarda y expulsa versiones; mantiene el índice y la vigente. | `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java`, `src/main/java/net/tacuchi/perfil/almacen/` |
| Recolección | Reserva la lectura del día, lee cada fuente, registra lo leído y su historial, y depura. | `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java` |
| Fuentes | Una pieza por sitio; las de pago pasan por el ciclo de Apify con gasto acotado. | `src/main/java/net/tacuchi/perfil/fuentes/` |
| Clasificación | Reglas fijas que dan encaje, motivos y tecnologías a cada oferta. | `src/main/java/net/tacuchi/perfil/clasificacion/`, `src/main/resources/ofertas/tecnologias.json` |
| Conexiones | Pausa, tope mensual, consumo y clave cifrada de cada fuente. | `src/main/java/net/tacuchi/perfil/service/impl/ConexionesServiceImpl.java`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java` |
| Corrida local | Abre una corrida para el lector con un código de un solo uso y recibe sus entregas. | `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java` |
| Aviso diario | Envía a la persona un correo por día con las ofertas nuevas de encaje alto. | `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java` |
| Persistencia | Consultas JDBC y migraciones Flyway. | `src/main/java/net/tacuchi/perfil/repository/`, `src/main/resources/db/migration/` |

Reglas que no se rompen:

- Toda ruta exige el código de acceso. La única excepción es `GET /cv`.
- Un cambio sin la cabecera `Origin` del panel recibe 403. La excepción son las rutas del lector, que no llama un navegador.
- Las rutas del lector solo aceptan el código del token de servicio, sin correo; las demás, solo el de la persona.
- Nunca corren dos recolecciones a la vez: programada, a demanda y corrida local comparten un candado.
- Una conexión de pago sin clave, sin tope mensual o sin la llave del servidor no consulta al servicio.
- La clave de Apify nunca vuelve en una respuesta ni se registra; viaja solo en la cabecera de autorización.
- El correo diario va solo a la persona.
- Ninguna fuente se reintenta ni prueba otra vía tras un fallo o un bloqueo.
- El índice del CV manda: los archivos expulsados se borran después de guardarlo.

## 4. Vista de ejecución

Lectura a demanda desde el panel:

1. La persona pulsa «Leer» y el panel envía `POST /api/recoleccion` o `POST /api/recoleccion/{fuente}` con su código y su origen.
2. La api toma el candado. Si ya hay otra recolección, responde 409 con `EN_CURSO`.
3. Omite cada conexión pausada, de pago sin configurar o en su tope del mes, y reserva en `lectura_dia` la lectura del día de las demás.
4. Responde 202 con lo que va a leer y lo omitido; la lectura sigue en otro hilo.
5. Lee cada fuente de una en una. Las públicas piden JSON; las de pago lanzan un actor de Apify con un gasto máximo y suman su costo al consumo del mes.
6. Clasifica y guarda cada oferta, marca la lectura y anota una fila en el historial.
7. Borra las nuevas y descartadas que no se vieron en 60 días, envía el aviso si corresponde y suelta el candado.
8. El panel consulta `GET /api/recoleccion` para ver el avance de cada fuente.

Subir una versión del CV:

1. El panel envía `POST /api/versiones` con `pdf` y, opcional, `word`.
2. La api valida el tamaño y el contenido real de cada archivo antes de guardar nada.
3. Guarda los archivos en R2 y reescribe `indice.json` con la nueva como vigente.
4. Si hay más de 10 versiones, expulsa las más antiguas, nunca la nueva ni la que era vigente, y borra sus archivos.
5. Desde ese momento `GET /cv` entrega el nuevo PDF, sin caché.

## 5. Vista de despliegue

| Qué | Por defecto | Cuándo aplica | Cómo cambiarlo |
|---|---|---|---|
| Puerto HTTP | 8080 | Siempre | `SERVER_PORT` |
| Recolección programada | Apagada (`-`), zona `America/Lima` | Siempre | Un cron en `PERFIL_OFERTAS_CRON` |
| Lecturas por día de una bolsa pública | 3 por día de Lima | En cada lectura | No se puede |
| Lecturas programadas por día | 1 por fuente | Con la programada encendida | No se puede |
| Tope mensual de LinkedIn y Bumeran | 1 USD | Hasta que la persona fija otro | `PUT /api/fuentes/{fuente}/tope` |
| Términos de búsqueda de Apify | desarrollador de software, programador, full stack | En cada lectura de pago | `PERFIL_COMPUTRABAJO_TERMINOS` |
| Avisos por corrida de Computrabajo | 25 | En cada lectura de Computrabajo | `PERFIL_COMPUTRABAJO_MAXAVISOS` |
| Plazo de una corrida de Apify | 5 min | En cada lectura de pago | `PERFIL_COMPUTRABAJO_PLAZOCORRIDA` |
| Conservación de ofertas | 60 días desde la última vez vistas, solo nuevas y descartadas | Al terminar cada recolección | No se puede |
| Versiones del CV | 10 como máximo; 10 MB por archivo | En cada carga | No se puede |
| Ofertas por lista | 500 | En `GET /api/ofertas` | No se puede |
| Conexiones a la base | 3 | Siempre | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` |
| Memoria de la JVM en la imagen | 224 MB de heap, 160 MB de metaspace | En la imagen de contenedor | `JAVA_TOOL_OPTIONS` |
| Plazos del lector local | 2 min para reportarse, 15 min por corrida | Con una corrida local abierta | No se puede |

Requisitos: JDK 25 y PostgreSQL. Las pruebas levantan su propio PostgreSQL 18 sin Docker.

Despliegues opcionales:

- **Imagen de contenedor.** Construida sobre Temurin 25 fijado por digest; corre como un usuario sin privilegios.
- **Integración continua.** Cada push corre `./mvnw verify`; en `main` publica la imagen con la etiqueta del commit y pide al orquestador que la despliegue. Un cambio solo en `docs/`, `README.md` o `ARCHITECTURE.md` no corre el flujo.
- **Enrutador.** `deploy/traefik-dynamic.yml` lleva `/api/` del dominio del panel y `/cv` del dominio del sitio a la api, y solo deja pasar tráfico de Cloudflare.
- **Aviso diario.** Con `PERFIL_AVISO_ACTIVO=true` exige `PERFIL_AVISO_REMITENTE` y `SPRING_MAIL_HOST`.
- **Lector local.** Con `PERFIL_LECTOR_TOKENID` y, opcional, `PERFIL_LECTOR_AUD`. Sin identificador, la api rechaza toda entrega.

## 6. Conceptos transversales

Datos y privacidad:

| Pregunta | Respuesta |
|---|---|
| ¿Qué sale del servidor y a dónde va? | A R2: los PDF, los Word y el índice. A las bolsas públicas: peticiones GET con un agente que nombra el monitor. A Apify: los términos de búsqueda, el gasto máximo y la clave en la cabecera. Al servidor de correo: el título, la empresa y el enlace al panel de cada oferta nueva de encaje alto. Al sitio público: la petición de su página 404. |
| ¿Qué nunca sale? | La clave de Apify en claro, salvo hacia Apify. La llave maestra. El código de la corrida local, del que solo se guarda su huella SHA-256. El detalle interno de un error. |
| ¿Cuándo ocurre? | Cuando la persona usa el panel o lanza una lectura; cuando alguien abre `/cv`; al terminar una recolección, si el aviso está encendido. Con la programada apagada, nada sale por horario. |
| ¿Dónde se guarda y por cuánto tiempo? | En la base de datos: ofertas, con el correo de contacto que traiga su texto; lecturas del día; historial de lecturas; conexiones; consumo mensual; avisos. Las ofertas nuevas y descartadas se borran a los 60 días sin verse; las guardadas y postuladas se conservan. El historial, el consumo y los avisos no tienen purga. En R2: hasta 10 versiones del CV. |
| ¿Cómo se ve exactamente lo que se envió? | No hay un registro del cuerpo de cada petición. Se ve el desenlace: `GET /api/recoleccion`, `GET /api/fuentes` con su consumo, `GET /api/fuentes/{fuente}/historial` y los logs de consola, que anotan cada lectura y cada correo enviado. |
| ¿Cómo se apaga? | Pausar una conexión detiene sus lecturas. `PERFIL_AVISO_ACTIVO=false` apaga el correo. Dejar `PERFIL_OFERTAS_CRON` en `-` mantiene apagada la programada. Quitar la llave de claves deja las de pago sin configurar. |

Seguridad:

- La api verifica cada código de acceso aunque Cloudflare ya filtre: firma, emisor, vigencia, aplicación y correo. El del lector exige el identificador del token de servicio y rechaza un código con correo.
- Sin cookies propias ni sesión; la protección entre sitios la da el filtro de origen.
- La clave de pago se cifra con AES-GCM, un vector propio y la fuente como dato asociado. La petición que la carga no la imprime.
- El código de la corrida local es de un solo uso, de 32 bytes, y se compara por su huella en tiempo constante.
- Los errores salen como RFC 9457 sin mensaje ni traza interna.
- La imagen corre con un usuario de sistema sin privilegios, el 10001.

Persistencia:

- PostgreSQL por JDBC, sin ORM. Tablas: `oferta`, `fuente`, `aviso`, `lectura_dia`, `conexion`, `consumo_mes` y `lectura_historial`.
- Flyway aplica las migraciones de `src/main/resources/db/migration/` al arrancar.
- El historial del CV vive en R2 como `indice.json` más `versiones/<id>/cv.pdf` y `cv.docx`. El índice se reescribe entero bajo un candado.

## 7. Decisiones

| Decisión | Registro |
|---|---|
| La api verifica el código del proxy de acceso aunque Cloudflare ya filtre | [0001](docs/decisions/0001-la-api-verifica-el-codigo-de-access.md) |
| Los cambios solo se aceptan con el origen del panel | [0002](docs/decisions/0002-los-cambios-exigen-el-origen-del-panel.md) |
| Una sola instancia: el candado y el avance viven en memoria | [0003](docs/decisions/0003-una-sola-instancia-con-el-candado-en-memoria.md) |
| Cada lectura se cuenta antes de pedirla al sitio | [0004](docs/decisions/0004-cada-lectura-se-cuenta-antes-de-pedirla.md) |
| La clave de pago se guarda cifrada con la fuente como dato asociado | [0005](docs/decisions/0005-claves-de-pago-cifradas-por-fuente.md) |
| Una conexión de pago nunca gasta sin límite | [0006](docs/decisions/0006-una-conexion-de-pago-nunca-gasta-sin-limite.md) |
| `/cv` no depende de que el sitio público esté arriba | [0007](docs/decisions/0007-el-cv-no-depende-del-sitio-publico.md) |
| LinkedIn y Bumeran se leen por Apify, desde el servidor | [0008](docs/decisions/0008-linkedin-y-bumeran-por-apify.md) |
| La llave de claves es opcional: sin ella, la api arranca | [0009](docs/decisions/0009-la-llave-de-claves-es-opcional.md) |
| El lector local solo corre cuando la persona lo inicia desde el panel | [0010](docs/decisions/0010-el-lector-local-solo-corre-a-pedido.md) |

No registrado aún:

- Por qué la vía de la máquina sigue en el código sin ninguna fuente que la declare.
- Por qué la recolección programada viene apagada.
- Por qué el historial del CV vive en R2 como un índice JSON y no en la base de datos.
- Por qué LinkedIn y Bumeran buscan los mismos términos que Computrabajo.
- Por qué el día de la persona se cuenta en la zona de Lima.

## Anexo: evidencia

Los números de línea refieren al commit `53fedef`. El texto de arriba no enlaza al código a propósito.

| Sección | Evidencia |
|---|---|
| 1. Propósito | `pom.xml:12-15`, `src/main/java/net/tacuchi/perfil/controller/CvPublicoController.java:17-32`, `src/main/java/net/tacuchi/perfil/model/Fuente.java:3-13` |
| 1. Qué no es | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:92-94`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java:13-20`, `src/main/resources/application.properties:15-17`, `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:20-23`, `src/main/java/net/tacuchi/perfil/model/Fuente.java:8-13`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:93-107` |
| 1. Metas | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:64-82`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:55-69`, `src/main/java/net/tacuchi/perfil/fuentes/CicloApify.java:47-59`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:17-18`, `src/main/java/net/tacuchi/perfil/repository/LecturaDiaRepository.java:25-34`, `src/main/java/net/tacuchi/perfil/service/ValidadorArchivos.java:16-61`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:9-11`, `src/main/java/net/tacuchi/perfil/config/PerfilProperties.java:17-21`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java:35-41` |
| 2. Contexto | `src/main/java/net/tacuchi/perfil/controller/CvPublicoController.java:25-32`, `src/main/java/net/tacuchi/perfil/controller/LectorController.java:20-29`, `.github/workflows/deploy.yml:6-13`, `.github/workflows/deploy.yml:64-77`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:84-97`, `src/main/resources/application.properties:9-14`, `src/main/resources/application.properties:18-32`, `src/main/java/net/tacuchi/perfil/config/AlmacenConfig.java:18-36`, `src/main/java/net/tacuchi/perfil/service/PaginaErrorSitio.java:25-44`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteFuentes.java:19-46`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteApify.java:52-66`, `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:59-76` |
| 3. Bloques | `src/main/java/net/tacuchi/perfil/config/PerfilProperties.java:22-34`, `src/main/java/net/tacuchi/perfil/config/OrigenPanelFilter.java:13-36`, `src/main/java/net/tacuchi/perfil/exception/ManejadorErrores.java:21-24`, `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java:54-80`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:40-47`, `src/main/java/net/tacuchi/perfil/fuentes/FuenteOfertas.java:7-16`, `src/main/java/net/tacuchi/perfil/clasificacion/Clasificador.java:15-21`, `src/main/java/net/tacuchi/perfil/service/impl/ConexionesServiceImpl.java:83-102`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:43-51`, `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:20-26`, `src/main/resources/db/migration/V1__ofertas.sql:1-2` |
| 3. Reglas | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:31-38`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:48-82`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:99-126`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java:13-15`, `src/main/java/net/tacuchi/perfil/service/ConexionesPago.java:25-36`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteApify.java:21-25`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:8-11`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteFuentes.java:19-21`, `src/main/java/net/tacuchi/perfil/fuentes/FuenteOfertas.java:9-10`, `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java:72-74` |
| 4. Lectura a demanda | `src/main/java/net/tacuchi/perfil/controller/RecoleccionController.java:17-51`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:155-194`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:197-215`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:231-240`, `src/main/java/net/tacuchi/perfil/fuentes/CicloApify.java:59-122`, `src/main/java/net/tacuchi/perfil/repository/OfertaRepository.java:87-91` |
| 4. Subir una versión | `src/main/java/net/tacuchi/perfil/controller/VersionesController.java:42-48`, `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java:54-80`, `src/main/java/net/tacuchi/perfil/model/Version.java:8-14`, `src/main/java/net/tacuchi/perfil/controller/CvPublicoController.java:17-20` |
| 5. Despliegue | `Dockerfile:10-19`, `src/main/resources/application.properties:10`, `src/main/resources/application.properties:15-32`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:85`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:9-30`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:19-22`, `src/main/resources/db/migration/V4__topes_apify.sql:1-4`, `src/main/java/net/tacuchi/perfil/config/PerfilProperties.java:77-83` |
| 5. Requisitos y opcionales | `pom.xml:17-21`, `Dockerfile:1-2`, `Dockerfile:10-14`, `.github/workflows/deploy.yml:1-13`, `.github/workflows/deploy.yml:35-77`, `deploy/traefik-dynamic.yml:1-17`, `deploy/traefik-dynamic.yml:61-88`, `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:43-57`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:99-118` |
| 6. Privacidad | `src/main/java/net/tacuchi/perfil/model/Version.java:8-14`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteFuentes.java:26`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteApify.java:52-60`, `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:78-94`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:43-45`, `src/main/java/net/tacuchi/perfil/exception/ManejadorErrores.java:21`, `src/main/java/net/tacuchi/perfil/fuentes/ExtractorCorreo.java:8`, `src/main/resources/db/migration/V1__ofertas.sql:13`, `src/main/java/net/tacuchi/perfil/repository/OfertaRepository.java:87-91`, `src/main/java/net/tacuchi/perfil/repository/HistorialLecturasRepository.java:19-44`, `src/main/java/net/tacuchi/perfil/controller/ConexionesController.java:32-51`, `src/main/resources/log4j2.xml:3-12`. Una búsqueda de `DELETE` en `src/main/java/net/tacuchi/perfil/repository/` solo encontró la purga de ofertas y la devolución de una lectura del día |
| 6. Seguridad | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:84-126`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:76-80`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java:18-21`, `src/main/java/net/tacuchi/perfil/dto/ClaveRequest.java:6-12`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:56`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:238-241`, `src/main/resources/application.properties:4-5`, `Dockerfile:11-14` |
| 6. Persistencia | `pom.xml:92-105`, `src/main/resources/db/migration/V1__ofertas.sql:2-50`, `src/main/resources/db/migration/V2__lecturas_del_dia.sql:3-9`, `src/main/resources/db/migration/V3__conexiones.sql:3-22`, `src/main/resources/db/migration/V5__historial_lecturas.sql:1-13`, `src/main/resources/application.properties:9`, `src/main/java/net/tacuchi/perfil/repository/IndiceRepository.java:10-28`, `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java:42-43` |
| 7. Decisiones | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:31-33`, `deploy/traefik-dynamic.yml:10`, `src/main/java/net/tacuchi/perfil/config/OrigenPanelFilter.java:13-16`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java:17-19`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:41-42`, `src/main/resources/db/migration/V2__lecturas_del_dia.sql:11`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java:18-21`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:55-58`, `src/main/java/net/tacuchi/perfil/fuentes/CicloApify.java:20`, `src/main/java/net/tacuchi/perfil/service/PaginaErrorSitio.java:13-16`, `src/main/java/net/tacuchi/perfil/config/PerfilProperties.java:92-96`, `src/main/java/net/tacuchi/perfil/service/ClaveApify.java:13-17`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:15-16`, `src/main/java/net/tacuchi/perfil/model/Fuente.java:11-12`, `src/main/java/net/tacuchi/perfil/service/ClaveApify.java:13-17`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:19-22`, `src/main/resources/db/migration/V4__topes_apify.sql:1-4`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java:35-41`, `src/main/java/net/tacuchi/perfil/service/impl/ConexionesServiceImpl.java:85-89`, `src/main/java/net/tacuchi/perfil/exception/ConfiguracionRechazadaException.java:27-31`, `src/main/java/net/tacuchi/perfil/controller/RecoleccionController.java:41-45`, `src/main/java/net/tacuchi/perfil/service/impl/CorridaLocalServiceImpl.java:93-110`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:25-28`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:269-273` |
