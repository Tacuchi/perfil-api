# Desarrollo

Requiere JDK 25. `./mvnw verify` corre todas las pruebas, sobre un PostgreSQL 18 real que se levanta solo y sin Docker. Las fuentes se prueban contra sus respuestas reales guardadas (`src/test/resources/fuentes/`), con los correos de terceros reemplazados por direcciones de ejemplo; el correo diario, contra un servidor SMTP de prueba.

`./mvnw spring-boot:test-run` levanta la API en `http://localhost:8080` para recorrerla con el panel: almacén en memoria, un sitio de prueba para la página 404, un Access de prueba que publica sus claves en local, un PostgreSQL de pruebas, las tres fuentes servidas desde sus respuestas guardadas, un Apify de prueba para las tres conexiones de pago, LinkedIn, Bumeran y Computrabajo (su clave es `clave-de-prueba`), y un servidor de correo de prueba con el aviso encendido. El código firmado para la persona queda en `target/codigo-local.jwt`; el panel lo envía con `PERFIL_CODIGO_LOCAL="$(cat target/codigo-local.jwt)" npm start`. El del token de servicio del lector queda en `target/codigo-lector-local.jwt`. La recolección no corre por horario: se dispara con los mandos locales, que exigen el mismo código y el origen del panel:

| Mando | Qué hace |
|---|---|
| `POST /api/local/recoger` | Corre la programada y espera a que termine (cada fuente sin lecturas en el día del reloj local). |
| `POST /api/local/programada` | Dispara la programada en segundo plano y responde sin esperarla. |
| `POST /api/local/dia-siguiente` | Adelanta el reloj un día y dispara la programada sin esperarla. |
| `POST /api/local/adelantar?dias=1` | Adelanta el reloj sin leer nada. |
| `PUT /api/local/fuentes/{fuente}?modo=BIEN\|FALLA\|BLOQUEA` | Hace que una fuente responda bien, falle o bloquee. |
| `PUT /api/local/fuentes/{fuente}/pausa?activa=true\|false` | La fuente recibe las peticiones y retiene la respuesta hasta que se la suelta (máximo 50 s), para ver el paso «leyendo». |
| `GET /api/local/apify` | Cuántas consultas y lanzamientos recibió el Apify de prueba desde que arrancó. |
| `PUT /api/local/apify/{fuente}?modo=...` | Elige cómo responde el Apify de prueba a una conexión de pago. |
| `POST /api/local/lector` | Hace de lector local sobre una corrida abierta. |
| `GET /api/local/correos` | Asunto y destinatarios de los correos recibidos. |

Nada de esto entra al jar. El contrato del almacén corre también contra R2 cuando están definidas las cuatro variables `PERFIL_ALMACEN_*`.
