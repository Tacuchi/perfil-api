CREATE TABLE lectura_historial (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fuente TEXT NOT NULL,
    inicio TIMESTAMPTZ NOT NULL,
    duracion_ms BIGINT NOT NULL CHECK (duracion_ms >= 0),
    estado TEXT NOT NULL CHECK (estado IN ('CORRECTA', 'FALLO', 'BLOQUEO')),
    parcial BOOLEAN NOT NULL,
    leidas INTEGER NOT NULL CHECK (leidas >= 0),
    nuevas INTEGER NOT NULL CHECK (nuevas >= 0),
    costo_usd NUMERIC,
    motivo TEXT
);
CREATE INDEX lectura_historial_fuente_inicio ON lectura_historial (fuente, inicio DESC, id DESC);
