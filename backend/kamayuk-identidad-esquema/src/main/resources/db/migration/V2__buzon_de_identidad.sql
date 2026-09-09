-- ============================================================================
--  V2 — EL BUZON DE SALIDA DE `identidad` (etapa 2 de `infrastructure#52`,
--       ADR-0028 §3, ADR-0039)
--
--  QUE FALTABA
--  -----------
--  La etapa 1 dejo el esquema entero y `kamayuk-identidad-nucleo` vacio: las once
--  escrituras de administracion seguian en `rentas` y aqui no habia nada que
--  publicar. La etapa 2 las trae, y con ellas la pregunta que ADR-0039 contesta:
--  como llega a los otros cuatro sistemas lo que aqui se escribe. La respuesta es
--  esta tabla, y se construye AHORA y no en la etapa 3 porque «el evento se escribe
--  en la MISMA transaccion que la escritura» es una propiedad del camino de
--  escritura: construirla despues es retrofit, y un retrofit sobre once metodos que
--  ya funcionan es la forma de que uno de los once se quede sin emitir y nadie lo
--  note. La etapa 3 es el lado que SIRVE estos eventos.
--
--  QUE ES ESTA TABLA
--  -----------------
--  Un OUTBOX TRANSACCIONAL: la fila se escribe en la MISMA transaccion que el hecho
--  que la produjo, y un proceso aparte la entrega. Es la misma pieza que `V5` de
--  `catastro` (`catastro_evento`) y que `V2` de `caja` (`pago_evento`), y por el
--  mismo motivo: si la fila esta, el hecho esta; si el hecho esta, la fila esta. No
--  hay ninguna llamada de red dentro de la transaccion que escribe un permiso.
--
--  ----------------------------------------------------------------------------
--  POR QUE AQUI NO HAY `estado`, Y EN `catastro` SI
--  ----------------------------------------------------------------------------
--
--  `catastro_evento` lleva `estado`, `intentos`, `ultimo_error` y `entregado_en`
--  porque tiene UN consumidor: `rentas`. «Entregado» es entonces una sola cosa y
--  cabe en una columna.
--
--  Aqui hay CUATRO —`rentas`, `catastro`, `normativa` y `caja`—, y un solo estado
--  no puede decir «entregado a `caja` y todavia no a `rentas`». Escribirlo igual
--  tendria una de dos consecuencias, las dos malas: o se marca entregado cuando el
--  PRIMERO acusa —y los otros tres se quedan sin el evento, en verde— o cuando lo
--  hace el ULTIMO, y entonces un consumidor caido bloquea la entrega a los otros
--  tres. El acuse ES POR CONSUMIDOR y va en OTRA TABLA, en la etapa 3.
--
--  Consecuencia inmediata, y es la que decide los privilegios de mas abajo: esta
--  tabla es INMUTABLE. Se inserta y se lee. `kamayuk_app` recibe `INSERT, SELECT` y
--  NADA MAS: ni `UPDATE` —no hay ninguna columna que cambie despues de escribirse—
--  ni `DELETE` (regla 4, RNF-051). Un evento es la unica prueba de que lo que los
--  otros cuatro tienen salio de aqui.
--
--  ----------------------------------------------------------------------------
--  EL `evento_id` ES ALEATORIO, Y ESA ES LA DIFERENCIA CON `catastro`
--  ----------------------------------------------------------------------------
--
--  `catastro` lo DERIVA del contenido, y hace bien: publica PROYECCIONES —«asi esta
--  este predio hoy»— que se republican enteras, y con la identidad derivada del
--  contenido reproyectar el padron no produce ni un evento si nada cambio.
--
--  Aqui se publican HECHOS. Dos altas del mismo usuario no ocurren —la cuenta es
--  unica—, y dos fijaciones seguidas de la misma matriz con el mismo contenido SON
--  DOS ACTOS: cada uno con su observacion y su fila de auditoria, hechos por dos
--  personas que decidieron lo mismo. Con la identidad derivada del contenido, el
--  segundo se colapsaria en el primero: el buzon no escribiria nada, la
--  reconstruccion seguiria cuadrando, y el consumidor no sabria que alguien volvio a
--  tocarlo. El `UNIQUE` de abajo sigue siendo la idempotencia del RECEPTOR —un
--  evento entregado dos veces se deduplica por el— y no la del emisor.
--
--  ----------------------------------------------------------------------------
--  EL `CHECK` CRUZADO VA EN POSITIVO Y CON `ELSE false` (la leccion de `V10` de
--  `catastro`)
--  ----------------------------------------------------------------------------
--
--  `V5` de `catastro` los escribio en negativo —«`PREDIO_PROYECTADO` no lleva
--  ejercicio y TODO LO DEMAS si»—; con tres tipos eran correctos y con seis
--  resultaron FALSOS, obligando a un tipo nuevo a nombrar un predio que no es suyo.
--  Aqui se nace escrito en positivo: cada tipo dice que lleva, y un OCTAVO tipo que
--  alguien anada al `tipo_ck` sin decidir su forma NO ENTRA — falla ruidosamente en
--  su primer INSERT en vez de colarse con la forma del vecino.
--
--  Hoy los siete exigen lo mismo (`sujeto_id IS NOT NULL`), asi que la clausula NO
--  discrimina entre ellos y podria escribirse `sujeto_id IS NOT NULL` a secas. NO se
--  escribe asi, y ese es el punto: lo que esta comprobacion protege no es la forma
--  de los siete de hoy sino la del octavo. Un `NOT NULL` a secas lo aceptaria
--  calladamente; este `CASE` lo rechaza.
--
--  ----------------------------------------------------------------------------
--  POR QUE `sujeto_id` NO TIENE CLAVE FORANEA
--  ----------------------------------------------------------------------------
--
--  Porque apunta a DOS tablas segun el tipo: a `usuario` en los dos tipos de cuenta,
--  a `grupo` en los dos de grupo y en los dos de miembro, y a cualquiera de las dos
--  en `PERMISO_FIJADO` —cuyo cuerpo lleva `sujeto: GRUPO|USUARIO` para decir cual—.
--  Una foranea a una de las dos rechazaria la mitad de los eventos; dos columnas
--  nulables con su CHECK cruzado seria la forma de `permiso`, y aqui no aporta:
--  nadie navega de un evento a su fila, porque el evento se lee para ENTREGARLO y lo
--  que viaja es su cuerpo.
--
--  Y hay un motivo mas fuerte, que es el de todo outbox: el evento tiene que
--  sobrevivir a la fila. Aqui hoy nada se borra (regla 4), pero una foranea ataria
--  la historia de lo publicado al estado presente del padron, que es exactamente lo
--  que un registro de hechos no debe hacer.
-- ============================================================================

CREATE TABLE identidad_evento (
    municipalidad_id bigint       NOT NULL REFERENCES municipalidad (id),
    id               bigint       GENERATED ALWAYS AS IDENTITY,
    evento_id        uuid         NOT NULL,
    tipo             varchar(40)  NOT NULL,
    sujeto_id        bigint,
    cuerpo           jsonb        NOT NULL,
    huella           char(64)     NOT NULL,
    creado_en        timestamptz  NOT NULL,

    CONSTRAINT identidad_evento_pk PRIMARY KEY (municipalidad_id, id),

    -- La idempotencia del RECEPTOR: un evento entregado dos veces se deduplica por
    -- aqui. Es del MOTOR y no de un `if` —dos entregas simultaneas leerian las dos
    -- «no esta» y las dos aplicarian—.
    CONSTRAINT identidad_evento_uq UNIQUE (municipalidad_id, evento_id),

    CONSTRAINT identidad_evento_tipo_ck CHECK (tipo IN ('USUARIO_DADO_DE_ALTA',
                                                        'USUARIO_MODIFICADO',
                                                        'GRUPO_DADO_DE_ALTA',
                                                        'GRUPO_MODIFICADO',
                                                        'MIEMBRO_AFILIADO',
                                                        'MIEMBRO_DESAFILIADO',
                                                        'PERMISO_FIJADO')),

    -- El sujeto, tipo por tipo. Ver la cabecera: en POSITIVO y con `ELSE false`.
    CONSTRAINT identidad_evento_sujeto_ck CHECK (
        CASE tipo
            WHEN 'USUARIO_DADO_DE_ALTA' THEN sujeto_id IS NOT NULL
            WHEN 'USUARIO_MODIFICADO'   THEN sujeto_id IS NOT NULL
            WHEN 'GRUPO_DADO_DE_ALTA'   THEN sujeto_id IS NOT NULL
            WHEN 'GRUPO_MODIFICADO'     THEN sujeto_id IS NOT NULL
            WHEN 'MIEMBRO_AFILIADO'     THEN sujeto_id IS NOT NULL
            WHEN 'MIEMBRO_DESAFILIADO'  THEN sujeto_id IS NOT NULL
            WHEN 'PERMISO_FIJADO'       THEN sujeto_id IS NOT NULL
            ELSE false
        END),

    -- El cuerpo es un OBJETO, y no un arreglo ni un escalar. Sin esto, un `cuerpo`
    -- que fuera `null` de JSON —que es un jsonb valido— pasaria, y el consumidor
    -- recibiria un evento cuyo contenido no se puede aplicar a ninguna fila.
    CONSTRAINT identidad_evento_cuerpo_ck CHECK (jsonb_typeof(cuerpo) = 'object')
);

-- El que lee el publicador: todo, en el orden en que se emitio. NO es parcial —al
-- reves que el de `catastro`— porque aqui no hay `estado` que filtrar: lo que
-- decide que le falta a cada consumidor es su acuse, que vive en otra tabla (etapa
-- 3). Coincide con la clave primaria a proposito y se declara igual: el dia que el
-- acuse llegue, la consulta de «lo que le falta a este consumidor» sigue barriendo
-- por (municipalidad, id) y no se puede quedar sin indice por un descuido.
CREATE INDEX identidad_evento_secuencia_ix ON identidad_evento (municipalidad_id, id);

COMMENT ON TABLE identidad_evento IS
    'El buzon de salida de `identidad` (ADR-0028 §3, ADR-0039). Se escribe EN LA MISMA TRANSACCION '
    'que la fila que lo produjo: si la fila esta, el evento esta. Es INMUTABLE y no lleva `estado` '
    'porque tiene CUATRO consumidores y un solo estado no puede decir «entregado a caja y no a '
    'rentas»: el acuse es por consumidor y va en otra tabla (etapa 3).';
COMMENT ON COLUMN identidad_evento.evento_id IS
    'La identidad del hecho, ALEATORIA y no derivada del contenido, al reves que en `catastro`: '
    'aqui se publican hechos y no proyecciones, y dos fijaciones de la misma matriz son dos actos '
    'con dos observaciones. Ver la cabecera de V2.';
COMMENT ON COLUMN identidad_evento.sujeto_id IS
    'El usuario o el grupo del que habla el hecho. Sin clave foranea a proposito: apunta a dos '
    'tablas segun el tipo, y ademas un evento tiene que sobrevivir a la fila que lo produjo.';
COMMENT ON COLUMN identidad_evento.cuerpo IS
    'La fila ENTERA tal como quedo, no el delta. Un delta obliga al consumidor a no haber perdido '
    'ningun evento nunca; con la fila entera, uno que empieza de cero converge igual y uno que '
    'recibe el mismo evento dos veces escribe lo mismo dos veces.';
COMMENT ON COLUMN identidad_evento.huella IS
    'sha256 del cuerpo CANONICO, calculada al emitir. NO es el sha256 de esta columna: `jsonb` '
    'reordena las claves y descarta los espacios, asi que recalcularla sobre lo leido no '
    'coincidiria. El consumidor la copia y no la recalcula.';
COMMENT ON COLUMN identidad_evento.id IS
    'La SECUENCIA que viaja en el evento. Monotona por ser IDENTITY. NO sirve como cursor del '
    'consumidor —el id se asigna al INSERT y no al COMMIT, asi que una transaccion lenta queda por '
    'detras de un cursor que ya paso (lo midio V5 de `catastro`)—: ordena la entrega, no la marca.';

-- ----------------------------------------------------------------------------
--  RLS. Sin valor por omision: sin contexto de tenant, la consulta FALLA.
--  Y FORCE, porque sin el el DUENO de la tabla la omite (DAT-01 §0, hallazgo 1).
-- ----------------------------------------------------------------------------

ALTER TABLE identidad_evento ENABLE ROW LEVEL SECURITY;
ALTER TABLE identidad_evento FORCE ROW LEVEL SECURITY;
CREATE POLICY identidad_evento_tenant ON identidad_evento FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- ----------------------------------------------------------------------------
--  PRIVILEGIOS
--
--  `INSERT, SELECT` y nada mas. NINGUN `UPDATE`: no hay una sola columna que cambie
--  despues de escribirse —el acuse es de otra tabla (etapa 3)— y concederlo dejaria
--  abierta la unica forma de reescribir un hecho ya publicado. NINGUN `DELETE`,
--  como en las trece tablas de `V1` (regla 4, RNF-051).
--
--  Son DOS guardas independientes y basta una: el privilegio lo niega el motor, y el
--  escaner de `comun-verificaciones` lo niega en el build nombrando archivo y linea
--  —`identidad_evento` entra en `tablasProtegidas()` y en `tablasInmutables()`—.
--  Solo el escaner dice CUAL: el privilegio y la politica dan el mismo 42501 y el
--  sintoma no los distingue (#435).
-- ----------------------------------------------------------------------------

GRANT INSERT, SELECT ON identidad_evento TO kamayuk_app;
GRANT SELECT          ON identidad_evento TO kamayuk_readonly;
