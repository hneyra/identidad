# `identidad` — Contexto para agentes

Usuarios, grupos, permisos y accesos: **quien puede hacer que, en que municipalidad**. Es el
**dueño de la autorizacion**, y no autentica a nadie.

El quinto repositorio de **Kamayuk**, el producto multi-municipal que reimplementa el sistema
documentado en el manual de usuario del SGTM de la Municipalidad Provincial de Sullana. Los otros
cinco son [`infrastructure`](https://github.com/hneyra/infrastructure) —el suelo y las barreras—,
[`rentas`](https://github.com/hneyra/rentas), [`catastro`](https://github.com/hneyra/catastro),
[`normativa`](https://github.com/hneyra/normativa) y [`caja`](https://github.com/hneyra/caja). El
reparto lo decide
[ADR-0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md);
que este sistema exista lo decide
[ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md),
al contestar D-19.

**Este es el sistema mas nuevo, y eso cambia como se lee todo lo de abajo**: casi nada de lo que
los otros cinco aprendieron por su cuenta se ha vuelto a descubrir aqui — se copio con su
comentario. Cuando algo diga «medido», lo que hay detras es la medicion del hermano que lo
descubrio, con su referencia; lo medido **aqui** va en la tabla del final, y esa **nace vacia**.

## Que hay hoy, medido y no supuesto

| Pieza | Estado |
|---|---|
| `infrastructure/` — el descriptor de despliegue | **Existe.** `yarn verificar` en verde —lint, tipos y **21 pruebas**—, sin Pulumi, sin token y sin cluster |
| `despliegue/compose.yaml` | **Existe**, con tres servicios encadenados contra la plataforma. **NO se ha levantado**: la maquina donde se escribio no tiene demonio de Docker, y decir que funciona seria deducirlo de que el YAML analiza |
| `.github/workflows/` | **Existe desde el primer commit**, con **cuatro** flujos: `backend.yml`, `infraestructura.yml`, `registro.yml` y `publicar-imagenes.yml`. **Sin `frontend.yml` ni `documentacion.yml`**, y es una afirmacion: no hay pantalla ni corpus |
| `docs/00-gobierno/` — la guarda del registro y su autoprueba | **Existen y corren en verde**: 9 muestras, 3 rutas de codigo de produccion, todas con muestra que las ejerza |
| `backend/` — seis modulos | Lo aporta el otro carril del issue #1. Su estado real lo dice **su** fila del registro, no esta tabla |
| `docs/30-arquitectura/adr/` | **El indice, y ningun ADR propio.** Es correcto: la decision que crea este sistema es ADR-0039 y vive en `infrastructure`; copiarla aqui daria dos documentos sobre lo mismo |
| Las imagenes `ghcr.io/hneyra/kamayuk-identidad{,-migrador}` | **NO existen.** El descriptor las nombra igual, y es correcto en esta etapa |
| Que `infrastructure` lo componga | **NO todavia.** Es su PR hermano (AC-6). Mientras tanto este descriptor se verifica aqui y **no lo compone nadie** — el riesgo que ADR-0031 §Consecuencias llama «el descriptor que nadie compone», cuyo sintoma es «lo desplegue y no cambio nada» |

## Lo que este repositorio NO hace

- **NO autentica.** Eso es Keycloak, y ya era uno solo para los cuatro (ADR-0005, ADR-0030 §3).
  ADR-0039 §«No toca» lo dice con todas las letras. Aqui **no se guarda ninguna contrasena**: lo
  que une la fila de `usuario` con la identidad del token es la cuenta, y la cuenta la crea
  `reconciliar-identidades.sh` en el emisor (ADR-0012).
- **NO decide que opciones tiene cada sistema.** El catalogo de opciones sigue siendo de su dueno
  —`rentas` deriva sus **134** de `docs/10-negocio/catalogo-de-opciones.md`; `catastro` (17),
  `caja` (4) y `normativa` (2) las escriben a mano (ADR-0039 §«Lo que cuesta», punto 5)—. Lo que
  este sistema guarda es **a quien se le concede cada una**.
- **NO esta en el camino caliente de ninguna autorizacion.** Cada sistema sigue autorizando contra
  **su copia local**: el `GuardiaDeAcceso` no cambia y `ComprobadorDeAccesoJdbc` sigue leyendo su
  propia base. Lo que cambia es **de donde sale esa copia**. Es lo que conserva la propiedad que
  `caja` existe para tener —«no le pregunta nada a nadie para cobrar […] es lo que hace cierto que
  la ventanilla cobre con `rentas` apagado»— y es el motivo por el que ADR-0039 descarto la salida
  1, que habria convertido «`rentas` caido» en «la ventanilla cerrada».
- **NO llama a ningun sistema hermano, y es una afirmacion.** Su egreso es DNS, su motor y
  Keycloak. La etapa 4 **no cambia esta lista**: quienes van a leer de aqui son los cuatro, asi
  que las aristas nuevas apareceran en SUS descriptores.
- **NO se llama `seguridad`, nunca.** ADR-0033 reserva esa palabra para un sistema de *seguridad
  ciudadana*. Dos sistemas con ese nombre se descubren cuando alguien dimensiona el motor
  equivocado o abre el repositorio equivocado.
- **No decide la etiqueta de su imagen, ni su namespace, ni sus `PriorityClass`.** Las pone
  `infrastructure`.
- **No tiene `git log` de su historia**, porque no tiene historia: es el unico de los seis que no
  sale del corte del monolito. Lo que si se hereda son las decisiones, y estan enlazadas.

## Estructura

```
backend/                Gradle. Java 25, Spring Boot 4. Seis modulos
  kamayuk-identidad-dominio-compartido/  objetos de valor y contexto de tenant
  kamayuk-identidad-esquema/             V1__baseline.sql y la prueba de aislamiento
  kamayuk-identidad-plataforma/          token -> SET LOCAL -> RLS, y el patron de repositorio
  kamayuk-identidad-nucleo/              el contexto acotado. Vacio salvo su `package-info`
  kamayuk-identidad-seguridad/           la copia local, la implantacion y el comprobador
  kamayuk-identidad-aplicacion/          ensambla el artefacto, y donde corren las barreras
infrastructure/         el descriptor de despliegue en TypeScript, con yarn
  src/descriptor.ts                      lo que este sistema aporta al cluster
  verificaciones/                        sus pruebas. NO son codigo de produccion
despliegue/compose.yaml la otra forma de levantarlo. Tiene que decir LO MISMO que el descriptor
docs/                   gobierno, el indice de ADR, los hallazgos de RLS y D0
```

**No hay `frontend/` ni `infra/`, y las dos ausencias son afirmaciones.** No hay pantalla —las de
administracion viven hoy en `rentas` y su mudanza es otro issue (ADR-0039, etapas 2 y 3)— ni
guiones de carga de datos. El dia que los haya, su ruta entra en `RUTAS_DE_CODIGO` de
`docs/00-gobierno/verificar-fila-del-registro.mjs` **y su muestra en la autoprueba**, que es lo que
esa autoprueba exige: una ruta sin muestra no falla, **deja de comprobarse en verde**.

El backend **no compila sin `infrastructure` clonado al lado**: las barreras se consumen como
*composite build* desde `../../infrastructure/librerias-backend`. Y el descriptor **no instala sin
el**: su dependencia es `link:../../infrastructure/infra/contrato`, y eso lo resuelve yarn contra
el disco — ninguna variable de entorno lo redirige.

Los paquetes son `kamayuk.identidad.*`; los modulos, `kamayuk-identidad-<contexto>`. Los **roles de
base de datos son `kamayuk_owner`, `kamayuk_app` y `kamayuk_readonly`**. Son del **cluster**, que
los cinco sistemas comparten, asi que se renombran en los cinco a la vez o en ninguno: un
`crear-roles.sql` con el nombre nuevo y otro con el viejo dejan a uno de los dos sin poder
conectarse.

## La colision de nombre con Keycloak

**En la plataforma, `identidad` ya significa Keycloak.** Este repositorio no puede cambiarlo, asi
que aplica **una regla**: *donde el nombre chocaria con el `identidad` de Keycloak, este sistema
dice `identidad-sistema`*. Son dos sitios, y cada uno tiene su medida:

| Donde | Valor | Que pasaria con `identidad` |
|---|---|---|
| `componente` de sus pods (`infrastructure/src/descriptor.ts`) | `identidad-sistema` | `infra/descriptor/sistemas.ts:35` descarta `componente: identidad` del **grafo de egreso** como infraestructura: las cuatro aristas de la etapa 4 desapareceria y el grafo diria que a este sistema no lo llama nadie. No es un rojo: es un grafo que miente |
| El servicio del backend (`despliegue/compose.yaml`) | `identidad-sistema` | el alias de red `identidad` **lo tiene Keycloak** en `kamayuk-plataforma` (`plataforma.compose.yaml:106`), y los cinco backends le piden ahi su JWKS: con dos contenedores registrandolo, el DNS de Docker reparte y **todo token seria invalido de forma intermitente** |

La etiqueta `sistema: identidad` **no cambia** —la pone `infrastructure` en `commonLabels`— y es la
que dice de quien es el pod. **Y `componente: identidad` sigue siendo correcto como DESTINO**: es
como este descriptor nombra a Keycloak en su egreso, y hay una prueba que lo fija para que «no uses
`identidad`» no se cumpla dejando a este sistema sin poder traerse el JWKS.

**Lo que NO se cierra desde aqui**: que `grafoDeEgreso` distinga la plataforma por el **namespace
de destino** y no por el nombre de la etiqueta. Es un cambio en `infrastructure` (AC-7 del issue
#1). Y mientras `servicioDe()` de `compose-de-los-sistemas.ts` componga el servicio del backend
como el nombre del sistema, su guarda sale **roja** aqui diciendo «el compose no tiene ningun
servicio «identidad»» — un rojo ruidoso que nombra la decision, que se prefiere al DNS que reparte
entre dos contenedores porque **ese no se pone rojo en ningun sitio**.

## Antes de escribir codigo, leer

| Si vas a tocar… | Lee |
|---|---|
| Cualquier cosa | [ADR-0002 — Estrategia multi-tenant](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0002-estrategia-multi-tenant.md) — es el riesgo numero uno |
| Base de datos | [Los cinco hallazgos de RLS](docs/40-datos/hallazgos-de-rls.md) **primero**. Aqui el hallazgo 1 pesa el doble: lo aislado son los permisos |
| Por que existe este sistema | [ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md) — entero, incluidas sus cinco etapas y lo que cuesta |
| La replica a los cuatro | [ADR-0028](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0028-el-tenant-no-cruza-por-http.md) §3, que es el buzon |
| Permisos de la sesion | [ADR-0013](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0013-permisos-de-la-sesion.md), de `rentas`: la matriz se vuelve a pedir en cada renovacion |
| El descriptor | [ADR-0011](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0011-infraestructura-como-codigo.md) y [ADR-0031](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0031-infraestructura-comun-y-propia.md) §2 |
| El esquema | [ADR-0032](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0032-el-esquema-nace-en-baseline.md) — nace en baseline, sin historia que traer |
| Backend | [ARQ-04 — Estandares de codigo](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/estandares-de-codigo-backend.md) |
| Montar el entorno | [D0 — Desarrollo](docs/D0-desarrollo/README.md) |

Indice de decisiones: [`docs/30-arquitectura/adr/README.md`](docs/30-arquitectura/adr/README.md).

## Decisiones abiertas que bloquean

Registro completo en [GOB-02](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/decisiones-abiertas.md).

| # | Decision | Bloquea |
|---|---|---|
| ~~D-19~~ | **Contestada el 2026-09-09** en su parte de «quien es el dueno»: la autorizacion es un sistema propio y se replica por el buzon ([ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md)). Sigue abierto **quien compone el catalogo de accesos de la sesion** | La sesion |
| D-22 | **Quien opera cinco despliegues.** Una municipalidad no opera cinco, y este sistema es el quinto | La implantacion |
| D-25 | **Si la separacion llega o no al hierro.** Cinco sistemas sobre un k3s de un nodo comparten disponibilidad — y `prod` **ya no cabia** con cuatro (#1) | El dimensionado |
| — | **Cuanto dura la ventana de inconsistencia** de la copia local. ADR-0039 §«Lo que cuesta» punto 2 exige que este **medida y escrita, no supuesta**. No se puede decidir sin la etapa 4 | Las etapas 4 y 5 |

## Reglas que no se negocian

Son las mismas en los seis repositorios, y las verifica **el mismo artefacto**:
[`comun-verificaciones`](https://github.com/hneyra/infrastructure/tree/main/librerias-backend/comun-verificaciones),
que vive en `infrastructure` y se consume como *composite build*.

| # | Regla | Motivo |
|---|---|---|
| 1 | **Importes en `BigDecimal`/`NUMERIC`.** Prohibidos `double` y `float` | Precision monetaria (RNF-055) |
| 2 | **Ningun metodo de dominio recibe `municipalidadId`.** Sale del token, se fija una vez con `SET LOCAL` | Si el desarrollador no lo maneja, no puede olvidarlo |
| 3 | **`SET LOCAL`, jamas `SET SESSION`** | `SET SESSION` sobrevive al retorno de la conexion al pool y contamina la peticion de otra municipalidad |
| 4 | **Sin `DELETE`** en deuda, pagos, recibos, valores, valuaciones, asientos ni auditoria. Se anula, se da de baja o se reversa | RNF-051, y el manual §Auditoria. **Aqui es la mas literal de las once**: un acceso no se borra, se revoca — si se borrara, la auditoria no podria decir quien lo tuvo |
| 5 | **Ningun literal numerico tributario en el codigo** | Reproducibilidad y cambio sin despliegue (RNF-053) |
| 6 | **Las reglas tributarias son funciones puras.** Sin base de datos, sin reloj, sin configuracion global; la fecha entra como argumento | Recalcular 2027 en 2037 debe dar el mismo centimo |
| 7 | **Nada de Spring ni JPA en la capa `dominio`** | Las reglas deben probarse sin levantar el contexto |
| 8 | **`alicuota`, nunca `tasa`**, para un porcentaje | `tasa` es un tipo de tributo |
| 9 | **No existe «la deuda»:** es `deudaActualizadaA(fecha)`, y toda cifra mostrada indica su fecha | RNF-075 |
| 10 | **Toda modificacion de datos exige observacion del usuario.** Sin observacion no se guarda | Manual §Auditoria; RNF-052. **Y aqui manda sobre cada concesion y cada revocacion**: quien concedio un permiso, cuando y por que |
| 11 | **Ningun SQL cruza la frontera de sistema** —un `JOIN` contra una tabla de otro sistema no deja huella en el bytecode, asi que lo vigila un escaner de texto y no ArchUnit | El dia que la base se parta, esa consulta deja de funcionar en produccion y no antes |

**Las reglas 1, 5, 6, 8 y 9 no tienen sujeto en este repositorio, y eso no las apaga**: aqui no hay
ni un importe ni una regla tributaria. Se conservan porque las verifica el mismo artefacto que los
otros cinco, y una regla que se quita «porque hoy no aplica» es la que nadie repone el dia que
aparece el primer importe.

**Si agregas una regla, agrega tambien la clase de muestra que la viola**, en las `muestras/` de
`comun-verificaciones`: una regla que no puede fallar no protege nada. Y lo exige por construccion
`ReglasDeArquitecturaMuerdenTest`, un `@TestFactory` sobre todas las reglas: una regla sin muestra
sale roja sola.

Lista completa con su justificacion:
[ARQ-04 — Estandares de codigo del backend](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/estandares-de-codigo-backend.md).

## Idioma

Español en el dominio, ingles en lo tecnico. **Sin tildes en identificadores**: Checkstyle lo
revisa en el backend, ESLint en el descriptor.

```java
public final class Acceso { … }                    // dominio: español
public interface AccesoRepository { … }            // patron: ingles
grupo.conceder(acceso, observacion);               // comportamiento: español
repository.findById(id);                           // infraestructura: ingles
```

Tablas y columnas en español `snake_case`. Campos de la API JSON en español `camelCase`.
Comentarios, pruebas y mensajes de commit en español.

## Comandos

```bash
cd backend
./gradlew verificarArquitectura   # ArchUnit, escaner de fuentes, aserciones y frontera de sistema
./gradlew verificarArranque       # el artefacto levanta en los dos perfiles (C-7). Requiere PostgreSQL 16
./gradlew verificarAislamiento    # aislamiento multi-tenant. BLOQUEANTE. Requiere PostgreSQL 16
./gradlew build                   # lo anterior mas Spotless
./gradlew spotlessApply           # arregla el formato en vez de solo reprocharlo

cd ../infrastructure
yarn install && yarn verificar    # el descriptor: lint, tipos y pruebas. Sin Pulumi ni cluster

# La plataforma: PostgreSQL con las bases, Keycloak con sus dos realms, Traefik y el buzon
cd ../../infrastructure
docker compose -f despliegue/plataforma.compose.yaml up -d --wait

# Y ESTE sistema contra ella: migraciones -> implantacion -> backend
cd ../identidad
docker compose -f despliegue/compose.yaml up --build --wait

# La guarda del registro y su autoprueba, que va ANTES
node docs/00-gobierno/verificar-las-muestras-del-registro.mjs
node docs/00-gobierno/verificar-fila-del-registro.mjs
```

**`verificarAislamiento` no se omite sin Docker: falla.** Una prueba bloqueante que se salta a si
misma deja el build en verde sin haber verificado nada. La salida documentada es apuntar a un
PostgreSQL 16 que ya exista, y **ninguna que omita la prueba**:

```bash
./gradlew verificarAislamiento \
  -Dkamayuk.pruebas.postgres.url=jdbc:postgresql://localhost:5432/postgres \
  -Dkamayuk.pruebas.postgres.usuario=postgres \
  -Dkamayuk.pruebas.postgres.clave=…
```

Tiene que ser **superusuario**, porque la prueba crea los roles del cluster, y **PostgreSQL 16**,
que es el motor que la plataforma levanta: verificar contra otro no dice nada del que corre. **El
esquema de este sistema no declara ninguna extension**, asi que la imagen `postgis` que el CI se
trae no la pide el esquema — la pide que el motor sea el mismo.

Como montarlo desde cero: [D0 — Desarrollo](docs/D0-desarrollo/README.md).

## Verificar antes de afirmar

**Ejecutar la prueba vale mas que razonar sobre ella.** Y no basta con que la verificacion este
escrita: **tiene que demostrarse que puede fallar** — se rompe a proposito el codigo que protege,
se ejecuta, y se anota el rojo exacto que sale.

Cada issue deja aqui una fila con que se implemento, **con que rotura se demostro que la
verificacion muerde** y que rojo produjo. Es lo que impide volver a descubrir el mismo hallazgo por
tercera vez.

> **La tabla nace vacia, y es correcto que se vea asi.** El registro anterior —288 filas, issue a
> issue— es historia de `sgtm` y **no viaja**: en un repositorio sin ese `git log` seria el
> registro de un trabajo que aqui no se hizo. Vive en
> [`sgtm/CLAUDE.md`](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/CLAUDE.md),
> que no se borra. Se consulta; no se copia.
>
> Y aqui la tabla nace vacia por partida doble: este es el unico de los seis repositorios que **no
> sale del corte del monolito**, asi que no hay ni siquiera un trabajo anterior al que referirse.
> La primera fila es la del issue #1.

Que la fila **exista** lo comprueba `docs/00-gobierno/verificar-fila-del-registro.mjs` en cada PR
que cierre un issue y toque codigo de produccion —`backend/<modulo>/src/main/`,
`infrastructure/src/` y `despliegue/`, las tres desde el primer dia—. Lo que la fila **diga** —que
la mutacion sea real y las cifras cuadren— no lo puede leer una maquina: eso lo lee la revision.

| Verificacion | Como se demostro que puede fallar | Resultado |
|---|---|---|
| **#1 — el repositorio existe, verifica en CI y despliega como quinto sistema** (`backend/` con seis modulos y su baseline de 13 tablas; `infrastructure/src/descriptor.ts` con 21 pruebas; `despliegue/compose.yaml`; los cuatro flujos de CI; la guarda del registro con tres rutas de codigo y su autoprueba; y el PR hermano de `infrastructure` que lo compone) | **Diez roturas, cada una aplicada sola y restaurada por copia comparada con `cmp`** —«RESTAURADO IGUAL» las diez—: (R1) quitar `ALTER TABLE usuario FORCE ROW LEVEL SECURITY` del baseline; (R2) devolver las dos `UNIQUE` de `modulo_sistema` y `acceso` a `(municipalidad_id, codigo)`; (R3) que el arnes de la prueba de aislamiento intente conectar tambien con `rol_carga_parametros`; (R4) quitar `kamayuk-identidad-nucleo` de `SISTEMA_DEL_MODULO`; (R5) un `@RequiereAcceso(acceso = "usuarios")` en `src/main`; (R6) quitar el `@ExtendWith` de `ArquitecturaTest`; (R7) etiquetar los pods con `componente: identidad`, que es Keycloak; (R8) una etiqueta de imagen escrita a mano en el migrador; (R9) quitar `owner` del inventario de claves; y (R10) quitar de la autoprueba del registro la muestra que toca `despliegue/` | **1, 1 (initializationError), 1, 1, 1, 1, 2, 3, 1 y exit 1.** (R1) «usuario tiene FORCE ROW LEVEL SECURITY (sin esto, el propietario evade la politica): Expecting value to be true but was false» —la barrera numero uno, y la unica que se ejecuto ademas desde fuera del agente que la escribio—. (R2) «duplicate key value violates unique constraint "modulo_codigo_uq"»: `DatosDePrueba` siembra a proposito el mismo codigo para `identidad` y para `rentas`, que es el motivo de la **unica desviacion del DDL identico**: `modulo_sistema` y `acceso` ganan `sistema`, porque esta base guarda a quien se concede cada opcion de CUATRO catalogos y dos sistemas pueden nombrar igual dos opciones distintas. (R3) «FATAL: permission denied for database … User does not have CONNECT privilege»: `crear-roles.sql` crea los cuatro roles del cluster y **no** le da `CONNECT` a `rol_carga_parametros`, que es de `normativa`. (R4) «Expecting empty but was: `["kamayuk-identidad-nucleo"]`». (R5) `CatalogoDelSistemaTest` con «Expecting empty but was: `["usuarios"]`» y el remedio dentro: la prueba de los otros cuatro contrasta el catalogo contra los `@RequiereAcceso` y aqui hay **cero** endpoints, asi que copiada saldria roja acusando al catalogo de tener seis de mas; lo que afirma en su lugar es el estado de partida y caduca sola con el primer controlador. (R6) «Rule 'classes that son controladores HTTP …' failed to check any classes» — **este repositorio es la combinacion que `comun-verificaciones` no contempla: tiene dominio y no tiene capa web.** El unico permiso publicado, `sinContextosAcotadosTodavia()`, exige que tampoco haya dominio; se resuelve DENTRO de este repositorio con una extension que desactiva ese unico metodo de la base y aplica las mismas veinte reglas perdonando solo las cinco sin sujeto, contadas en las dos direcciones, y que cae sola con el primer controlador. Lo que falta en la libreria —el permiso por ambito— queda declarado. (R7) 2 en rojo, las dos de AC-7, y el resto verde: el defecto es solo del grafo. **La colision es real y esta medida**: `componente: identidad` es Keycloak en `infra/componentes/Identidad.ts` y `grafoDeEgreso` (`descriptor/sistemas.ts:35`) filtra esa etiqueta como infraestructura, asi que un sistema con ella desaparece del grafo; los pods de aqui llevan `componente: identidad-sistema` y el compose llama al backend igual, porque en la red `kamayuk-plataforma` el servicio `identidad` es Keycloak y dos alias iguales reparten el DNS. (R10) «`/^despliegue\//` no lo toca ninguna muestra que espere rojo». **Lo que la plantilla tenia y aqui no cabe, quitado con su motivo**: los dos modulos de negocio de `normativa`, sus dos pruebas de contrato (nadie consume nada de aqui todavia, y la entrada de Gradle tiene que volver con la primera operacion), `kamayuk.redondeo`, y las dos rutas sparse-checkout de `backend.yml`. **Y dos cosas que el encargo no decia y la medida corrigio**: `documento_emitido` tambien esta en los cuatro baselines (son 13 tablas comunes y no 12) y viaja con el paquete `documentos` de `plataforma`, asi que entra; y `nucleo` vacio **si** es un modulo para Spring Modulith 2 —el comentario heredado del SRTM que decia lo contrario salio falso al ejecutar `ModulosTest`—. **Cifras, medidas en esta maquina dos veces** (PostgreSQL 16.13 nativo en el 5432, JDK 25.0.4, `./gradlew build verificarArquitectura verificarAislamiento verificarArranque --rerun-tasks`): **488 pruebas, 0 fallos, 1 omitida** —la de la base que la extension desactiva—, `BUILD SUCCESSFUL` en 1 m 26 s; `cd infrastructure && yarn verificar` 21 de 21; la autoprueba del registro 9 muestras y 3 rutas. **Lo que no se pudo verificar aqui**: el compose no se levanto (sin demonio de Docker); la imagen no se construyo; y `yarn capacidad` con este sistema dentro es del PR hermano de `infrastructure`, que lo mide antes y despues |
