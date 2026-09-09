# Backend de `identidad`

Spring Boot 4 sobre Java 25, multi-modulo, monolito modular con Spring Modulith
(ADR-0001, ADR-0003). Es el quinto sistema de Kamayuk: el dueño de usuarios, grupos, accesos y
permisos (ADR-0039, que contesta la mitad de D-19 sobre quien es el dueño).

**Que hay, y es la etapa 1 de [`infrastructure#52`](https://github.com/hneyra/infrastructure/issues/52):**
el esquema completo como una migracion Flyway (`V1__baseline.sql`, 13 tablas), el camino del
contexto de tenant (token → `SET LOCAL` → RLS), la copia local que autoriza, la implantacion de la
municipalidad, y las **cinco barreras** de `comun-verificaciones` corriendo. El repositorio existe,
verifica y despliega.

**Que NO hay, y hay que saberlo antes de buscarlo:** ni una regla de negocio.
`kamayuk-identidad-nucleo` nace **vacio** —solo su `package-info.java`—, no hay ningun controlador
y por tanto no hay contrato de API ni `docs/50-api/`. Las **once escrituras de administracion**
—altas y bajas de usuario, grupos, afiliaciones, permisos por grupo y las excepciones por usuario,
con sus vigencias— siguen viviendo en `rentas` (ADR-0030 §3) y las trae la **etapa 2**, junto con
sus pantallas y las seis opciones que `CatalogoDelSistema` ya declara.

El esquema si nace entero, y ese orden es deliberado: una tabla que llega despues de su codigo llega
sin RLS y sin privilegios por columna, y eso no se ve hasta que se parte la base.

## Comandos

```bash
./gradlew build                   # todo, incluidas Spotless, Checkstyle y NullAway
./gradlew verificarAislamiento    # aislamiento multi-tenant. Bloqueante. Requiere PostgreSQL 16
./gradlew verificarArquitectura   # ArchUnit, escaner de fuentes y Spring Modulith. Bloqueante
./gradlew verificarArranque       # los dos perfiles levantan de verdad. Requiere PostgreSQL 16
./gradlew spotlessApply           # arregla el formato en vez de solo reprocharlo
```

**Este backend no compila sin `infrastructure` clonado al lado.** Las barreras se consumen como
*composite build* desde `../../infrastructure/librerias-backend`, y `settings.gradle.kts` lo
comprueba antes y falla diciendo que `git clone` falta, en vez de dejar reventar a Gradle sobre un
directorio que no esta.

## Pruebas que necesitan PostgreSQL

Lo necesita todo modulo que arranca la base con los fixtures de `kamayuk-identidad-esquema` —el
criterio es su `build.gradle.kts`: declara
`testImplementation(testFixtures(project(":kamayuk-identidad-esquema")))`—, mas el propio
`kamayuk-identidad-esquema`. Una base en memoria no tiene Row Level Security, asi que **la prueba
bloqueante numero uno no se podria escribir** (CAL-01 §2).

Por omision se levanta un contenedor con Testcontainers. Donde no hay Docker, se apunta a un
PostgreSQL 16 que ya exista — y **ninguna salida omite la prueba**: una prueba bloqueante que se
salta a si misma deja el build en verde sin haber verificado nada.

```bash
./gradlew build \
  -Dkamayuk.pruebas.postgres.url=jdbc:postgresql://localhost:5432/postgres \
  -Dkamayuk.pruebas.postgres.usuario=postgres \
  -Dkamayuk.pruebas.postgres.clave=…
```

Tiene que ser **superusuario**: el arnes crea los cuatro roles del cluster y les asigna su clave.
Y **PostgreSQL 16**, que es contra lo que corre CI.

**Si dos corridas apuntan al mismo motor**, el provisionamiento se serializa con un candado de
asesoramiento sobre la base `postgres` y la clave de cada rol se **deriva** del cluster, para que la
segunda no le cambie la clave a la primera mientras la primera la esta usando (#698). Si ese motor
no tuviera la base de coordinacion, la salida es `--max-workers=1` y sin ninguna otra corrida en
marcha; el mensaje lo dice con el remedio dentro.

## Lo que hay que entender antes de tocarlo

**La aplicacion no migra.** Arranca con `spring.flyway.enabled: false` y se conecta como
`kamayuk_app`, que no tiene DDL. Quien migra es `Migrador`, en su propio contenedor, como
`kamayuk_owner`, y termina antes de que la aplicacion arranque. Es el **mismo** codigo que
provisiona la base de cada prueba de persistencia: si el despliegue migrara por su cuenta, lo
verificado en CI y lo desplegado en la municipalidad dejarian de ser lo mismo.

**`crear-roles.sql` no es una migracion** y corre antes de la primera, como superusuario: las
politicas del baseline nombran roles que tienen que existir, y un rol no puede crearse a si mismo.
Los cuatro roles son los mismos que en los otros cuatro sistemas —son del **cluster**—, pero esta
base **no le da `CONNECT` a `rol_carga_parametros`**: esa credencial es la unica que escribe un
valor normativo (ADR-0007 §5) y aqui no hay ninguno.

**La unica desviacion del esquema respecto de los otros cuatro** son dos columnas: `modulo_sistema`
y `acceso` llevan `sistema`, y su `UNIQUE` es `(municipalidad_id, sistema, codigo)`. El motivo, y lo
que la columna todavia no arregla, estan escritos en la cabecera de `V1__baseline.sql` y en el
javadoc de `ComprobadorDeAccesoJdbc`.

**Si el build se queja del formato, no lo pelees: `spotlessApply`.** Checkstyle no revisa formato a
proposito, para no discutir con el formateador. Lo que si revisa, y es facil de incumplir con el
teclado en español, son los **identificadores con tilde**: `alicuota`, nunca `alícuota`.

## La imagen

Dos objetivos del mismo `Dockerfile`, y el contexto es la **raiz del repositorio**:

```bash
docker build -f backend/Dockerfile --target aplicacion -t ghcr.io/hneyra/kamayuk-identidad .
docker build -f backend/Dockerfile --target migrador  -t ghcr.io/hneyra/kamayuk-identidad-migrador .
```

Estan separados porque las credenciales son distintas y no deben convivir en el mismo contenedor.
El jar se llama `identidad.jar` y vive en `/opt/kamayuk/identidad.jar`.
