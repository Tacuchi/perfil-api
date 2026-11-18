# Arquitectura de perfil-api

Revisado el 2026-11-18 contra la versión 0.0.1-SNAPSHOT (commit `53fedef`).

Vista general de la api del perfil profesional: el historial del CV y el monitor de ofertas. No cubre el sitio público, el panel ni el lector local, que viven en otros repositorios. Todos los valores son los de por defecto.

En resumen:

- Guarda las versiones del CV y entrega la vigente en `/cv`, la única ruta pública.
- Recoge ofertas de seis fuentes, las clasifica contra el CV y las lista para el panel.
- La usa una sola persona: toda otra ruta exige su código de acceso.
- Los datos viven en PostgreSQL (ofertas) y en R2 (archivos del CV).
- Nada se lee solo: cada lectura la lanza la persona, y las de pago tienen tope.

## 1. Propósito y metas de calidad

perfil-api es el servidor del sistema del perfil profesional. Atiende al panel de la persona y a cualquier visitante que abre el CV.

Qué no es:

- No es multiusuario: acepta un solo correo.
- No corre en varias instancias: el candado de la recolección vive en memoria.
- No escribe a terceros: el único destinatario del correo es la persona.

| Meta | Escenario |
|---|---|
| Nadie entra sin el código de acceso | Toda ruta salvo `GET /cv` exige un código firmado del proxy de acceso; un cambio exige además el origen del panel. |
| El gasto nunca pasa el tope | Una conexión de pago sin clave, sin tope o con el tope del mes consumido no consulta al servicio. |
| Una carga rechazada no deja rastro | Los archivos se validan por su contenido antes de guardar nada. |

## 2. Contexto y alcance

![Diagrama de contexto de perfil-api](docs/architecture/context.svg)

[Versión interactiva](https://tacuchi.github.io/perfil-api/architecture/context.html).

| Interfaz externa | Dirección | Cuándo ocurre |
|---|---|---|
| Visitante | Entrante, `GET /cv` | Al abrir el CV público. |
| Panel | Entrante, `/api/**` | En cada pantalla y en cada cambio de la persona. |
| Proxy de acceso | Saliente, HTTPS | Al verificar un código: descarga las claves de firma. |
| Base de datos | Saliente, JDBC | En cada consulta, y al arrancar para migrar. |
| Almacén R2 | Saliente, API de S3 | Al subir, elegir o servir una versión del CV. |
| Fuentes de ofertas | Saliente, HTTPS | Cuando la persona lanza una lectura: tres bolsas públicas y tres por Apify. |
| Servidor de correo | Saliente, SMTP | Al terminar una recolección, con el aviso encendido. |

El lector local y el orquestador de despliegue también llaman a la api; el diagrama los muestra.

## 3. Bloques de construcción

![Diagrama de contenedores de perfil-api](docs/architecture/containers.svg)

[Versión interactiva](https://tacuchi.github.io/perfil-api/architecture/containers.html).

El código vive en `src/main/java/net/tacuchi/perfil/`; la tabla nombra la carpeta de cada bloque.

| Bloque | Responsabilidad | Código |
|---|---|---|
| Configuración y seguridad | Valida las variables al arrancar y verifica cada código de acceso. | `config/` |
| Rutas HTTP | Traducen cada petición a un servicio y cada error a RFC 9457. | `controller/`, `exception/` |
| Versiones del CV | Valida, guarda y expulsa versiones; mantiene la vigente. | `service/`, `almacen/` |
| Recolección y fuentes | Reserva la lectura del día y lee cada sitio, uno a la vez. | `service/`, `fuentes/` |
| Clasificación | Reglas fijas que dan encaje y tecnologías a cada oferta. | `clasificacion/` |
| Conexiones de pago | Pausa, tope mensual, consumo y clave cifrada por fuente. | `service/` |
| Persistencia | Consultas JDBC y migraciones Flyway. | `repository/` |

Reglas que no se rompen:

- Toda ruta exige el código de acceso. La única excepción es `GET /cv`.
- Un cambio sin el origen del panel recibe 403. La excepción son las rutas del lector.
- Nunca corren dos recolecciones a la vez.
- Una conexión de pago sin clave, sin tope o sin la llave del servidor no consulta al servicio.
- La clave de pago nunca vuelve en una respuesta ni se registra.

## 4. Vista de ejecución

Lectura a demanda desde el panel:

1. La persona pulsa «Leer» y el panel envía `POST /api/recoleccion`.
2. La api toma el candado; si ya hay una recolección, responde 409.
3. Omite las conexiones pausadas o sin tope y reserva la lectura del día de las demás.
4. Responde 202 y sigue leyendo en otro hilo, fuente por fuente.
5. Clasifica y guarda cada oferta, y anota la lectura en el historial.
6. Depura las ofertas viejas, envía el aviso si corresponde y suelta el candado.

Subir una versión del CV sigue el mismo patrón: validar, guardar en R2, reescribir el índice y expulsar las versiones sobrantes.

## 5. Vista de despliegue

| Qué | Por defecto | Cuándo aplica | Cómo cambiarlo |
|---|---|---|---|
| Puerto HTTP | 8080 | Siempre | `SERVER_PORT` |
| Recolección programada | Apagada | Siempre | Un cron en `PERFIL_OFERTAS_CRON` |
| Tope mensual de una fuente de pago | 1 USD | Hasta que la persona fija otro | `PUT /api/fuentes/{fuente}/tope` |
| Términos de búsqueda | Tres términos de desarrollo de software | En cada lectura de pago | `PERFIL_COMPUTRABAJO_TERMINOS` |
| Aviso diario por correo | Apagado | Al terminar una recolección | `PERFIL_AVISO_ACTIVO` |
| Conexiones a la base | 3 | Siempre | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` |
| Memoria de la JVM | 224 MB de heap | En la imagen de contenedor | `JAVA_TOOL_OPTIONS` |

Los límites fijos (lecturas por día, versiones del CV, conservación de ofertas) están en el código; los lista [la guía de configuración](docs/configuracion.md#límites-fijos).

Requisitos: JDK 25 y PostgreSQL.

Despliegue: cada push a `main` construye la imagen de contenedor y pide al orquestador que la despliegue. Un cambio solo de documentación no corre el flujo.

## 6. Conceptos transversales

Datos y privacidad:

| Pregunta | Respuesta |
|---|---|
| ¿Qué sale del servidor y a dónde va? | Los archivos del CV a R2, las búsquedas a las fuentes de ofertas y el aviso al correo de la persona. |
| ¿Qué nunca sale? | La clave de pago en claro, salvo hacia su servicio; la llave maestra; el detalle interno de un error. |
| ¿Cuándo ocurre? | Cuando la persona usa el panel o lanza una lectura, y cuando alguien abre `/cv`. |
| ¿Dónde se guarda y por cuánto tiempo? | Ofertas en la base: las no guardadas se borran a los 60 días. El CV en R2: hasta 10 versiones. |
| ¿Cómo se ve exactamente lo que se envió? | No se registra el cuerpo de cada petición; se ve el historial de lecturas y el consumo por fuente. |
| ¿Cómo se apaga? | Pausar una conexión detiene sus lecturas; el aviso y la recolección programada vienen apagados. |

Seguridad:

- La api verifica firma, emisor, vigencia, aplicación y correo de cada código, aunque el proxy ya filtre.
- La clave de pago se cifra con AES-GCM y la fuente como dato asociado.
- Los errores salen sin mensaje ni traza interna.

Persistencia: PostgreSQL por JDBC, sin ORM, con migraciones Flyway. El historial del CV vive en R2 como un índice JSON más los archivos de cada versión.

## 7. Decisiones

Los diez registros están en [docs/decisions/](docs/decisions/). Los que más pesan:

| Decisión | Registro |
|---|---|
| La api verifica el código del proxy de acceso aunque Cloudflare ya filtre | [0001](docs/decisions/0001-la-api-verifica-el-codigo-de-access.md) |
| Una sola instancia: el candado y el avance viven en memoria | [0003](docs/decisions/0003-una-sola-instancia-con-el-candado-en-memoria.md) |
| Cada lectura se cuenta antes de pedirla al sitio | [0004](docs/decisions/0004-cada-lectura-se-cuenta-antes-de-pedirla.md) |
| Una conexión de pago nunca gasta sin límite | [0006](docs/decisions/0006-una-conexion-de-pago-nunca-gasta-sin-limite.md) |
| LinkedIn y Bumeran se leen por Apify, desde el servidor | [0008](docs/decisions/0008-linkedin-y-bumeran-por-apify.md) |

No registrado aún:

- Por qué la recolección programada viene apagada.
- Por qué el historial del CV vive en R2 y no en la base de datos.
- Por qué el día de la persona se cuenta en la zona de Lima.

## Anexo: evidencia

Los números de línea refieren al commit `53fedef`. El texto de arriba no enlaza al código a propósito.

| Sección | Evidencia |
|---|---|
| 1. Propósito y metas | `src/main/java/net/tacuchi/perfil/controller/CvPublicoController.java:17-32`, `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:64-82`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:55-69`, `src/main/java/net/tacuchi/perfil/service/ValidadorArchivos.java:16-61` |
| 2. Contexto | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:84-97`, `src/main/java/net/tacuchi/perfil/config/AlmacenConfig.java:18-36`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteFuentes.java:19-46`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteApify.java:52-66` |
| 3. Bloques y reglas | `src/main/java/net/tacuchi/perfil/config/OrigenPanelFilter.java:13-36`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java:13-15`, `src/main/java/net/tacuchi/perfil/service/ConexionesPago.java:25-36`, `src/main/java/net/tacuchi/perfil/fuentes/ClienteApify.java:21-25` |
| 4. Ejecución | `src/main/java/net/tacuchi/perfil/controller/RecoleccionController.java:17-51`, `src/main/java/net/tacuchi/perfil/service/impl/RecoleccionServiceImpl.java:155-194`, `src/main/java/net/tacuchi/perfil/service/impl/VersionesServiceImpl.java:54-80` |
| 5. Despliegue | `src/main/resources/application.properties:15-32`, `src/main/java/net/tacuchi/perfil/model/Reglas.java:9-30`, `Dockerfile:10-19`, `.github/workflows/deploy.yml:6-13` |
| 6. Transversales | `src/main/java/net/tacuchi/perfil/service/impl/AvisoServiceImpl.java:78-94`, `src/main/java/net/tacuchi/perfil/repository/OfertaRepository.java:87-91`, `src/main/java/net/tacuchi/perfil/service/CifradoClaves.java:18-21`, `src/main/java/net/tacuchi/perfil/exception/ManejadorErrores.java:21` |
| 7. Decisiones | `src/main/java/net/tacuchi/perfil/config/SeguridadConfig.java:31-33`, `src/main/java/net/tacuchi/perfil/service/CandadoRecoleccion.java:17-19`, `src/main/java/net/tacuchi/perfil/model/Conexion.java:55-58` |
