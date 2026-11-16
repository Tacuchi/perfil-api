-- Conserva pausas, claves y topes elegidos. Solo completa los topes que aún faltan.
INSERT INTO conexion (fuente, tope_mensual) VALUES ('LINKEDIN', 1.00), ('BUMERAN', 1.00)
ON CONFLICT (fuente) DO UPDATE SET tope_mensual = EXCLUDED.tope_mensual
WHERE conexion.tope_mensual IS NULL;

-- Una lectura puede explicar varias búsquedas parciales sin perder sus motivos.
ALTER TABLE fuente ALTER COLUMN detalle TYPE TEXT;
