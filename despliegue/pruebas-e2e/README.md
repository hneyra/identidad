# Ejercer la API de `identidad` contra la pila levantada

Esta carpeta es para dos audiencias:

* **el frontend y los otros modulos**, que necesitan saber que pide y que devuelve esta API sin
  leerse el backend — para eso son los `.http`;
* **el que despliega**, que necesita saber si lo que acaba de levantar *funciona* — para eso es
  `ejercer.sh`, que afirma y sale con codigo distinto de cero.

| Archivo | Que es |
|---|---|
| `00-token.http` | los **dos** tokens, y por que no son intercambiables |
| `10-seguridad.http` | las **21** operaciones de `/identidad/api/v1/seguridad` |
| `20-eventos.http` | el buzon: las 2 operaciones que consumen los cuatro satelites |
| `ejercer.sh` | los mismos ejercicios en `curl`, con **55 casos** que afirman |

Los `.http` se abren con REST Client (VS Code) o con el cliente HTTP de IntelliJ. `ejercer.sh` solo
necesita `curl`, `jq` y `bash`.

## Levantarlo todo: un comando

```bash
cd ../../../infrastructure/despliegue
./levantar-todo.sh identidad
```

Eso hace, en este orden —que no se puede permutar—: genera el `.env` si no existe (con una clave
**distinta** por rol), levanta la plataforma, levanta este sistema con su `--env-file`, y prepara las
identidades. Al terminar **imprime las dos claves** que `ejercer.sh` necesita:

```bash
cd ../../identidad/despliegue/pruebas-e2e
CLAVE_DEL_ADMINISTRADOR='<la que imprimio>' \
CLAVE_DE_SERVICIO_RENTAS='<la que imprimio>' \
  ./ejercer.sh
```

Con la pila bien levantada: **55 casos, 0 fallos**, codigo de salida 0.

**No omite nada en silencio.** Sin la clave de servicio el buzon no se salta: el guion falla
diciendo que falta. Para dejarlo fuera a proposito hay que pedirlo, y entonces lo dice en el
resumen:

```bash
./ejercer.sh --sin-buzon
```

Y lo mismo corre en CI en cada PR que toque la plataforma, con `schedule` diario:
[`infrastructure/.github/workflows/arranque-en-limpio.yml`](https://github.com/hneyra/infrastructure/blob/main/.github/workflows/arranque-en-limpio.yml).

## Las direcciones no estan escritas en ningun sitio, y es a proposito

Las pone `infrastructure/despliegue/.env`, y **no son las mismas en dos maquinas**: el `.env.ejemplo`
y los seis `docs/D0-desarrollo/` dicen 8080/8180/5432, y una instalacion cualquiera puede decir otra
cosa. Asi que `ejercer.sh` **las deriva de ese archivo** —`KAMAYUK_PUERTO_INGRESO`,
`KAMAYUK_PUERTO_IDENTIDAD`— y las imprime al arrancar. Escribirlas aqui era
[`infrastructure`#74](https://github.com/hneyra/infrastructure/issues/74): tenia los puertos de UNA
maquina, y en cualquier otra el sintoma es «Connection refused» a un servicio que esta arriba.

| Que | De donde sale |
|---|---|
| La API | Traefik, `KAMAYUK_PUERTO_INGRESO`, bajo `/identidad/api/v1` |
| Keycloak | `KAMAYUK_PUERTO_IDENTIDAD`, realm `kamayuk` |
| El correo (Mailpit) | `KAMAYUK_PUERTO_CORREO` |
| PostgreSQL | `KAMAYUK_PUERTO_BASE` |

Para apuntar a otra instalacion se pisa por entorno: `INGRESO`, `KEYCLOAK`, `REALM`, `UBIGEO`,
`ADMINISTRADOR`, y `ENV_DE_LA_PLATAFORMA` para leer otro `.env`.

**El backend no publica ningun puerto**: se entra por Traefik, que encamina `PathPrefix(/identidad)`
sin quitar el prefijo. Y `/actuator/health` **no** cuelga de `/identidad`, asi que desde el anfitrion
solo se alcanza entrando al contenedor:

```bash
docker compose -f despliegue/compose.yaml exec identidad-sistema \
  curl -s localhost:8080/actuator/health
```

Comprobar que la implantacion hizo su trabajo —**170 eventos y 5 cuentas** con el catalogo de hoy—:

```bash
docker compose -f ../infrastructure/despliegue/plataforma.compose.yaml exec base \
  psql -U postgres -d identidad \
  -c "SELECT tipo, count(*) FROM identidad_evento GROUP BY tipo ORDER BY tipo" \
  -c "SELECT cuenta FROM usuario ORDER BY cuenta"
```

## Los cuatro pasos que el guion automatiza, y los tres defectos que rodean

Esto **ya no hay que hacerlo a mano** —lo hace
`infrastructure/despliegue/identidad/preparar-identidades.sh`, que `levantar-todo.sh` encadena—, pero
se queda escrito porque los defectos siguen abiertos: el dia que se cierren, desaparecen del guion
**y de esta nota** a la vez.

| Paso | Que hace | Por que existe |
|---|---|---|
| 1 | `restaurar-ambitos-de-fabrica.sh` | **Rodeo de [#72](https://github.com/hneyra/infrastructure/issues/72).** Declarar `clientScopes` en el realm versionado **borra los trece ambitos de fabrica**. Sin `profile` no hay `preferred_username` y sin `basic` no hay `sub`, asi que **todo funcionario recibe 403** con un token perfectamente valido |
| 2 | `crear-usuario.sh administrador <clave> <id>` **sin `--reset`** | El alta declarativa (`reconciliar-identidades.sh`) crea al administrador **sin clave** y le manda un enlace por correo, que es lo correcto (ADR-0012) y no sirve para un arnes: sin clave permanente el `grant_type=password` no funciona |
| 3 | `reconciliar-identidades.sh servicios`, con una clave por cliente | **Parte de [#74](https://github.com/hneyra/infrastructure/issues/74).** `CLAVES_DE_SERVICIO` no tiene ninguna fuente en compose: en el cluster es un `Secret` montado, y aqui no hay quien lo escriba. El guion las genera en `despliegue/.claves-de-servicio/` (modo 700) y **nunca reescribe una que ya este**: reescribirla dejaria al consumidor que la lleva con un 401 que no se parece a su causa |
| 4 | alinear `municipalidad_id` con el id de la base | **Rodeo de [#73](https://github.com/hneyra/infrastructure/issues/73).** El claim se escribe con **tres valores distintos** segun quien lo escriba —el ubigeo (`200105`), el `municipalidadId` del JSON (`9`) y el id que la secuencia asigna (`1`)— y solo el ultimo lo entiende el RLS. Sin esto el sintoma es **403 «La cuenta «…» no esta dada de alta en este sistema» con la fila delante** en la tabla, que manda a mirar el alta: lo unico que esta bien |

Y el `--env-file` del `.env` de la plataforma —que el guion pone por su cuenta— es el resto de #74:
los cinco composes interpolan variables de ese archivo, ninguno declara `env_file:` y ninguno tiene
`.env` propio. Sin el, el `up` muere en el primer `${...:?}`.

**El paso 4 obliga al orden**: necesita el `id` que la SECUENCIA le dio a la municipalidad, y esa
fila la escribe la implantacion del sistema. Por eso la cadena es plataforma -> sistema ->
identidades, y no al reves. Corrido antes, el guion falla diciendo exactamente eso.


## Que se ejerce, y por que los negativos son la mitad

De los 55 casos, **16 son negativos**. No son adorno: son el contrato. Un e2e que solo prueba el
camino feliz no distingue una API que valida de una que se traga cualquier cosa.

| Se manda | Contesta |
|---|---|
| nada de token | 401 `NO_AUTENTICADO` |
| un token de persona al buzon | 403 `SIN_IDENTIDAD_DE_SERVICIO` — no `SIN_PRIVILEGIO`, porque se arregla pidiendo otro token y no concediendo un permiso |
| una escritura sin `observacion`, o con menos de 5 caracteres | 422 |
| permisos sin `sistema` | 422 enumerando los cinco |
| permisos **sin** `privilegios` | 422 — `[]` es la retirada explicita, y ausente es un olvido |
| un privilegio que no existe (`ESCRITURA`) | 422 con los siete |
| `ordenarPor` fuera de la lista blanca | 422 `ORDEN_NO_ADMITIDO` |
| un parametro de query que la operacion no declara | 422 nombrandolo |
| una vigencia invertida | 422 |
| una cuenta repetida | 409 |
| un grupo de otra municipalidad | 404 — el RLS no distingue «no existe» de «no es tuya», y eso es la propiedad, no el defecto |
| un verbo equivocado sobre una ruta que existe | 405 con `Allow` |
| retirar al ultimo administrador | 409 |
| `limite=501` en el buzon | 422 |
| un acuse que no es un UUID, o de un evento que no consta | 422 |

## Como se demostro que este e2e puede fallar

Un e2e que no puede fallar no protege nada. Medido: se cambio la etiqueta de Traefik de
`PathPrefix(/identidad)` a `PathPrefix(/identidad-otro)` en `despliegue/compose.yaml`, se recreo el
contenedor del backend, y `ejercer.sh` salio con **codigo 1 y 52 de 52 casos en rojo**, el primero
diciendo «esperaba 200 y contesto 404». Restaurado el compose y recreado el contenedor, vuelve a
**55 casos, 0 fallos**.

## Lo que este e2e NO cubre, dicho

* **El portal del ciudadano.** `SeguridadWeb` ya monta su cadena contra el realm
  `kamayuk-ciudadano`, pero no hay ni un controlador bajo `/identidad/api/v1/portal/**` todavia.
* **Los cuatro consumidores de verdad.** Lo que se ejerce es el buzon con un token de servicio; que
  los cuatro ingestores lo apliquen bien es de sus repositorios, y lo que se comprueba aqui son sus
  cuatro `contratos-que-consume/identidad.json` por reflexion, en `ContratoCon<Consumidor>Test`.
* **La ventana de inconsistencia** de la copia local: exige las cinco aplicaciones levantadas.
