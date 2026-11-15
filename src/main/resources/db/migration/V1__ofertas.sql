-- Ofertas recogidas de los sitios de empleo, con su clasificación y el estado que les pone la persona.
CREATE TABLE oferta (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fuente           VARCHAR(30)   NOT NULL,
    id_externo       VARCHAR(300)  NOT NULL,
    titulo           VARCHAR(500)  NOT NULL,
    empresa          VARCHAR(300),
    publicada_en     TIMESTAMPTZ,
    ubicacion        VARCHAR(500),
    modalidad        VARCHAR(20)   NOT NULL,
    url              VARCHAR(2000) NOT NULL,
    descripcion      TEXT          NOT NULL,
    correo_contacto  VARCHAR(320),
    idioma           VARCHAR(5),
    encaje           VARCHAR(10)   NOT NULL,
    motivos          TEXT[]        NOT NULL,
    tecnologias      TEXT[]        NOT NULL,
    clave_similitud  VARCHAR(800)  NOT NULL,
    estado           VARCHAR(12)   NOT NULL DEFAULT 'NUEVA',
    postulada_en     TIMESTAMPTZ,
    publicada        BOOLEAN       NOT NULL DEFAULT TRUE,
    vista_primera    TIMESTAMPTZ   NOT NULL,
    vista_ultima     TIMESTAMPTZ   NOT NULL,
    avisada_en       TIMESTAMPTZ,
    CONSTRAINT oferta_una_por_fuente UNIQUE (fuente, id_externo),
    CONSTRAINT oferta_estado_valido CHECK (estado IN ('NUEVA', 'GUARDADA', 'DESCARTADA', 'POSTULADA')),
    CONSTRAINT oferta_encaje_valido CHECK (encaje IN ('ALTO', 'MEDIO', 'NO_ENCAJA')),
    CONSTRAINT oferta_postulada_con_fecha CHECK ((estado = 'POSTULADA') = (postulada_en IS NOT NULL))
);

CREATE INDEX oferta_clave_similitud ON oferta (clave_similitud);
CREATE INDEX oferta_estado_encaje ON oferta (estado, encaje);

-- Resultado de la última lectura de cada fuente.
CREATE TABLE fuente (
    nombre           VARCHAR(30)  PRIMARY KEY,
    estado           VARCHAR(10)  NOT NULL,
    detalle          VARCHAR(300),
    ultima_lectura   TIMESTAMPTZ  NOT NULL,
    ultima_correcta  TIMESTAMPTZ,
    ofertas_leidas   INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT fuente_estado_valido CHECK (estado IN ('CORRECTA', 'FALLO', 'BLOQUEO'))
);

-- Un correo de aviso por día como máximo: la clave primaria lo garantiza.
CREATE TABLE aviso (
    dia        DATE         PRIMARY KEY,
    enviado_en TIMESTAMPTZ  NOT NULL,
    ofertas    INTEGER      NOT NULL
);
