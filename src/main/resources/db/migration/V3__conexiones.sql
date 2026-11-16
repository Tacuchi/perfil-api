-- Lo que la persona administra de cada conexión: pausa, tope mensual y clave cifrada; y el consumo de las de pago.
-- Solo agrega tablas; no toca oferta, fuente, aviso ni lectura_dia. Una conexión sin fila está activa y sin clave.
CREATE TABLE conexion (
    fuente            VARCHAR(30)    PRIMARY KEY,
    pausada           BOOLEAN        NOT NULL DEFAULT FALSE,
    motivo_pausa      VARCHAR(300),
    tope_mensual      NUMERIC(10, 2),
    clave_cifrada     BYTEA,
    clave_cargada_en  TIMESTAMPTZ,
    CONSTRAINT conexion_tope_positivo CHECK (tope_mensual IS NULL OR tope_mensual > 0),
    CONSTRAINT conexion_clave_con_fecha CHECK ((clave_cifrada IS NULL) = (clave_cargada_en IS NULL))
);

-- Consumo de cada conexión de pago por mes de Lima (el primer día del mes identifica el mes), en dólares.
CREATE TABLE consumo_mes (
    fuente   VARCHAR(30)    NOT NULL,
    mes      DATE           NOT NULL,
    consumo  NUMERIC(12, 4) NOT NULL,
    PRIMARY KEY (fuente, mes),
    CONSTRAINT consumo_mes_no_negativo CHECK (consumo >= 0),
    CONSTRAINT consumo_mes_primer_dia CHECK (EXTRACT(DAY FROM mes) = 1)
);
