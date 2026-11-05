-- Un reporte por número y por red (ip) y por instalación. Las IP y los identificadores se guardan
-- como huellas con sal secreta: la base no contiene IP ni identificadores legibles.
CREATE TABLE IF NOT EXISTS reportes (
  huella      TEXT    NOT NULL,   -- huella del número reportado
  ip          TEXT    NOT NULL,   -- huella de la IP de quien reporta
  instalacion TEXT    NOT NULL,   -- huella del identificador anónimo de la app
  categoria   TEXT    NOT NULL,
  creado      INTEGER NOT NULL,   -- milisegundos desde 1970
  UNIQUE (huella, ip),
  UNIQUE (huella, instalacion)
);
CREATE INDEX IF NOT EXISTS reportes_creado ON reportes (creado);

-- Contador diario para limitar cuántos reportes puede mandar una IP o una instalación.
CREATE TABLE IF NOT EXISTS limites (
  clave TEXT    NOT NULL,
  dia   TEXT    NOT NULL,
  n     INTEGER NOT NULL,
  PRIMARY KEY (clave, dia)
);
