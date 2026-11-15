-- Lecturas de cada fuente por día de Lima, contadas antes de pedirlas al sitio: el tope diario se aplica sobre esta cuenta.
-- Solo agrega la tabla y la siembra; no toca oferta, fuente ni aviso.
CREATE TABLE lectura_dia (
    fuente    VARCHAR(30)  NOT NULL,
    dia       DATE         NOT NULL,
    lecturas  INTEGER      NOT NULL,
    PRIMARY KEY (fuente, dia),
    CONSTRAINT lectura_dia_positiva CHECK (lecturas > 0)
);

-- La última lectura de cada fuente cuenta como una lectura de su día: el día del despliegue no se lee de más.
INSERT INTO lectura_dia (fuente, dia, lecturas)
SELECT nombre, CAST(ultima_lectura AT TIME ZONE 'America/Lima' AS DATE), 1
FROM fuente;
