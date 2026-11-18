# Rutas de la api

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
| `GET /api/fuentes` | persona | Estado de cada fuente: `CORRECTA`, `FALLO`, `BLOQUEO` o `SIN_LEER`, con su última lectura correcta y las lecturas que le quedan hoy (`lecturasRestantes`). |
| `GET /api/fuentes/{fuente}/historial` | persona | Las lecturas de una fuente, de 20 en 20, de la más reciente a la más antigua; `antesDe=<id>` pide la página siguiente. |
| `POST /api/fuentes/{fuente}/pausar` · `/reanudar` | persona | Pausa o reanuda una conexión. Una pausada no se lee ni se cuenta. |
| `PUT /api/fuentes/{fuente}/clave` | persona | `{"clave": "..."}` carga o reemplaza la clave de Apify, una sola para las tres conexiones de pago. Se guarda cifrada y nunca vuelve: `GET /api/fuentes` solo dice `tieneClave` y `claveCargadaEn`. Sin la llave de claves del servidor: `409` con motivo `FALTA_LLAVE`. |
| `PUT /api/fuentes/{fuente}/tope` | persona | `{"topeMensual": 5}` fija el tope de gasto del mes, en dólares. `GET /api/fuentes` muestra además `consumoMes` y `queFalta`. |
| `POST /api/recoleccion` | persona | Lanza ahora la lectura de todas las fuentes con lecturas disponibles hoy. Responde `202` con lo que va a leer y lo omitido por tope; lee en segundo plano. |
| `POST /api/recoleccion/{fuente}` | persona | Lanza ahora la lectura de una fuente. |
| `POST /api/recoleccion/maquina` | persona | Abre una corrida para el lector local y responde `202` con su código de un solo uso. Hoy ninguna fuente se lee desde la máquina, así que se rechaza con `SIN_FUENTES`. |
| `GET /api/recoleccion` | persona | Avance de la recolección en curso (programada o a demanda) o resultado de la última: cada fuente lanzada como `PENDIENTE`, `LEYENDO` o `TERMINADA`, con su resultado. |
| `GET /api/tecnologias` | persona | Tecnologías del CV con las que se clasifica, principales y secundarias. |
| `POST /api/lector/corrida/iniciar` · `/terminar` | lector | Con el código de la corrida en el cuerpo, el lector local la inicia o la cierra. |
| `POST /api/lector/corrida/fuentes/{fuente}/empezar` · `/entregar` | lector | El lector avisa que empieza a leer una fuente y luego entrega sus avisos, 25 como máximo. |

Un lanzamiento se rechaza con `409` y su `motivo`: `EN_CURSO` (ya corre otra), `TOPE_DIARIO` (la fuente ya recibió sus lecturas de hoy), `PAUSADA`, `DESDE_MAQUINA`, `FALTA_CONFIGURAR` (a una de pago le falta su clave, su tope o la llave del servidor), `TOPE_MENSUAL` (una de pago consumió su tope del mes) o `SIN_FUENTES` (ninguna se puede leer).

«Persona» = petición con el código del proxy de acceso (`Cf-Access-Jwt-Assertion`) válido para la aplicación y el correo configurados. Los cambios además exigen `Origin` igual a la dirección del panel.

«Lector» = petición con el código del token de servicio del lector, sin correo. Estas rutas no exigen `Origin`.
