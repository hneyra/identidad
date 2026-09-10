# `identidad`

Usuarios, grupos, permisos y accesos: **quien puede hacer que, en que municipalidad**. Es el
quinto sistema de **Kamayuk**, y nace de contestar D-19 en
[ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md).

> **No autentica a nadie, y eso hay que leerlo antes que nada.** La **autenticacion** ya era una
> sola para los cuatro sistemas y sigue siendo de Keycloak (ADR-0005, ADR-0030 §3): ADR-0039 dice
> con todas las letras que **no la toca**. Lo que este sistema pasa a ser dueno de decidir es la
> **autorizacion**.
>
> **Y no se llama `seguridad`, tambien a proposito**: ADR-0033 ya reserva esa palabra para un
> sistema de *seguridad ciudadana* —serenazgo, luminarias, camaras—. Dos sistemas con ese nombre
> no se descubren al escribirlo: se descubren cuando alguien dimensiona el motor equivocado o abre
> el repositorio equivocado.

## Por que existe: el defecto que ADR-0039 midio

Las nueve escrituras de administracion de seguridad viven **solo** en `rentas`, y escriben **la
base de `rentas`**. En `catastro`, `normativa` y `caja` el unico escritor de las tablas de
seguridad es el sembrador de la copia local, que corre **una vez al implantar**, **solo agrega**
(`ON CONFLICT … DO NOTHING`) y siembra **un** usuario: el administrador. No hay evento, ni cliente
HTTP, ni tabla de sincronizacion entre ellos.

Consecuencia: **un permiso concedido en `rentas` no llega nunca a los otros tres**, y un
funcionario que no sea el administrador recibe un 403 de cada pantalla de `catastro`, `normativa` y
`caja` — con la unica salida de escribir SQL a mano contra produccion. Hoy no se ve porque cada
municipalidad declarada tiene un solo usuario; **se ve el dia que haya un segundo funcionario**.

Este repositorio nacio como la **etapa 1** de las cinco que ADR-0039 reparte: el sitio donde
poner lo demas. Lo de arriba describe el estado del que se salio. Desde la **etapa 4** la
administracion vive **solo aqui** —las once escrituras llegaron en la etapa 2, `rentas` retiro las
suyas y las cuatro opciones que las servian (134 → 130)—, cada escritura sale por el buzon
(`identidad_evento`) en su misma transaccion, el buzon se sirve por HTTP con acuse por consumidor
(etapa 3), y **los cuatro sistemas lo consumen** con su propio ingestor, que declara lo que lee en
`docs/50-api/contratos-que-consume/identidad.json` y se comprueba en el CI de aqui. Lo que sigue
sin medirse es **cuanto dura la ventana de inconsistencia** de cada copia local (etapa 5). Las
cuatro cuentas de servicio con que esos ingestores leen el buzon —`service-account-kamayuk-
<sistema>-servicio-<ubigeo>`— las da de alta y las afilia al grupo «Consumidores del buzon» la
propia **implantacion**: hasta que lo hizo, los cuatro conseguian su token del emisor y recibian un
403 de aqui, y su copia local se quedaba como la dejo su implantacion sin un solo error que lo
dijera.
El estado medido de cada pieza esta en `CLAUDE.md`, fila a fila.

## Que hay hoy, y que falta

| Pieza | Estado |
|---|---|
| `infrastructure/` — el descriptor (ADR-0031 §2) | **Existe y verifica**: `yarn verificar` en verde —lint, tipos y **21 pruebas**—, sin Pulumi, sin token y sin cluster |
| `despliegue/compose.yaml` | **Existe**: tres servicios encadenados contra la plataforma. **No se ha levantado**: la maquina donde se escribio no tiene demonio de Docker |
| `.github/workflows/` — su CI | **Existe desde el primer commit**, con cuatro flujos: el backend, el descriptor, la guarda del registro y la publicacion de las dos imagenes |
| `docs/00-gobierno/` — la guarda del registro y su autoprueba | **Existen y corren en verde**, con `/^despliegue\//` y `/^infrastructure\/src\//` **desde el primer dia**: es la deuda que `rentas`#45 y `caja`#39 tuvieron que pagar despues |
| `backend/` — seis modulos y **cero reglas de negocio** | Lo aporta el otro carril de este mismo issue: `dominio-compartido`, `esquema`, `plataforma`, `seguridad`, `nucleo` (vacio salvo su `package-info`) y `aplicacion`. Su estado real lo dice su propia fila del registro |
| `docs/30-arquitectura/adr/` | **Existe el indice, y ningun ADR propio** — es correcto: la decision que crea este sistema es ADR-0039 y vive en `infrastructure` |
| Las imagenes `ghcr.io/hneyra/kamayuk-identidad{,-migrador}` | **NO existen todavia.** El descriptor las nombra igual, y es correcto: describe como se desplegaria, y **`infrastructure` no puede declarar `kamayuk:versionDeIdentidad` hasta que las dos de ese `sha` esten publicadas y comprobadas contra el registro** |
| Que `infrastructure` lo componga como quinto sistema | **NO todavia**: es su PR hermano (AC-6 del issue #1). Hasta que entre, este descriptor se verifica aqui y no lo compone nadie — que es el riesgo que ADR-0031 §Consecuencias nombra: «el descriptor que nadie compone» |

## Por donde entrar

- **Montar el entorno y ejecutarlo**: [`docs/D0-desarrollo/README.md`](docs/D0-desarrollo/README.md).
- **Contexto para agentes**, con las once reglas y lo que este repositorio no hace:
  [`CLAUDE.md`](CLAUDE.md).

## El descriptor

```bash
cd infrastructure
yarn install
yarn verificar          # lint, tipos y pruebas. Sin Pulumi, sin token y sin cluster
```

Declara **su base y sus roles**, **su Deployment con sus tres sondas**, **su Job de migracion**,
**su Job de implantacion**, **sus rutas bajo su prefijo `identidad/`**, **su egreso**, sus alertas,
su panel y su inventario de claves. No declara la etiqueta de su imagen: la pone `infrastructure`,
y es lo que hace que una liberacion normal no sea un `pulumi up` (ADR-0011 §5).

**Su egreso, que es su grafo de dependencias:**

```
identidad  ──▶  su motor          (PostgreSQL, en el namespace de la plataforma)
           ──▶  Keycloak          (el JWKS con que valida los tokens que recibe)
           ──▶  (ningun sistema)
```

**Ningun sistema hermano, y es una afirmacion.** Este sistema no llama a nadie, y la etapa 4 de
ADR-0039 **no cambia esta lista**: quienes van a leer de aqui son los cuatro, asi que las aristas
nuevas apareceran en SUS descriptores. Es tambien lo que conserva la propiedad que `caja` existe
para tener —cobrar sin preguntarle nada a nadie—, y el motivo por el que ADR-0039 descarto la
salida de «`rentas` publica la API y los tres preguntan».

## La colision de nombre con Keycloak, y como se resuelve aqui

En la plataforma, **`identidad` ya significa Keycloak**. Este repositorio no puede cambiarlo, asi
que aplica **una regla**: *donde el nombre chocaria con el `identidad` de Keycloak, este sistema
dice `identidad-sistema`*. Son dos sitios, y cada uno tiene su medida escrita:

| Donde | Valor | Que pasaria con `identidad` |
|---|---|---|
| La etiqueta `componente` de sus pods | `identidad-sistema` | `descriptor/sistemas.ts:35` descarta `componente: identidad` del **grafo de egreso** como infraestructura: las cuatro aristas de la etapa 4 desapareceria y el grafo diria que a este sistema no lo llama nadie |
| El servicio del backend en el compose | `identidad-sistema` | el alias de red `identidad` **lo tiene Keycloak** en `kamayuk-plataforma`, y los cinco backends le piden ahi su JWKS: con dos contenedores registrandolo, el DNS reparte y todo token seria invalido de forma intermitente |

La etiqueta `sistema: identidad` **no cambia** —la pone `infrastructure`— y es la que dice de quien
es el pod. La otra mitad, que `grafoDeEgreso` distinga la plataforma por el **namespace de
destino** en vez de por el nombre de la etiqueta, es del PR hermano de `infrastructure` (AC-7).

## Lo que este repositorio NO decide

- **La etiqueta de su imagen.** La fija `infrastructure` al componer.
- **Su namespace ni sus `PriorityClass`.** Son de alcance de cluster.
- **Quien es cada persona.** Eso es Keycloak: aqui no se guarda ninguna contrasena.
- **Que opciones tiene cada sistema.** El catalogo de opciones sigue siendo de su dueno
  (ADR-0039 §«Lo que cuesta», punto 5); lo que este sistema guarda es **a quien se le concede
  cada una**.
- **Si su descriptor se aplica.** `infrastructure` lo audita con las mismas reglas que audita los
  suyos y **se niega** si incumple: una ruta fuera del prefijo, un `Deployment` sin limites, un
  `Secret` en claro o privilegios sobre la base de otro sistema.

## Los otros cinco repositorios

[`infrastructure`](https://github.com/hneyra/infrastructure) —el suelo y las barreras—,
[`rentas`](https://github.com/hneyra/rentas), [`catastro`](https://github.com/hneyra/catastro),
[`normativa`](https://github.com/hneyra/normativa) y [`caja`](https://github.com/hneyra/caja). El
archivo historico, y la unica copia con `git log` de la historia entera, es
[`sgtm`](https://github.com/hneyra/sgtm), que **no se borra ni se modifica**.
