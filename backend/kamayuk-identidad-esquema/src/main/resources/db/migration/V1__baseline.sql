-- ============================================================================
--  IDENTIDAD — V1__baseline.sql
--
--  El esquema del QUINTO sistema: quien puede hacer que, en que municipalidad
--  (ADR-0039). 13 tablas, y ninguna es de negocio tributario.
--
--  Que es este sistema. D-19 preguntaba quien es el dueño de la autorizacion, y se
--  contesto el 2026-09-09: es un SISTEMA PROPIO, y la copia local que cada uno de los
--  otros cuatro consulta para autorizar se replica por el buzon. Lo que este baseline
--  crea son las OCHO tablas de esa decision —`municipalidad`, `usuario`, `grupo`,
--  `miembro`, `modulo_sistema`, `acceso`, `permiso` y `sesion`—, mas las cinco
--  transversales que ADR-0032 replica en los cinco baselines: `auditoria` con sus dos
--  particiones, `documento_emitido` y `respaldo`.
--
--  ESTA ES LA ETAPA 1 (`infrastructure#52`), y por eso NO hay aqui ni una escritura de
--  administracion: `kamayuk-identidad-nucleo` nace vacio y las once escrituras llegan en
--  la etapa 2, con las pantallas que hoy viven en `rentas` (ADR-0030 §3). El esquema si
--  nace entero, y ese orden es deliberado: una tabla que llega despues de su codigo
--  llega sin RLS y sin privilegios por columna, y eso no se ve hasta que se parte la base.
--
--  EL DDL DE LAS TRECE ES EL DE LOS OTROS CUATRO BASELINES, LITERAL. No se rehizo ni se
--  «mejoro»: son las mismas tablas del mismo modelo del manual, y dos copias que
--  divergen producen un sistema donde el mismo usuario puede una cosa en una pantalla y
--  no en la de al lado — el sintoma, un 403 en un sitio y no en otro, no se parece a su
--  causa (lo dice `ComprobadorDeAccesoJdbc` desde C-7 y aqui vale igual).
--
--  ----------------------------------------------------------------------------
--  LA UNICA DESVIACION, Y SU MOTIVO
--  ----------------------------------------------------------------------------
--
--  `modulo_sistema` y `acceso` ganan una columna `sistema varchar(20) NOT NULL`, y su
--  UNIQUE pasa de `(municipalidad_id, codigo)` a `(municipalidad_id, sistema, codigo)`.
--
--  En los otros cuatro cada base guarda SU catalogo: `rentas` siembra el suyo, `caja` el
--  suyo, y un codigo identifica una opcion sin ambiguedad. Aqui no: esta base guarda a
--  quien se concede cada opcion de CUATRO catalogos —los cinco sistemas menos las que no
--  publica ninguno— y dos sistemas pueden nombrar igual dos opciones distintas. Sin la
--  columna, sembrar el catalogo del segundo sistema chocaria con el UNIQUE del primero y
--  el `ON CONFLICT ... DO NOTHING` del sembrador lo dejaria pasar EN SILENCIO: la opcion
--  del segundo sistema no se crearia, nadie podria darle permiso, y el sintoma —una
--  pantalla a la que no se le puede dar acceso— es exactamente el que RF-122 existe para
--  impedir.
--
--  El `CHECK (sistema IN (...))` nombra los cinco uno a uno y no admite cualquier texto:
--  una fila con un sistema que no existe es un permiso que nadie va a consultar nunca, y
--  no hay forma de verlo mirando pantallas.
--
--  LO QUE LA COLUMNA NO ARREGLA, y se dice aqui en vez de descubrirse en la etapa 2:
--  `ComprobadorDeAccesoJdbc` empareja el acceso por `a.codigo = :acceso` y su puerto
--  —`ComprobadorDeAcceso.autoriza(usuario, acceso, privilegio, fecha)`— no lleva el
--  sistema. Mientras esta base tenga un solo catalogo sembrado (la etapa 1 siembra el de
--  `identidad` y nada mas) hay exactamente una fila por codigo y la consulta es correcta;
--  el dia que entren los cinco, dos codigos iguales de sistemas distintos hacen que ese
--  `single()` REVIENTE. Quien decide de que sistema es el acceso que se comprueba es la
--  etapa 2, y hasta entonces esto es un hecho declarado y no un defecto escondido.
--
--  ----------------------------------------------------------------------------
--
--  ESTO ES UNA MIGRACION DE FLYWAY, NO UN `esquema.sql` SUELTO, y el motivo NO
--  es la migracion de datos (ADR-0032 §2). Son tres, y ninguna es hipotetica:
--    - el CHECKSUM sobre DDL ya aplicado. El modo de fallo real no es «falta una
--      migracion»: es que alguien edite una que ya corrio en su maquina y la
--      base de al lado quede distinta sin que nada se ponga rojo;
--    - el Job de implantacion ESPERA al de migracion consultando
--      `flyway_schema_history`, porque en Kubernetes no hay equivalente
--      de `service_completed_successfully`;
--    - las pruebas de persistencia corren las migraciones reales contra un motor
--      real, y sin version no se puede decir contra QUE esquema pasaron.
--
--  NO SE COPIA NINGUNA MIGRACION DE `sgtm`. Sus `V1..V78` estan entrelazadas a
--  proposito -`V1` crea nucleo y catastro juntos, `V6` aplica RLS a todo el
--  esquema de una vez, `V7` reparte los privilegios de TODOS los roles- y no hay
--  reparto posible. La historia se queda en `sgtm`, que no se borra, y ahi sigue
--  contestando por que una columna es como es.
--
--  A PARTIR DE AQUI, CADA CAMBIO ES UNA MIGRACION NUEVA DE ESTE REPOSITORIO:
--  `V2`, `V3`, y asi. En cuanto haya una base en `stg` que alguien no quiera
--  rehacer, este archivo deja de poder editarse.
--
--  ANTES DE ESTE ARCHIVO hay que haber corrido `crear-roles.sql`: los roles se
--  provisionan con una conexion de superusuario, porque las politicas de §6
--  NOMBRAN roles que deben existir, y `kamayuk_owner` no puede crearse a si mismo.

--  ----------------------------------------------------------------------------
--  LOS CINCO HALLAZGOS DE RLS (DAT-01 §0), VERIFICADOS EJECUTANDO
--  ----------------------------------------------------------------------------
--
--  Van en el encabezado de los cinco baselines a proposito: los cinco sistemas
--  van a tropezar con ellos, y en un repositorio nuevo NO HAY `git log` donde
--  encontrarlos. Los dos primeros se heredaron verificados del SRTM; los otros
--  tres salieron en `sgtm`, midiendo planes y migraciones.
--
--  1. UN SUPERUSUARIO OMITE RLS.
--     `FORCE ROW LEVEL SECURITY` protege del PROPIETARIO de la tabla, no del
--     SUPERUSUARIO. Consecuencias, todas obligatorias: el rol de aplicacion se
--     crea `NOSUPERUSER NOBYPASSRLS`; la aplicacion no se conecta como
--     propietario; y una prueba de aislamiento escrita sobre la conexion por
--     omision de Testcontainers -que es de superusuario- PASA EN VERDE SIN
--     VERIFICAR NADA.
--
--  2. UNA PARTICION NO HEREDA LA POLITICA DEL PADRE.
--     Consultar una particion directamente evade la politica de su padre. Dos
--     mitigaciones, y la segunda es la que cierra el hueco: RLS explicita en
--     cada particion (§6 de este archivo), y LA APLICACION NO TIENE NINGUN
--     PRIVILEGIO SOBRE NINGUNA PARTICION. Por eso aqui no hay
--     `GRANT ... ON ALL TABLES IN SCHEMA`: una particion nueva no recibe
--     privilegios salvo que alguien se los conceda, y eso se ve en el diff.
--
--  3. BAJO RLS, UN `LIKE 'prefijo%'` NO LLEGA NUNCA AL INDICE.
--     `textlike` no es *leakproof*, asi que PostgreSQL no lo evalua antes de la
--     politica y la condicion se queda en el `Filter`. Toda busqueda por prefijo
--     se escribe como RANGO, con `~>=~` / `~<~` y un indice `text_pattern_ops`.
--     Y una funcion no leakproof envolviendo la columna -`lower`, `unaccent`-
--     tampoco llega: por eso hay columnas GENERADAS de busqueda.
--
--  4. UNA CLAVE FORANEA NUEVA SOBRE UNA TABLA CON RLS NO SE PUEDE VALIDAR.
--     Validar lanza una consulta, la consulta queda sujeta a la politica, y el
--     migrador corre sin contexto de tenant -correctamente: migrar no es atender
--     a ninguna municipalidad-. La migracion entera muere con
--     `unrecognized configuration parameter "app.municipalidad_id"`. Por eso hay
--     restricciones `NOT VALID`, que SIGUEN comprobando cada INSERT y cada
--     UPDATE. Medido aparte: un `CHECK` validado SI pasa -su escaneo no
--     atraviesa la politica-, asi que un `NOT VALID` sobre un CHECK es por
--     DATOS, no por RLS.
--
--  5. BAJO RLS, EL OPERADOR ESPACIAL TAMPOCO LLEGA AL INDICE.
--     Es el hallazgo 3 con otro operador: `geography_overlaps` tampoco es
--     *leakproof*. Y el sintoma engana mas, porque EL PLAN SIGUE DICIENDO
--     "Index": usa uno por la condicion de la propia politica y lee la tabla
--     entera del inquilino. Por eso el marco del lote se escribe con `<=` / `>=`
--     sobre cuatro columnas GENERADAS en `double precision` -y no `numeric`,
--     porque `numeric_le` tampoco es leakproof-. Aqui no hay ni una columna
--     espacial, y el hallazgo se conserva por lo mismo que los otros cuatro: la
--     primera migracion que traiga geometria no lo tiene que volver a descubrir.
-- ============================================================================

-- ==========================================================================
--  1. DOMINIOS DE TIPO
--  Uno solo, y no los siete de los otros baselines: los seis que faltan
--  -`alicuota`, `area_m2`, `cod_catastral`, `dinero`, `monto_calc` y
--  `porcentaje`- son tipos de una CIFRA TRIBUTARIA, y aqui no se calcula
--  ninguna. Un dominio declarado que ninguna columna usa no es inocuo: dice
--  que este esquema guarda importes, y el primero que lo lea lo creera.
-- ==========================================================================

CREATE DOMAIN ejercicio AS smallint
    CONSTRAINT ejercicio_check CHECK (((VALUE >= 1990) AND (VALUE <= 2100)));

-- ==========================================================================
--  2. FUNCIONES
--  Una sola, y es de disparador: un disparador sin su funcion no protege
--  nada, y una funcion sin su disparador es codigo muerto que ademas MIENTE
--  sobre lo que este esquema contiene.
--
--  Las otras tres de `normativa` -`conjunto_sellado_es_inmutable`,
--  `detalle_de_conjunto_sellado_es_inmutable` y
--  `valuacion_de_publicacion_sellada_es_inmutable`- cuelgan de tablas que no
--  estan aqui, asi que no viajan.
-- ==========================================================================

CREATE OR REPLACE FUNCTION public.documento_solo_cuenta_reimpresiones()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.tipo IS DISTINCT FROM OLD.tipo
       OR NEW.numero IS DISTINCT FROM OLD.numero
       OR NEW.ejercicio IS DISTINCT FROM OLD.ejercicio
       OR NEW.referencia IS DISTINCT FROM OLD.referencia
       OR NEW.datos IS DISTINCT FROM OLD.datos
       OR NEW.formato IS DISTINCT FROM OLD.formato
       OR NEW.resumen IS DISTINCT FROM OLD.resumen
       OR NEW.fecha_emision IS DISTINCT FROM OLD.fecha_emision
    THEN
        RAISE EXCEPTION
          'Un documento emitido no se edita: lo unico que cambia es cuantas veces se reimprimio. '
          'Si los datos estaban mal, se emite otro y se anula este';
    END IF;
    RETURN NEW;
END
$function$
;

-- ==========================================================================
--  3. TABLAS
--  Las particionadas van antes que sus particiones.
-- ==========================================================================

CREATE TABLE acceso (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    modulo_id bigint NOT NULL,
    sistema character varying(20) NOT NULL,
    tipo character varying(12) NOT NULL,
    codigo character varying(60) NOT NULL,
    nombre character varying(160) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE auditoria (
    municipalidad_id bigint NOT NULL,
    ejercicio ejercicio NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    tabla character varying(60) NOT NULL,
    clave character varying(120) NOT NULL,
    operacion character varying(15) NOT NULL,
    usuario_id character varying(60) NOT NULL,
    origen_equipo character varying(80),
    origen_ip inet,
    fecha timestamp with time zone DEFAULT now() NOT NULL,
    observacion character varying(1000) NOT NULL,
    datos_anteriores jsonb,
    datos_nuevos jsonb
) PARTITION BY LIST (ejercicio);

CREATE TABLE documento_emitido (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    tipo character varying(40) NOT NULL,
    numero character varying(40) NOT NULL,
    ejercicio ejercicio NOT NULL,
    referencia character varying(80) NOT NULL,
    datos jsonb NOT NULL,
    formato character varying(10) NOT NULL,
    resumen character(64) NOT NULL,
    fecha_emision date NOT NULL,
    reimpresiones integer DEFAULT 0 NOT NULL,
    usuario_emision character varying(60) NOT NULL,
    observacion character varying(500) NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE grupo (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    nombre character varying(80) NOT NULL,
    descripcion character varying(300),
    habilitado boolean DEFAULT true NOT NULL,
    vigencia_desde date,
    vigencia_hasta date
);

CREATE TABLE miembro (
    municipalidad_id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    fecha_alta timestamp with time zone DEFAULT now() NOT NULL,
    usuario_alta character varying(60) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    fecha_baja timestamp with time zone,
    usuario_baja character varying(60)
);

CREATE TABLE modulo_sistema (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    sistema character varying(20) NOT NULL,
    codigo character varying(30) NOT NULL,
    nombre character varying(120) NOT NULL,
    orden smallint DEFAULT 0 NOT NULL,
    activo boolean DEFAULT true NOT NULL
);

CREATE TABLE municipalidad (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    ubigeo character(6) NOT NULL,
    nombre character varying(160) NOT NULL,
    tipo character varying(20) NOT NULL,
    activa boolean DEFAULT true NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    es_demostracion boolean DEFAULT false NOT NULL
);

CREATE TABLE permiso (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    acceso_id bigint NOT NULL,
    grupo_id bigint,
    usuario_id bigint,
    ejecucion boolean DEFAULT false NOT NULL,
    lectura boolean DEFAULT false NOT NULL,
    registro boolean DEFAULT false NOT NULL,
    modificacion boolean DEFAULT false NOT NULL,
    eliminacion boolean DEFAULT false NOT NULL,
    impresion boolean DEFAULT false NOT NULL,
    especial boolean DEFAULT false NOT NULL,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL,
    usuario_registro character varying(60) NOT NULL
);

CREATE TABLE respaldo (
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    inicio timestamp with time zone NOT NULL,
    fin timestamp with time zone,
    resultado character varying(12) NOT NULL,
    destino character varying(200) NOT NULL,
    tamano_bytes bigint,
    detalle character varying(500),
    ultima_restauracion_verificada timestamp with time zone,
    ultima_restauracion_verificada_por character varying(200)
);

CREATE TABLE sesion (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    usuario_id bigint NOT NULL,
    inicio timestamp with time zone DEFAULT now() NOT NULL,
    fin timestamp with time zone,
    origen_equipo character varying(80),
    origen_ip inet,
    agente character varying(200),
    ejercicio_trabajo ejercicio
);

CREATE TABLE usuario (
    municipalidad_id bigint NOT NULL,
    id bigint GENERATED ALWAYS AS IDENTITY NOT NULL,
    cuenta character varying(60) NOT NULL,
    sujeto_oidc character varying(120),
    nombre character varying(160) NOT NULL,
    correo character varying(160),
    habilitado boolean DEFAULT true NOT NULL,
    vigencia_desde date,
    vigencia_hasta date,
    fecha_registro timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE auditoria_2026 PARTITION OF auditoria FOR VALUES IN ('2026');
CREATE TABLE auditoria_2027 PARTITION OF auditoria FOR VALUES IN ('2027');

-- ==========================================================================
--  4. RESTRICCIONES
--  Primero las de tabla, despues las foraneas: una foranea no se puede crear
--  contra una tabla que todavia no tiene su clave primaria.
-- ==========================================================================

ALTER TABLE acceso ADD CONSTRAINT acceso_codigo_uq UNIQUE (municipalidad_id, sistema, codigo);
ALTER TABLE acceso ADD CONSTRAINT acceso_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE acceso ADD CONSTRAINT acceso_sistema_check CHECK (((sistema)::text = ANY ((ARRAY['rentas'::character varying, 'catastro'::character varying, 'normativa'::character varying, 'caja'::character varying, 'identidad'::character varying])::text[])));
ALTER TABLE acceso ADD CONSTRAINT acceso_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['OPCION_MENU'::character varying, 'POLITICA'::character varying])::text[])));
ALTER TABLE auditoria ADD CONSTRAINT auditoria_observacion_ck CHECK ((length(btrim((observacion)::text)) >= 5));
ALTER TABLE auditoria ADD CONSTRAINT auditoria_operacion_check CHECK (((operacion)::text = ANY ((ARRAY['ALTA'::character varying, 'MODIFICACION'::character varying, 'BAJA'::character varying, 'ANULACION'::character varying, 'REVERSION'::character varying, 'PERMISO'::character varying, 'ACCESO'::character varying])::text[])));
ALTER TABLE auditoria ADD CONSTRAINT auditoria_pk PRIMARY KEY (municipalidad_id, ejercicio, id);
ALTER TABLE documento_emitido ADD CONSTRAINT documento_emitido_formato_check CHECK (((formato)::text = ANY ((ARRAY['PDF'::character varying, 'XLS'::character varying, 'RTF'::character varying])::text[])));
ALTER TABLE documento_emitido ADD CONSTRAINT documento_emitido_reimpresiones_check CHECK ((reimpresiones >= 0));
ALTER TABLE documento_emitido ADD CONSTRAINT documento_numero_uq UNIQUE (municipalidad_id, tipo, ejercicio, numero);
ALTER TABLE documento_emitido ADD CONSTRAINT documento_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE grupo ADD CONSTRAINT grupo_nombre_uq UNIQUE (municipalidad_id, nombre);
ALTER TABLE grupo ADD CONSTRAINT grupo_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE grupo ADD CONSTRAINT grupo_vigencia_ck CHECK (((vigencia_hasta IS NULL) OR (vigencia_desde IS NULL) OR (vigencia_hasta >= vigencia_desde)));
ALTER TABLE miembro ADD CONSTRAINT miembro_pk PRIMARY KEY (municipalidad_id, grupo_id, usuario_id);
ALTER TABLE modulo_sistema ADD CONSTRAINT modulo_codigo_uq UNIQUE (municipalidad_id, sistema, codigo);
ALTER TABLE modulo_sistema ADD CONSTRAINT modulo_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE modulo_sistema ADD CONSTRAINT modulo_sistema_sistema_check CHECK (((sistema)::text = ANY ((ARRAY['rentas'::character varying, 'catastro'::character varying, 'normativa'::character varying, 'caja'::character varying, 'identidad'::character varying])::text[])));
ALTER TABLE municipalidad ADD CONSTRAINT municipalidad_pkey PRIMARY KEY (id);
ALTER TABLE municipalidad ADD CONSTRAINT municipalidad_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['DISTRITAL'::character varying, 'PROVINCIAL'::character varying])::text[])));
ALTER TABLE municipalidad ADD CONSTRAINT municipalidad_ubigeo_key UNIQUE (ubigeo);
ALTER TABLE permiso ADD CONSTRAINT permiso_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE permiso ADD CONSTRAINT permiso_sujeto_ck CHECK ((((grupo_id IS NOT NULL) AND (usuario_id IS NULL)) OR ((grupo_id IS NULL) AND (usuario_id IS NOT NULL))));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_fechas_ck CHECK (((fin IS NULL) OR (fin >= inicio)));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_pkey PRIMARY KEY (id);
ALTER TABLE respaldo ADD CONSTRAINT respaldo_resultado_check CHECK (((resultado)::text = ANY ((ARRAY['EN_CURSO'::character varying, 'EXITOSO'::character varying, 'FALLIDO'::character varying])::text[])));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_tamano_bytes_check CHECK (((tamano_bytes IS NULL) OR (tamano_bytes >= 0)));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_terminado_ck CHECK ((((resultado)::text = 'EN_CURSO'::text) OR (fin IS NOT NULL)));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_verificacion_completa_ck CHECK (((ultima_restauracion_verificada IS NULL) = (ultima_restauracion_verificada_por IS NULL)));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_verificacion_exitosa_ck CHECK (((ultima_restauracion_verificada IS NULL) OR ((resultado)::text = 'EXITOSO'::text)));
ALTER TABLE respaldo ADD CONSTRAINT respaldo_verificacion_posterior_ck CHECK (((ultima_restauracion_verificada IS NULL) OR ((fin IS NOT NULL) AND (ultima_restauracion_verificada >= fin))));
ALTER TABLE sesion ADD CONSTRAINT sesion_fechas_ck CHECK (((fin IS NULL) OR (fin >= inicio)));
ALTER TABLE sesion ADD CONSTRAINT sesion_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE usuario ADD CONSTRAINT usuario_cuenta_uq UNIQUE (municipalidad_id, cuenta);
ALTER TABLE usuario ADD CONSTRAINT usuario_pk PRIMARY KEY (municipalidad_id, id);
ALTER TABLE usuario ADD CONSTRAINT usuario_sujeto_uq UNIQUE (municipalidad_id, sujeto_oidc);
ALTER TABLE usuario ADD CONSTRAINT usuario_vigencia_ck CHECK (((vigencia_hasta IS NULL) OR (vigencia_desde IS NULL) OR (vigencia_hasta >= vigencia_desde)));

ALTER TABLE acceso ADD CONSTRAINT acceso_modulo_fk FOREIGN KEY (municipalidad_id, modulo_id) REFERENCES modulo_sistema(municipalidad_id, id);
ALTER TABLE documento_emitido ADD CONSTRAINT documento_emitido_municipalidad_id_fkey FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE grupo ADD CONSTRAINT grupo_municipalidad_id_fkey FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE miembro ADD CONSTRAINT miembro_grupo_fk FOREIGN KEY (municipalidad_id, grupo_id) REFERENCES grupo(municipalidad_id, id);
ALTER TABLE miembro ADD CONSTRAINT miembro_usuario_fk FOREIGN KEY (municipalidad_id, usuario_id) REFERENCES usuario(municipalidad_id, id);
ALTER TABLE modulo_sistema ADD CONSTRAINT modulo_sistema_municipalidad_id_fkey FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);
ALTER TABLE permiso ADD CONSTRAINT permiso_acceso_fk FOREIGN KEY (municipalidad_id, acceso_id) REFERENCES acceso(municipalidad_id, id);
ALTER TABLE permiso ADD CONSTRAINT permiso_grupo_fk FOREIGN KEY (municipalidad_id, grupo_id) REFERENCES grupo(municipalidad_id, id);
ALTER TABLE permiso ADD CONSTRAINT permiso_usuario_fk FOREIGN KEY (municipalidad_id, usuario_id) REFERENCES usuario(municipalidad_id, id);
ALTER TABLE sesion ADD CONSTRAINT sesion_usuario_fk FOREIGN KEY (municipalidad_id, usuario_id) REFERENCES usuario(municipalidad_id, id);
ALTER TABLE usuario ADD CONSTRAINT usuario_municipalidad_id_fkey FOREIGN KEY (municipalidad_id) REFERENCES municipalidad(id);

-- ==========================================================================
--  5. INDICES
--  Los que sostienen una consulta que este sistema hace de verdad, no los
--  que «podrian hacer falta»: un indice que nadie usa cuesta escritura y
--  espacio y promete una busqueda que no ocurre (C-12).
-- ==========================================================================

CREATE INDEX acceso_modulo_ix ON public.acceso USING btree (municipalidad_id, modulo_id, tipo);
CREATE INDEX auditoria_tabla_ix ON public.auditoria USING btree (municipalidad_id, tabla, clave);
CREATE INDEX auditoria_usuario_ix ON public.auditoria USING btree (municipalidad_id, usuario_id, fecha);
CREATE INDEX documento_referencia_ix ON public.documento_emitido USING btree (municipalidad_id, tipo, referencia);
CREATE INDEX miembro_usuario_ix ON public.miembro USING btree (municipalidad_id, usuario_id);
CREATE INDEX permiso_acceso_ix ON public.permiso USING btree (municipalidad_id, acceso_id);
CREATE UNIQUE INDEX permiso_grupo_uq ON public.permiso USING btree (municipalidad_id, acceso_id, grupo_id) WHERE (grupo_id IS NOT NULL);
CREATE UNIQUE INDEX permiso_usuario_uq ON public.permiso USING btree (municipalidad_id, acceso_id, usuario_id) WHERE (usuario_id IS NOT NULL);
CREATE INDEX respaldo_inicio_ix ON public.respaldo USING btree (inicio DESC);
CREATE INDEX sesion_abierta_ix ON public.sesion USING btree (municipalidad_id, usuario_id) WHERE (fin IS NULL);

-- ==========================================================================
--  6. ROW LEVEL SECURITY
--  Sin valor por omision: sin contexto de tenant, la consulta FALLA. Y
--  FORCE, porque sin el el DUENO de la tabla la omite. Cada particion repite
--  su bloque: una particion NO HEREDA la politica de su padre (DAT-01 §0,
--  hallazgo 2).
-- ==========================================================================

ALTER TABLE acceso ENABLE ROW LEVEL SECURITY;
ALTER TABLE acceso FORCE ROW LEVEL SECURITY;
CREATE POLICY acceso_tenant ON acceso FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;
CREATE POLICY auditoria_tenant ON auditoria FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE auditoria_2026 ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_2026 FORCE ROW LEVEL SECURITY;
CREATE POLICY auditoria_2026_tenant ON auditoria_2026 FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE auditoria_2027 ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_2027 FORCE ROW LEVEL SECURITY;
CREATE POLICY auditoria_2027_tenant ON auditoria_2027 FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE documento_emitido ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento_emitido FORCE ROW LEVEL SECURITY;
CREATE POLICY documento_por_tenant ON documento_emitido FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE grupo ENABLE ROW LEVEL SECURITY;
ALTER TABLE grupo FORCE ROW LEVEL SECURITY;
CREATE POLICY grupo_tenant ON grupo FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE miembro ENABLE ROW LEVEL SECURITY;
ALTER TABLE miembro FORCE ROW LEVEL SECURITY;
CREATE POLICY miembro_tenant ON miembro FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE modulo_sistema ENABLE ROW LEVEL SECURITY;
ALTER TABLE modulo_sistema FORCE ROW LEVEL SECURITY;
CREATE POLICY modulo_sistema_tenant ON modulo_sistema FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE municipalidad ENABLE ROW LEVEL SECURITY;
ALTER TABLE municipalidad FORCE ROW LEVEL SECURITY;
CREATE POLICY municipalidad_escritura ON municipalidad FOR ALL TO kamayuk_owner
    USING (true)
    WITH CHECK (true);
CREATE POLICY municipalidad_lectura ON municipalidad FOR SELECT TO PUBLIC
    USING (true);
ALTER TABLE permiso ENABLE ROW LEVEL SECURITY;
ALTER TABLE permiso FORCE ROW LEVEL SECURITY;
CREATE POLICY permiso_tenant ON permiso FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE respaldo ENABLE ROW LEVEL SECURITY;
ALTER TABLE respaldo FORCE ROW LEVEL SECURITY;
CREATE POLICY respaldo_escritura ON respaldo FOR ALL TO kamayuk_owner
    USING (true)
    WITH CHECK (true);
CREATE POLICY respaldo_lectura ON respaldo FOR SELECT TO PUBLIC
    USING (true);
ALTER TABLE sesion ENABLE ROW LEVEL SECURITY;
ALTER TABLE sesion FORCE ROW LEVEL SECURITY;
CREATE POLICY sesion_tenant ON sesion FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;
CREATE POLICY usuario_tenant ON usuario FOR ALL TO PUBLIC
    USING ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint))
    WITH CHECK ((municipalidad_id = (current_setting('app.municipalidad_id'::text))::bigint));

-- ==========================================================================
--  7. PRIVILEGIOS
--  El rol de la aplicacion NO es dueno ni superusuario, y NO recibe
--  privilegios sobre las particiones: se los da el padre.
--
--  NINGUN GRANT A `rol_carga_parametros`, y no es un olvido: esa credencial
--  es la unica que puede escribir un valor normativo (ADR-0007 §5) y aqui no
--  hay ninguno. `crear-roles.sql` tampoco le da CONNECT a esta base, asi que
--  un GRANT aqui seria un privilegio sobre una base a la que ese rol no
--  puede ni abrir sesion — o sea, ruido que alguien tendria que interpretar.
--
--  Y NINGUN `DELETE`, en ninguna tabla y para ningun rol (RNF-051, regla 4).
-- ==========================================================================

GRANT INSERT, SELECT, UPDATE ON acceso TO kamayuk_app;
GRANT SELECT ON acceso TO kamayuk_readonly;
GRANT INSERT, SELECT ON auditoria TO kamayuk_app;
GRANT SELECT ON auditoria TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON documento_emitido TO kamayuk_app;
GRANT SELECT ON documento_emitido TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON grupo TO kamayuk_app;
GRANT SELECT ON grupo TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON miembro TO kamayuk_app;
GRANT SELECT ON miembro TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON modulo_sistema TO kamayuk_app;
GRANT SELECT ON modulo_sistema TO kamayuk_readonly;
GRANT SELECT ON municipalidad TO kamayuk_app;
GRANT SELECT ON municipalidad TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON permiso TO kamayuk_app;
GRANT SELECT ON permiso TO kamayuk_readonly;
GRANT SELECT ON respaldo TO kamayuk_app;
GRANT SELECT ON respaldo TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON sesion TO kamayuk_app;
GRANT SELECT ON sesion TO kamayuk_readonly;
GRANT INSERT, SELECT, UPDATE ON usuario TO kamayuk_app;
GRANT SELECT ON usuario TO kamayuk_readonly;

-- ==========================================================================
--  8. DISPARADORES DE INMUTABILIDAD
--  Con su funcion. Un disparador sin su funcion no protege nada.
-- ==========================================================================

CREATE TRIGGER documento_inmutable_trg BEFORE UPDATE ON public.documento_emitido FOR EACH ROW EXECUTE FUNCTION documento_solo_cuenta_reimpresiones();

-- ==========================================================================
--  9. COMENTARIOS
--  El por que de una columna, que es lo primero que se pierde.
-- ==========================================================================

COMMENT ON TABLE documento_emitido IS 'Documentos emitidos con los datos que los generaron, para reimprimirlos identicos (RF-132).';
COMMENT ON TABLE municipalidad IS 'Registro de tenants. No es tabla de tenant: la aplicacion la lee entera porque los procesos masivos iteran municipalidad por municipalidad. Solo kamayuk_owner escribe.';
COMMENT ON COLUMN municipalidad.es_demostracion IS 'Instalacion de demostracion: todo documento emitido bajo este tenant sale marcado, en los tres formatos. Lo lee la capa de documentos, no cada emisor. Solo kamayuk_owner la escribe, como el alta de la municipalidad.';
COMMENT ON COLUMN modulo_sistema.sistema IS 'De que sistema es este modulo del menu. Es la desviacion de este baseline: `identidad` guarda los catalogos de los cinco, y dos sistemas pueden nombrar igual dos modulos distintos. Entra en modulo_codigo_uq por eso.';
COMMENT ON COLUMN acceso.sistema IS 'De que sistema es esta opcion. Mismo motivo que modulo_sistema.sistema, y con el mismo coste declarado: ComprobadorDeAccesoJdbc empareja por codigo y no lleva el sistema, asi que mientras haya mas de un catalogo sembrado esa consulta hay que acotarla (etapa 2).';
COMMENT ON TABLE respaldo IS 'Estado de las copias de seguridad (RF-126). La aplicacion solo lee: quien hace la copia y escribe aqui es el proceso de despliegue, como kamayuk_owner.';
COMMENT ON COLUMN respaldo.ultima_restauracion_verificada IS 'Instante en que se comprobo, restaurandola de verdad, que esta copia se puede restaurar (RNF-079). NULO significa «nunca se probo», nunca «hoy».';
COMMENT ON COLUMN respaldo.ultima_restauracion_verificada_por IS 'Que proceso lo comprobo: el simulacro de restauracion y el ambiente contra el que corrio. No es un usuario de la aplicacion: la aplicacion no restaura.';
