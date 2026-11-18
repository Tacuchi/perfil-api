# perfil-api

> La api del perfil profesional: versiones del CV con `/cv` público y un monitor de ofertas de trabajo.

API de `<tu-dominio-del-panel>`: guarda las versiones del CV y entrega la vigente en `/cv`, y recoge, clasifica y lista ofertas de trabajo.

La usa una sola persona, desde el panel, detrás del proxy de acceso. Cada lectura de ofertas la lanza la persona, y las de pago nunca gastan más que su tope del mes.

## Instalación

### Requisitos

Requiere JDK 25 y un PostgreSQL 18.

### Configuración

Todo llega por variables de entorno; si falta una, el servicio no arranca y la nombra. La lista completa, con los valores opcionales y la recolección programada, está en [docs/configuracion.md](docs/configuracion.md).

### Despliegue

`.github/workflows/deploy.yml` prueba, publica la imagen con la etiqueta del commit y redespliega en el orquestador. Las rutas del proxy están en `deploy/traefik-dynamic.yml`. La imagen limita la JVM a 224 MB de heap; en el orquestador fija además el límite de memoria del contenedor en 512 MB. La base de datos es un PostgreSQL 18 aparte en el mismo servidor.

## Uso

### Rutas

`GET /cv` es la única ruta pública. Las demás exigen a la persona o, en `/api/lector/**`, al lector local. La tabla de rutas, sus filtros y los motivos de rechazo están en [docs/rutas.md](docs/rutas.md).

### Monitor de ofertas

- **Fuentes públicas:** Get on Board, Remotive y Remote OK, por la API pública que cada una declara. La persona lanza cada lectura desde el panel. La recolección programada, apagada por defecto, si se enciende lee una vez al día cada fuente que ese día aún no tiene lecturas. Cada una de estas tres recibe como máximo 3 lecturas por día de Lima, programadas y a demanda, terminen bien o no: cada lectura se cuenta en la base (`lectura_dia`) antes de pedirla al sitio. Ante un fallo o un bloqueo la fuente queda marcada y no se prueba otra vía; la programada no la reintenta.
- **Computrabajo:** por el actor de Apify `santamaria-automations/computrabajo-scraper`, con la clave de Apify y el tope mensual que la persona carga en Conexiones. Busca los términos configurados en Perú, por recientes, hasta 25 avisos por corrida. Cada corrida pide a Apify un gasto máximo igual al saldo del tope y suma al consumo del mes de Lima lo que Apify informa; si ese costo no se asienta a tiempo, se calcula por el número de avisos con los precios de la corrida. Alcanzado el tope, no se lee y Apify no recibe ninguna consulta. No tiene tope diario. La clave viaja solo en la cabecera de autorización.
- **LinkedIn y Bumeran:** también por Apify, con la misma clave y un tope mensual propio, de 1 USD por defecto. Buscan los mismos términos: LinkedIn en Perú y en remoto para Latinoamérica; Bumeran en Perú.
- **Una recolección a la vez:** el candado y el avance viven en memoria, así que la API corre como una sola instancia.
- **Clasificación:** reglas fijas en `clasificacion/Clasificador.java`. Las tecnologías del CV están en `src/main/resources/ofertas/tecnologias.json`, copiadas a mano del hub; si el CV cambia, hay que copiarlas de nuevo.
- **Conservación:** las nuevas y las descartadas se eliminan 60 días después de vistas por última vez; las guardadas y las postuladas se conservan.
- **Esquema:** `src/main/resources/db/migration/`, aplicado por Flyway al arrancar.

### Desarrollo

`./mvnw verify` corre todas las pruebas y `./mvnw spring-boot:test-run` levanta la API en local con dobles de prueba para recorrerla con el panel. Los dobles, el código local y los mandos están en [docs/desarrollo.md](docs/desarrollo.md).

## Cómo funciona

La api es un solo proceso Spring Boot. Guarda las ofertas y las conexiones en la base de datos y los archivos del CV en R2. Lee las ofertas de las bolsas públicas y de Apify, y escribe solo a la persona. Toda petición, salvo `/cv`, llega con el código del proxy de acceso.

![Diagrama de contexto de perfil-api](docs/architecture/context.svg)

El cuadro completo está en [ARCHITECTURE.md](ARCHITECTURE.md).

## Contribuir

Es un proyecto personal: no se aceptan contribuciones externas. Para trabajar en el código, ver [docs/desarrollo.md](docs/desarrollo.md).

## Licencia

Sin licencia de uso: todos los derechos reservados. No hay archivo de licencia ni licencia declarada en `pom.xml`.
