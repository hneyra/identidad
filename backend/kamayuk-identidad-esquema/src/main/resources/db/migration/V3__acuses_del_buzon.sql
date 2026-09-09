-- ============================================================================
--  V3 — EL ACUSE DEL BUZON, POR CONSUMIDOR (etapa 3 de `infrastructure#52`,
--       ADR-0028 §3, ADR-0039)
--
--  QUE FALTABA
--  -----------
--  `V2` dejo el buzon escrito y sin nadie que lo sirviera: la etapa 2 es el lado
--  que EMITE. Para servirlo hace falta contestar una pregunta que `V2` dejo
--  planteada con todas las letras y no contesto: «que le falta a cada uno».
--
--  ----------------------------------------------------------------------------
--  POR QUE ESTO ES UNA TABLA Y NO UNA COLUMNA (la decision entera de `V2`)
--  ----------------------------------------------------------------------------
--
--  `catastro_evento` lleva `estado`, `intentos` y `entregado_en` porque tiene UN
--  consumidor: `rentas`. «Entregado» es entonces una sola cosa y cabe en una
--  columna.
--
--  Aqui hay CUATRO —`rentas`, `catastro`, `normativa` y `caja`—. Con una columna,
--  el primero que acusara retiraria el evento para los otros tres: los tres se
--  quedarian sin el, EN VERDE, y el sintoma llegaria semanas despues como un 403
--  de un permiso que aqui si esta. La alternativa —marcar entregado cuando acusa
--  el ULTIMO— es igual de mala por el otro lado: un consumidor caido bloquea la
--  entrega a los otros tres.
--
--  Asi que el acuse es POR CONSUMIDOR y vive aqui. `identidad_evento` se queda
--  INMUTABLE, que es lo que sus privilegios ya decian: se inserta y se lee.
--
--  ----------------------------------------------------------------------------
--  LOS CUATRO DEL `CHECK`, Y POR QUE `identidad` NO ESTA
--  ----------------------------------------------------------------------------
--
--  Los consumidores del producto son cinco menos uno: **`identidad` no se consume
--  a si mismo**. Lo que este buzon publica ES lo que esta base acaba de escribir,
--  asi que un acuse con `consumidor = 'identidad'` solo puede significar una de
--  dos cosas, las dos defectos: que alguien monto el ingestor apuntando a su
--  propio buzon, o que una cuenta de servicio de este sistema esta acusando lo
--  que nadie ha consumido — y lo acusado no se vuelve a servir. El `CHECK` lo
--  rechaza en el motor, y `Consumidor.deAzp` lo rechaza antes, en el borde.
--
--  El `CHECK` es una lista escrita y se sabe: son los cuatro sistemas del
--  producto, que es una cifra que solo cambia con un ADR (ADR-0029, ADR-0039).
--  Lo que ata esta lista con la del codigo es `ConsumidorTest`, que compara el
--  enumerado contra `SistemasDelProducto` — sin ella serian dos sitios con la
--  misma verdad y el que se quedaria viejo seria justo el del motor, cuyo rojo
--  llega en produccion.
--
--  ----------------------------------------------------------------------------
--  LA CLAVE PRIMARIA LLEVA EL CONSUMIDOR, Y ESA ES TODA LA TABLA
--  ----------------------------------------------------------------------------
--
--  `(municipalidad_id, consumidor, evento_id)`. Sin `consumidor` dentro, el primer
--  acuse de `rentas` haria imposible el de `caja` —clave duplicada— o, peor, con
--  un `ON CONFLICT DO NOTHING` lo aceptaria en silencio y `caja` se quedaria sin
--  el evento creyendo que lo acuso. Es exactamente el defecto que esta tabla
--  existe para no tener, escrito en la clave.
--
--  ----------------------------------------------------------------------------
--  LA FORANEA VA A `(municipalidad_id, evento_id)`, NO A `id`
--  ----------------------------------------------------------------------------
--
--  Porque lo que viaja al consumidor y vuelve en su acuse es el `evento_id`: el
--  `id` es la SECUENCIA, que ordena la entrega y no la identifica (`V2` lo dice al
--  detalle — se asigna al INSERT y no al COMMIT). La foranea es COMPUESTA con la
--  municipalidad a proposito: sin ella, y aunque RLS lo impida hoy, la tabla
--  admitiria por su forma un acuse de una municipalidad sobre un evento de otra.
--
--  Y aqui SI hay foranea, al reves que en `identidad_evento.sujeto_id`: un acuse
--  de un evento que no existe no es un hecho que haya que conservar, es un error
--  del cliente. Se rechaza, y `BuzonDeIdentidadJdbc.acusar` lo comprueba ANTES
--  para poder nombrar el evento en vez de devolver el nombre de una restriccion.
--
--  ----------------------------------------------------------------------------
--  NO HAY `UPDATE` NI `DELETE`, POR EL MISMO MOTIVO QUE EN `V2`
--  ----------------------------------------------------------------------------
--
--  Un acuse no cambia: o esta o no esta. `UPDATE` no tendria que tocar —ninguna
--  columna cambia despues de escribirse— y `DELETE` seria volver a servir un
--  evento que un consumidor ya aplico, o sea reabrir la unica cosa que este
--  mecanismo cierra. Que un evento se entregue dos veces es aceptable —la entrega
--  es AL MENOS UNA VEZ y el receptor deduplica por `evento_id`—; que un acuse se
--  borre sin que nadie lo sepa, no.
-- ============================================================================

CREATE TABLE identidad_evento_acuse (
    municipalidad_id bigint       NOT NULL REFERENCES municipalidad (id),
    consumidor       varchar(20)  NOT NULL,
    evento_id        uuid         NOT NULL,
    acusado_en       timestamptz  NOT NULL,

    CONSTRAINT identidad_evento_acuse_pk
        PRIMARY KEY (municipalidad_id, consumidor, evento_id),

    -- Compuesta con la municipalidad: ver la cabecera. Casa con
    -- `identidad_evento_uq`, que es UNIQUE (municipalidad_id, evento_id).
    CONSTRAINT identidad_evento_acuse_evento_fk
        FOREIGN KEY (municipalidad_id, evento_id)
        REFERENCES identidad_evento (municipalidad_id, evento_id),

    CONSTRAINT identidad_evento_acuse_consumidor_ck
        CHECK (consumidor IN ('rentas', 'catastro', 'normativa', 'caja'))
);

-- El indice que sirve la pregunta de cada vuelta: «lo que este consumidor no ha
-- acusado». La consulta es un LEFT JOIN de `identidad_evento` contra esta por
-- (municipalidad_id, evento_id) con el consumidor fijado, y la clave primaria
-- lleva las tres columnas en ese orden — asi que el indice de la PK ya la sirve y
-- no se declara ninguno mas. Se dice aqui para que nadie lo anada creyendo que
-- falta: un indice de mas cuesta en cada acuse y no acelera ninguna lectura
-- (C-12 midio lo que cuesta un indice que promete una busqueda que no ocurre).

COMMENT ON TABLE identidad_evento_acuse IS
    'Que evento del buzon ha aplicado ya cada consumidor (etapa 3, ADR-0028 §3). Es una tabla y no '
    'una columna de `identidad_evento` porque hay CUATRO consumidores: con una sola columna, el '
    'primero que acusara retiraria el evento para los otros tres, en verde. Se inserta y se lee: '
    'ni UPDATE ni DELETE.';
COMMENT ON COLUMN identidad_evento_acuse.consumidor IS
    'Cual de los cuatro sistemas acuso. NO llega como parametro de la peticion: sale del `azp` del '
    'token de servicio —`kamayuk-<sistema>-servicio-<ubigeo>`— porque un cliente que pudiera decir '
    '«soy caja» acusaria en nombre de otro, y lo acusado no se vuelve a servir. `identidad` no esta '
    'entre los cuatro: no se consume a si mismo.';
COMMENT ON COLUMN identidad_evento_acuse.evento_id IS
    'El `evento_id` y no la secuencia: es lo que viaja al consumidor y lo que vuelve en su acuse. '
    'La secuencia ordena la entrega y no la identifica (ver V2).';
COMMENT ON COLUMN identidad_evento_acuse.acusado_en IS
    'Cuando se acuso, con el reloj de la aplicacion y no con `now()` de la base: el motor es otro '
    'proceso y otra maquina, y un instante que pone la base no se puede fijar en una prueba.';

-- ----------------------------------------------------------------------------
--  RLS. Sin valor por omision: sin contexto de tenant, la consulta FALLA.
--  Y FORCE, porque sin el el DUENO de la tabla la omite (DAT-01 §0, hallazgo 1).
-- ----------------------------------------------------------------------------

ALTER TABLE identidad_evento_acuse ENABLE ROW LEVEL SECURITY;
ALTER TABLE identidad_evento_acuse FORCE ROW LEVEL SECURITY;
CREATE POLICY identidad_evento_acuse_tenant ON identidad_evento_acuse FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- ----------------------------------------------------------------------------
--  PRIVILEGIOS
--
--  `INSERT, SELECT` y nada mas, igual que el buzon que acompana. Ver la cabecera:
--  un acuse no cambia, y borrarlo es volver a servir lo que ya se aplico.
-- ----------------------------------------------------------------------------

GRANT INSERT, SELECT ON identidad_evento_acuse TO kamayuk_app;
GRANT SELECT          ON identidad_evento_acuse TO kamayuk_readonly;
