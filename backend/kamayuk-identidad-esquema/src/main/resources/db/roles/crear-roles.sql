-- ============================================================================
--  IDENTIDAD — Roles de base de datos (ARQ-03 §4)
--
--  NO es una migracion de Flyway. Se ejecuta ANTES de la primera migracion, con
--  una conexion de superusuario, porque:
--    - las politicas RLS de `V1__baseline.sql` nombran roles y estos deben existir;
--    - kamayuk_owner necesita CREATE sobre el esquema para poder migrar;
--    - un rol no puede crearse a si mismo.
--
--  Idempotente: se puede volver a ejecutar sobre una base ya provisionada.
--
--  Las CLAVES NO ESTAN AQUI. Los roles se crean sin LOGIN; quien provisiona el
--  ambiente asigna la clave con `ALTER ROLE ... LOGIN PASSWORD ...` desde su
--  gestor de secretos. La prueba de aislamiento hace lo mismo con claves
--  generadas al vuelo.
--
--  NOSUPERUSER y NOBYPASSRLS son explicitos y no decorativos: un superusuario
--  omite RLS incluso con FORCE ROW LEVEL SECURITY (DAT-01 §0, hallazgo 1).
-- ============================================================================

DO $roles$
DECLARE
    r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['kamayuk_owner', 'kamayuk_app', 'kamayuk_readonly', 'rol_carga_parametros']
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('CREATE ROLE %I NOLOGIN', r);
        END IF;
        EXECUTE format(
            'ALTER ROLE %I NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOREPLICATION', r);
    END LOOP;
END
$roles$;

-- Solo kamayuk_owner hace DDL. La aplicacion nunca.
GRANT USAGE, CREATE ON SCHEMA public TO kamayuk_owner;
-- `rol_carga_parametros` no recibe USAGE aqui: sin CONNECT a esta base no puede llegar a
-- usar ningun esquema suyo, y darselo diria que se espera que lo use.
GRANT USAGE           ON SCHEMA public TO kamayuk_app, kamayuk_readonly;

-- Sin GRANT de pertenencia entre roles: kamayuk_owner concede privilegios sobre sus
-- propias tablas sin necesitarla, y ser miembro de kamayuk_app le permitiria un
-- SET ROLE que borra la separacion.

-- ---------- Extensiones: NINGUNA ----------
-- Este esquema no crea ni una, por el mismo motivo medido que `normativa` en C-13 y
-- `caja` en P5D: ninguna de las cuatro que el archivo del monolito declaraba tiene nada
-- que hacer aqui. Este sistema guarda quien puede hacer que, y sus trece tablas no
-- tienen una sola columna geografica, ni un indice GiST, ni un EXCLUDE, ni una busqueda
-- por aproximacion.
--
--   pg_trgm     la busqueda por aproximacion de nombre es del PADRON de contribuyentes
--               (RF-014), que es de `rentas`. Aqui la busqueda por cuenta es exacta.
--   unaccent    lo que la obligaria es una columna generada `nombre_normalizado`, y no
--               hay ninguna en este esquema.
--   postgis     la geometria del predio es de `catastro` (ADR-0021). Y es la mas cara de
--               las cuatro: NO es *trusted*, asi que obligaria a un superusuario y a la
--               imagen postgis/postgis para provisionar una base que no dibuja nada, y su
--               tabla `spatial_ref_sys` obligaria a una exencion en el
--               AislamientoMultiTenantTest de este modulo — o sea que una declaracion de
--               mas se propaga a la lista de excepciones de la barrera numero uno.
--   btree_gist  la exclusion de vigencias que no se pisan es de `catastro` (#669). Aqui
--               las vigencias de `usuario` y `grupo` son dos fechas con un CHECK, no un
--               rango con exclusion.
--
-- Lo vigila desde `infrastructure` `extensiones-de-las-migraciones.ts`, en las DOS
-- direcciones: el dia que una migracion de aqui necesite una, se pone rojo nombrando la
-- migracion y la extension, antes de que llegue a ningun motor.

-- ---------- CONNECT sobre esta base ----------
--  PostgreSQL concede `CONNECT` a PUBLIC al crear una base, asi que TODO rol del cluster puede
--  conectarse a la de cualquier sistema sin que nadie se lo haya dado. Se midio (C-7 §6): sobre
--  una base recien creada, `has_database_privilege('<un rol cualquiera>', '<esa base>', 'CONNECT')`
--  devuelve `true`; tras el `REVOKE ... FROM PUBLIC`, `false`.
--
--  Los roles son del CLUSTER y los cuatro sistemas lo comparten, de modo que sin esto la
--  credencial de carga de valores normativos —y la de la aplicacion de cualquier otro sistema—
--  puede abrir una sesion contra esta base. No veria filas —RLS esta forzada— pero seria una
--  credencial de mas apuntando a un padron, que es exactamente lo que #155 midio con el rol del
--  respaldo y lo que `30-base-de-keycloak.sh` ya hace con la base del monolito.
--
--  `rol_carga_parametros` NO esta, y esa es la diferencia con `normativa`. Ese rol es la unica
--  credencial que puede escribir un valor normativo (ADR-0007 §5) y sus politicas de escritura
--  viven en el `V1` de `normativa`; aqui no hay ni un valor normativo que cargar. El rol SI se
--  crea —el bloque de arriba lo crea porque los roles son del CLUSTER y los cinco sistemas
--  comparten los mismos cuatro nombres: un `crear-roles.sql` que creara tres y otro cuatro
--  dejaria al cluster distinto segun que sistema se provisionara primero—, pero sin CONNECT a
--  esta base no puede abrir una sesion contra ella. Eso es lo que C-7 §6 midio y lo que hace que
--  «tiene credencial» y «puede llegar al padron» dejen de ser lo mismo.
--
--  Va aqui y no en una migracion porque `REVOKE ... ON DATABASE` solo lo puede hacer quien la
--  posee, y `kamayuk_owner` —que es quien migra— a proposito NO es dueno de la base (#722 lo midio:
--  «permission denied for database»). Este guion corre como superusuario.
DO $connect$
DECLARE
    base text := current_database();
BEGIN
    EXECUTE format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', base);
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO kamayuk_owner, kamayuk_app, kamayuk_readonly', base);
END
$connect$;
