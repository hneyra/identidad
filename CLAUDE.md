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
| `docs/00-gobierno/` — la guarda del registro y su autoprueba | **Existen y corren en verde**: **12 muestras, 4 rutas** de codigo de produccion, todas con muestra que las ejerza. La fila la busca en **`docs/agent/HISTORY.md` y solo ahi** desde el 2026-09-12, y tiene que ser una **fila** —una cabecera que cite el issue no cuenta— |
| `backend/` — seis modulos | Lo aporta el otro carril del issue #1. Su estado real lo dice **su** fila del registro, no esta tabla |
| `backend/kamayuk-identidad-nucleo` — el contexto acotado | **Ya no esta vacio** (etapa 2, [#2](https://github.com/hneyra/identidad/issues/2)). Las **once escrituras** de administracion —altas, bajas, reactivaciones, vigencias, la afiliacion y las dos matrices de permisos—, sus dos repositorios, los **cuatro controladores** bajo `/identidad/api/v1/seguridad`, el buzon de salida y la implantacion. Copiado de `rentas@33f329a2` (`kamayuk-rentas-seguridad`): `git mv` no cruza repositorios, asi que **la historia de esas clases se queda en el `git log` de `rentas`** |
| `identidad_evento` — el buzon de salida | **Existe** (`V2`). Siete tipos, el cuerpo con la fila **entera** tal como quedo, y la emision **dentro de la misma transaccion** que la escritura. **Sin `estado`**, e inmutable: hay cuatro consumidores y una sola columna no puede decir «entregado a `caja` y no a `rentas`» |
| `identidad_evento_acuse` — el acuse, por consumidor | **Existe** (`V3`, etapa 3). Clave `(municipalidad_id, consumidor, evento_id)` —el consumidor va DENTRO de la clave, y esa es toda la tabla—, `CHECK` de los **cuatro** que consumen (`identidad` no se consume a si mismo), RLS `ENABLE`+`FORCE`, e `INSERT, SELECT` y nada mas |
| El buzon **servido** | **Existe** (etapa 3): `GET /identidad/api/v1/eventos/pendientes` y `POST /identidad/api/v1/eventos/acuses`, con la opcion `eventos` del catalogo. **Quien pregunta sale del `azp` del token de servicio** —`kamayuk-<sistema>-servicio-<ubigeo>`— y nunca de un parametro. **Lo consumen los cuatro desde la etapa 4** ([#4](https://github.com/hneyra/identidad/issues/4)): cada uno con su ingestor, que declara lo que lee en `docs/50-api/contratos-que-consume/identidad.json` y se comprueba **aqui**, en `ContratoCon<Consumidor>Test`. Los cuatro contratos piden lo mismo —las dos operaciones, los siete campos del evento, `quedan`, `limite` y el cuerpo del acuse— y los cuatro cumplen. **Y desde la etapa 4 los cuatro pueden leerlo**: la implantacion da de alta sus cuatro cuentas de servicio y las afilia al grupo «Consumidores del buzon» |
| El grupo «Consumidores del buzon» y sus cuatro miembros | **Existe con sus cuatro cuentas dentro** desde la etapa 4 ([#10](https://github.com/hneyra/identidad/pull/10)). La implantacion da de alta `service-account-kamayuk-<sistema>-servicio-<ubigeo>` —los **cuatro** de `Consumidor`, sin `identidad`, que no se consume a si mismo— y las afilia, **emitiendo sus altas y sus afiliaciones por el buzon** como cualquier otra escritura. Nacia vacio hasta la etapa 3 «porque a quien se afilia lo decide quien despliegue»; **medido con las cinco aplicaciones levantadas, no lo decidia nadie**: el emisor crea el cliente confidencial de cada satelite, asi que el consumidor consigue su token, y aqui recibia **403 «la cuenta … no esta dada de alta en este sistema»**. Lo que sigue siendo del despliegue es que esas cuentas existan en el **emisor** (`reconciliar-identidades.sh servicios`) |
| `docs/10-negocio/catalogo-de-accesos/` | **Existe**, con los **cinco** catalogos —`rentas` **130** desde la etapa 4 (eran 134: su AC-4 retiro `usuarios`, `grupos`, `miembros` y `permisos`, y **conservo** `modulos` y `accesos`, que son lecturas de su copia local), `catastro` 16, `identidad` **7** —las seis del manual mas `eventos`, que la etapa 3 anade y que no es una pantalla—, `caja` 3, `normativa` 1: **157 opciones**—. El de `rentas` se **deriva** de su clon hermano con `derivar-catalogo-de-rentas.mjs`; los otros cuatro estan transcritos de su `CatalogoDelSistema`. Lo que impide que las cinco copias se separen de sus originales **no vive aqui**: es la guarda cruzada de `infrastructure` (AC-4) |
| Las once escrituras de `rentas` | **Ya no estan alli** (etapa 4, `rentas@d4c3fb5`): con su consumidor construido, `rentas` retiro sus once casos de uso, sus dos repositorios, tres controladores y las cuatro opciones de `SEGURIDAD` que los servian. **Desde la etapa 4 este es el unico sitio donde se administra.** Entre la 2 y la 4 hubo dos, y fue deliberado (AC-7) |
| `docs/30-arquitectura/adr/` | **El indice, y ningun ADR propio.** Es correcto: la decision que crea este sistema es ADR-0039 y vive en `infrastructure`; copiarla aqui daria dos documentos sobre lo mismo |
| Las imagenes `ghcr.io/hneyra/kamayuk-identidad{,-migrador}` | **NO existen.** El descriptor las nombra igual, y es correcto en esta etapa |
| Que `infrastructure` lo componga | **Si, medido el 2026-09-10.** `infra/descriptor/sistemas.ts:22,33` lo importa y lo registra en `SISTEMAS`, `infra/config.ts:373` lo lleva en `SISTEMAS_CON_IMAGEN`, y `infra/verificaciones/compose-de-los-sistemas.ts:124` ya tiene la excepcion `identidad → identidad-sistema` fijada en las dos direcciones. **El «rojo ruidoso» que anuncia `despliegue/compose.yaml:48-56` esta cerrado**, y esta fila decia lo contrario hasta hoy |

## Lo que este repositorio NO hace

- **NO autentica.** Eso es Keycloak, y ya era uno solo para los cuatro (ADR-0005, ADR-0030 §3).
  ADR-0039 §«No toca» lo dice con todas las letras. Aqui **no se guarda ninguna contrasena**: lo
  que une la fila de `usuario` con la identidad del token es la cuenta, y la cuenta la crea
  `reconciliar-identidades.sh` en el emisor (ADR-0012).
- **NO decide que opciones tiene cada sistema.** El catalogo de opciones sigue siendo de su dueno
  —`rentas` deriva sus **130** de `docs/10-negocio/catalogo-de-opciones.md`; `catastro` (**16**),
  `caja` (**3**) y `normativa` (**1**) las escriben a mano en su `CatalogoDelSistema` (ADR-0039
  §«Lo que cuesta», punto 5)—. Lo que este sistema guarda es **a quien se le concede cada una**, y
  desde la etapa 2 guarda ademas una **copia** de los cinco catalogos en
  `docs/10-negocio/catalogo-de-accesos/` para poder sembrarlos: es una copia y se dice, y lo que
  impide que se separe del original es la guarda cruzada de `infrastructure`. **Las cifras estan
  medidas el 2026-09-09 leyendo los cinco clones**, y la etapa 1 las tenia mal: decia 17, 4 y 2;
  y `rentas` dijo 134 durante las etapas 2 y 3 y dice **130** desde la 4, que le retiro las cuatro
  de administracion que ya no sirve.
  Las de **este** sistema son **siete** desde la etapa 3, y la septima —`eventos`— es la unica que
  no sale del manual: la piden las cuatro **cuentas de servicio** para leer y acusar el buzon.
- **NO esta en el camino caliente de ninguna autorizacion.** Cada sistema sigue autorizando contra
  **su copia local**: el `GuardiaDeAcceso` no cambia y `ComprobadorDeAccesoJdbc` sigue leyendo su
  propia base. Lo que cambia es **de donde sale esa copia**. Es lo que conserva la propiedad que
  `caja` existe para tener —«no le pregunta nada a nadie para cobrar […] es lo que hace cierto que
  la ventanilla cobre con `rentas` apagado»— y es el motivo por el que ADR-0039 descarto la salida
  1, que habria convertido «`rentas` caido» en «la ventanilla cerrada».
- **NO llama a ningun sistema hermano, y es una afirmacion.** Su egreso es DNS, su motor y
  Keycloak. La etapa 3 **no la cambia**, y es una decision: el buzon **se sirve** y no se empuja
  —empujar obligaria a conocer las cuatro direcciones, pedir cuatro credenciales y reintentar
  cuatro veces, o sea a que el dueno de la autorizacion dependa de que los cuatro esten arriba—.
  La etapa 4 tampoco, medido: quienes leen de aqui son los cuatro, y las aristas nuevas
  aparecieron en SUS descriptores —cada uno declara su egreso hacia `identidad-sistema`—, no en
  este.
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
  kamayuk-identidad-esquema/             V1__baseline.sql, V2 (el buzon) y la prueba de aislamiento
  kamayuk-identidad-plataforma/          token -> SET LOCAL -> RLS, y el patron de repositorio
  kamayuk-identidad-nucleo/              el contexto acotado: las once escrituras, los cuatro
                                         controladores, el buzon de salida y la implantacion
  kamayuk-identidad-seguridad/           el comprobador de acceso del guardia, y nada mas
  kamayuk-identidad-aplicacion/          ensambla el artefacto, y donde corren las barreras
infrastructure/         el descriptor de despliegue en TypeScript, con yarn
  src/descriptor.ts                      lo que este sistema aporta al cluster
  verificaciones/                        sus pruebas. NO son codigo de produccion
despliegue/compose.yaml la otra forma de levantarlo. Tiene que decir LO MISMO que el descriptor
docs/                   gobierno, el indice de ADR, los hallazgos de RLS, D0 y el catalogo de
                        accesos de los CINCO sistemas (`10-negocio/catalogo-de-accesos/`)
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
| ~~—~~ | **Cuanto dura la ventana de inconsistencia** de la copia local: **medida el 2026-09-10** con las cinco aplicaciones levantadas, y escrita en [`identidad-4-la-ventana-de-la-copia-local.md`](https://github.com/hneyra/infrastructure/blob/main/docs/00-gobierno/identidad-4-la-ventana-de-la-copia-local.md) — `T_espera (0-300 s) + T_arranque + T_vuelta(N)`: **~2 min 36 s tipico y ~5 min 10 s en el peor caso**, con ~330 eventos/s en caliente. Es lo que ADR-0039 §«Lo que cuesta» punto 2 exigia medir y no suponer | ~~La etapa 5~~ |

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
| 12 | **Ningun sistema que no sea `identidad` ESCRIBE la autorizacion** —`usuario`, `grupo`, `miembro`, `permiso`— (ADR-0039). **Aqui es el unico repositorio donde tiene que estar ENCENDIDA**, y lo esta desde la etapa 4: `escritoresDeLaAutorizacionConMotivo()` declara `AdministracionRepositoryJdbc` y `PermisoRepositoryJdbc`, **sin fecha de fin**, porque este sistema es el dueño | Dos sistemas que escriben la misma tabla de permisos en dos bases no dan un error: dan **dos respuestas** a «quien puede hacer esto» |

**La regla 12 nace desactivada en cada repositorio y aqui estuvo desactivada hasta la etapa 4**, con
su aviso impreso en cada corrida —«no se vigila en este repositorio … No es «no hay escrituras»: es
«no se ha mirado»»—. Declararla es lo que la enciende, y una entrada que no nombre una clase de
produccion la señala #27.

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

## El monolito se llamaba `sgtm`, y en la prosa se sigue llamando asi

El producto es **Kamayuk**. El sistema del que sale —el monolito retirado— se llamaba `sgtm`, y
ese nombre **ya no esta en el codigo**: ni en un realm, ni en una imagen, ni en un identificador, ni
en un dato de configuracion.

**Pero sigue en los comentarios, en `docs/` y en el registro de «Verificar antes de afirmar», y eso
es deliberado.** No es limpieza pendiente:

- una fila del registro que dice «copiado de `sgtm@33f329a2`» **es la medicion que se hizo**;
  reescribirla la falsifica, y borrarla pierde con que rotura se demostro;
- un comentario que dice «hasta `E` la sonda apuntaba a `sgtm`» **es el motivo por el que el codigo
  de al lado es como es**; quitar el nombre lo deja sin sujeto y hay que volver a descubrirlo;
- y varias guardas explican en su docblock **de que defecto vienen**, que es lo que impide que
  alguien las «simplifique».

**Asi que NO se hace una pasada de limpieza sobre la prosa.** Si estas aqui por un `grep sgtm` que
devuelve cientos de lineas: casi todas son de este tipo y se quedan.

**Lo que si esta prohibido es que la cadena vuelva al codigo**, y lo vigila **una sola guarda para
los seis**: `sin-el-nombre-del-monolito.test.ts` de `infrastructure`, que barre este arbol y los
cinco clones hermanos. Barre **solo codigo de produccion** —ni `docs/`, ni `*.md`, ni pruebas— y
**omite comentarios**, por lo de arriba.

Esta en un sitio y no en `comun-verificaciones` porque, medido, **del lado Java no hay nada que
vigilar**: `backend/*/src/main` de los cinco solo nombra el monolito en comentarios y en dos
`COMMENT ON COLUMN`. Anadir una prohibicion a la libreria compartida exigiria su clase de muestra y
tocaria los seis builds para vigilar el conjunto vacio.

**Dos excepciones declaradas, y las dos con su motivo dentro de la guarda.** (1) Los buckets
`sgtm-{stg,prod}-respaldos` (`infra/Pulumi.{stg,prod}.yaml`): **son el nombre de cosas que
existen**, y renombrarlos en el codigo sin renombrar el bucket manda los respaldos a un sitio que no
existe — y eso no da error hasta el dia que hay que restaurar. (2) Dos `COMMENT ON COLUMN` dentro de
un `V1__baseline.sql` **ya aplicado**: Flyway valida la suma de comprobacion de cada migracion, asi
que editar una que ya corrio hace fallar el arranque de **toda base existente**. No es que no se
quiera cambiar: **no se puede** — se corregiria con una migracion nueva, si alguna vez importa.

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
`infrastructure/src/` y `despliegue/` desde el primer dia, y
`docs/10-negocio/catalogo-de-accesos/` desde la etapa 2: los cinco catalogos viajan dentro del jar
y una opcion que se cae de uno deja su pantalla sin nadie que pueda dar permiso—. Lo que la fila **diga** —que
la mutacion sea real y las cifras cuadren— no lo puede leer una maquina: eso lo lee la revision.

| Verificacion | Como se demostro que puede fallar | Resultado |
|---|---|---|

**Las 12 filas viven en [`docs/agent/HISTORY.md`](docs/agent/HISTORY.md)**, y ahí es donde se
escribe la siguiente. Se mudaron el 2026-09-12: eran el **74 %** de este archivo, que se carga
entero en cada sesión
([`infrastructure`#114](https://github.com/hneyra/infrastructure/issues/114)).

La tabla de arriba se deja **con su cabecera y vacía** a propósito: es la forma de la fila que hay
que escribir, y tenerla delante evita ir a buscarla. **Pero escribirla aquí ya no cuenta**: desde
el 2026-09-12 —los seis repositorios migrados— la guarda busca la fila en `docs/agent/HISTORY.md`
**y solo ahí**, y exige que sea una **fila** (una línea que empiece por `|`): una cabecera o un
párrafo que citen el issue no la satisfacen.
