# Decisiones de arquitectura (ADR)

Un ADR registra una decision con su contexto y sus consecuencias. **No se editan una vez
aceptados**: si una decision cambia, se escribe otro ADR que declare obsoleto al anterior. El
historial de por que se hizo algo vale mas que la coherencia del documento.

## Los de este repositorio: **ninguno todavia, y es correcto que se vea asi**

Este sistema nace de una decision que **ya esta escrita, y no aqui**:
[ADR-0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md),
que contesta D-19 y vive en `infrastructure` porque lo que decide es el reparto en sistemas, que
es de alli (ADR-0029, ADR-0031).

Escribir aqui un ADR que repitiera esa decision daria **dos** documentos sobre lo mismo, y el dia
que alguien edite uno de los dos habria que preguntar cual manda. Lo que si va a haber aqui, y
tendra su ADR, son las decisiones que esta etapa **deja abiertas a proposito** y que las etapas 3,
4 y 5 de ADR-0039 tienen que cerrar:

- **cuanto dura la ventana de inconsistencia** de la copia local (ADR-0039 §«Lo que cuesta», punto
  2: «tiene que estar medida y escrita, no supuesta»);
- **que hace un consumidor con un evento que no sabe aplicar** —el defecto que `rentas`#54 midio en la
  ingestion de `catastro`—;
- **quien compone el catalogo de accesos de la sesion**, que es la parte de D-19 que ADR-0039
  **no** contesta.

Ninguna de las tres se puede decidir sin construir la etapa que la necesita, asi que no se
adelantan.

## La numeracion NO se reinicia

El ADR nuevo de este repositorio seria el **0040**, no el 0001. Los treinta y nueve existen y
estan repartidos; empezar de nuevo daria dos `ADR-0001` distintos en el mismo producto, y el dia
que alguien cite «ADR-0004» habria que preguntar de cual habla.

## Los que enlaza, y no copia

Viven en el repositorio de quien toma la decision. **Aqui solo esta el enlace**: una copia seria
un segundo ADR el dia que alguien edite uno de los dos.

| # | Decision | Vive en | Por que le importa a este repositorio |
|---|---|---|---|
| [0002](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0002-estrategia-multi-tenant.md) | Esquema compartido con Row Level Security | `infrastructure` | el aislamiento, que es el riesgo numero uno — y aqui lo aislado son los permisos |
| [0005](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0005-identidad-y-acceso.md) | Identidad y acceso | `infrastructure` | **la AUTENTICACION, que este sistema no toca**: es de Keycloak y ya era una sola |
| [0011](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0011-infraestructura-como-codigo.md) | Infraestructura como codigo | `infrastructure` | por que un descriptor devuelve objetos PLANOS, y §5: **la etiqueta de la imagen no la pone este repositorio** |
| [0012](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0012-usuarios-y-grupos-declarativos.md) | Usuarios y grupos declarativos | `infrastructure` | de donde salen los usuarios que este esquema copia, y por que una clave no vive en git |
| [0013](https://github.com/hneyra/rentas/blob/main/docs/30-arquitectura/adr/ADR-0013-permisos-de-la-sesion.md) | Permisos de la sesion | `rentas` | **por que la matriz se vuelve a pedir en cada renovacion**, que es lo que descarta meter los permisos en el token (ADR-0039 §«las otras dos») |
| [0028](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0028-el-tenant-no-cruza-por-http.md) | El contexto de municipalidad no cruza por HTTP | `infrastructure` | §3 es **el buzon** por el que ADR-0039 hace viajar la replica |
| [0029](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0029-cuatro-sistemas-separados.md) | Cuatro sistemas separados | `infrastructure` | por que hay sistemas separados, y con que criterio se reparte |
| [0031](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0031-infraestructura-comun-y-propia.md) | Infraestructura comun y propia | `infrastructure` | **por que `infrastructure/src/descriptor.ts` esta aqui** y que puede y no puede declarar |
| [0032](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0032-el-esquema-nace-en-baseline.md) | El esquema de cada sistema nace en un baseline | `infrastructure` | su `V1__baseline.sql`, y por que no hay historia de migraciones que traer |
| [0039](https://github.com/hneyra/infrastructure/blob/main/docs/30-arquitectura/adr/ADR-0039-la-identidad-es-un-sistema.md) | **La autorizacion es un sistema, y se replica por el buzon** | `infrastructure` | **es este repositorio entero**: por que existe, como se llama y en que orden se construye |

Decisiones **pendientes**:
[GOB-02](https://github.com/hneyra/sgtm/blob/migracion-a-microservicios/docs/00-gobierno/decisiones-abiertas.md).

## Plantilla

```markdown
# ADR-00XX — Titulo

| Campo | Valor |
|---|---|
| Estado | Propuesto \| Aceptado \| Obsoleto (reemplazado por ADR-00YY) |
| Fecha | AAAA-MM-DD |

## Contexto
## Decision
## Consecuencias
## Alternativas consideradas
```

El vocabulario del estado no cambia: **Propuesto**, **Aceptado** u **Obsoleto**, siempre con esa
letra.
