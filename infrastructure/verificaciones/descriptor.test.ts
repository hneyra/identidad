import { describe, expect, it } from "vitest";
import type { Contenedor, EntornoDelDescriptor, Manifiesto } from "@kamayuk/infra-contrato";
import { identidad } from "../src/descriptor";

/**
 * El descriptor de `identidad`, verificado sobre lo que devuelve.
 *
 * Esto es lo que corre en la maquina de quien lo escribe y en el CI de este repositorio: **sin
 * Pulumi, sin token y sin cluster**. La auditoria completa —las convenciones de `INF-01` §4 y las
 * cinco prohibiciones— la hace `infrastructure` al componer; aqui se comprueba lo que este
 * repositorio decide y solo el.
 */

const ENTORNO: EntornoDelDescriptor = {
  ambiente: "stg",
  namespace: "kamayuk-identidad-stg",
  dominio: "stg.kamayuk.example",
  // `sistema: identidad` la pone `infrastructure` en `commonLabels` y no este descriptor. Se
  // escribe aqui porque es la mitad de AC-7 que este archivo NO controla y contra la que se mide
  // la otra: los pods llevan `sistema: identidad` y `componente: identidad-sistema`.
  etiquetas: { "app.kubernetes.io/part-of": "kamayuk", ambiente: "stg", sistema: "identidad" },
  imagenDe: (c) => `ghcr.io/hneyra/kamayuk-${c}:0eee58e43e04b1c2d3f4a5b6c7d8e9f0a1b2c3d4`,
  secretoDe: (c) => `kamayuk-identidad-stg-${c}`,
  prioridadDe: (clase) => `kamayuk-stg-prioridad-${clase}`,
  // Del AMBIENTE, no de este sistema (C-7): quien recibe el aviso cuando algo
  // se rompe aqui. `checkInvariants` de `infrastructure` rechaza el relleno.
  operacion: { responsable: "Guardia de plataforma", canal: "guardia@example.pe" },
  // La municipalidad que el AMBIENTE implanta (C-14, punto 4). Los cinco sistemas implantan la
  // misma, cada uno en su base.
  implantacion: {
    ubigeo: "200105",
    nombre: "Municipalidad Distrital de Catacaos",
    tipo: "DISTRITAL",
    administrador: "administrador",
    nombreDelAdministrador: "Administrador del sistema",
    esDemostracion: true,
    // El `id` de la fila que crea el Job de implantacion. En una base recien creada vale 1.
    municipalidadId: 1,
  },
  namespaceDe: (otro) => `kamayuk-${otro}-stg`,
  // El nombre de un `Job` lleva la version: un `Job` de Kubernetes es INMUTABLE.
  nombreConVersion: (base) => `${base}-0eee58e43e04`,
  plataforma: {
    namespace: "kamayuk-stg",
    // El anfitrion del motor, ya cruzando el namespace (C-17, punto 1). Los cuatro descriptores
    // del corte escribian `postgres:5432` a mano, que es el nombre del `compose.yaml` local: en
    // Kubernetes no existe ningun `Service` que se llame asi.
    motor: "kamayuk-stg-postgres.kamayuk-stg:5432",
    emisor: "https://stg.kamayuk.example/keycloak/realms/sgtm",
    jwks: "http://kamayuk-stg-identidad.kamayuk-stg:8080/keycloak/realms/sgtm/protocol/openid-connect/certs",
    token: "http://kamayuk-stg-identidad.kamayuk-stg:8080/keycloak/realms/sgtm/protocol/openid-connect/token",
  },
};

describe("el descriptor de identidad", () => {
  it("declara su base, y SOLO la suya", () => {
    const base = identidad.baseDeDatos(ENTORNO);
    expect(base.nombre).toBe("identidad");
    for (const rol of base.roles) {
      expect(rol.sobre).toEqual(["identidad"]);
      // Un superusuario OMITE RLS aunque haya FORCE (DAT-01 §0, hallazgo 1). En el sistema que
      // guarda quien puede que, eso seria leer los permisos de todas las municipalidades a la vez.
      expect(rol.superusuario).toBe(false);
    }
  });

  /**
   * `kamayuk_app` no recibe `DELETE`, y aqui es la regla 4 en su forma mas literal: un acceso no
   * se borra, se revoca — si se borrara, la auditoria no podria decir quien lo tuvo.
   */
  it("y el rol de la aplicacion no puede borrar nada", () => {
    const app = identidad.baseDeDatos(ENTORNO).roles.find((r) => r.nombre === "kamayuk_app");
    expect(app, "no hay rol `kamayuk_app`: la aplicacion no tendria con que conectarse").toBeDefined();
    expect(app?.privilegios).not.toContain("DELETE");
    expect(app?.privilegios).not.toContain("ALL");
  });

  it("no fija la etiqueta de ninguna imagen: la pide", () => {
    // La prohibicion (b) de `infrastructure`, comprobada aqui tambien porque es la que sostiene
    // que una liberacion normal NO sea un `pulumi up` (ADR-0011 §5).
    const admisibles = identidad.imagenes.map((n) => ENTORNO.imagenDe(n));
    const imagenes = [...identidad.despliegue(ENTORNO), ...identidad.migracion(ENTORNO)]
      .flatMap((m) =>
        m.kind === "Deployment"
          ? m.spec.template.spec.containers
          : m.kind === "Job"
            ? m.spec.template.spec.containers
            : [],
      )
      .map((c) => c.image);
    expect(imagenes.length).toBeGreaterThan(0);
    for (const i of imagenes) expect(admisibles).toContain(i);
  });

  it("todas sus rutas van bajo su prefijo", () => {
    for (const m of identidad.ingreso(ENTORNO)) {
      if (m.kind !== "IngressRoute") continue;
      for (const r of m.spec.routes) {
        for (const encaje of r.match.matchAll(/PathPrefix\(`([^`]*)`\)/g)) {
          expect(encaje[1]).toMatch(/^\/identidad(\/|$)/);
        }
      }
    }
  });

  it("no emite ningun Secret, y su inventario no trae valores", () => {
    const todos = [
      ...identidad.despliegue(ENTORNO),
      ...identidad.migracion(ENTORNO),
      ...identidad.implantacion(ENTORNO),
      ...identidad.ingreso(ENTORNO),
    ];
    expect(todos.some((m) => (m as { kind: string }).kind === "Secret")).toBe(false);
    for (const c of identidad.claves(ENTORNO)) {
      for (const campo of ["valor", "value", "data", "stringData", "password"]) {
        expect((c as unknown as Record<string, unknown>)[campo]).toBeUndefined();
      }
    }
  });

  it("todo contenedor declara limites de recursos", () => {
    const contenedores = contenedoresDe([
      ...identidad.despliegue(ENTORNO),
      ...identidad.migracion(ENTORNO),
      ...identidad.implantacion(ENTORNO),
    ]);
    expect(contenedores.length).toBeGreaterThan(0);
    for (const c of contenedores) {
      expect(c.resources.requests.cpu).toBeTruthy();
      expect(c.resources.limits.memory).toBeTruthy();
    }
  });

  it("NO tiene egreso a ningun sistema hermano, y es una afirmacion", () => {
    // Hoy este sistema no llama a nadie. La etapa 4 de ADR-0039 no cambia esta lista: quienes
    // van a leer de aqui son los CUATRO, asi que las aristas nuevas apareceran en SUS
    // descriptores. Si algun dia aparece una aqui, hay que decir a que sistema y para que.
    expect(destinosDeEgreso()).toEqual([]);
  });
});

/**
 * AC-7 — la colision de nombre con Keycloak, fijada donde se decide.
 *
 * ## El defecto que esta prueba impide, y no es hipotetico
 *
 * En la plataforma, `componente: identidad` **ya es Keycloak**: lo etiqueta asi
 * `infra/componentes/Identidad.ts` desde que existe. Y `infra/descriptor/sistemas.ts:35` declara
 *
 *     const infraestructura = ["postgres", "identidad"];
 *
 * y con esa lista **descarta de `grafoDeEgreso` toda arista cuyo `podSelector` lleve esa
 * etiqueta**. El grafo de egreso ES el grafo de dependencias del producto (ADR-0029).
 *
 * Consecuencia medida sobre el codigo de `infrastructure`: si los pods de este sistema llevaran
 * `componente: identidad`, el dia que la etapa 4 de ADR-0039 haga que `rentas`, `catastro`,
 * `normativa` y `caja` declaren su arista hacia aqui, **las cuatro se filtrarian como
 * infraestructura**. El grafo diria que a este sistema no lo llama nadie mientras los cuatro lo
 * llaman. No es un rojo: es un grafo que miente, que es lo que este proyecto lleva doscientos
 * issues evitando.
 *
 * ## Por que la prueba esta aqui y que NO cierra
 *
 * Lo que este repositorio decide es la etiqueta de SUS pods, y eso es lo que se fija. La otra
 * mitad —que `grafoDeEgreso` distinga la plataforma por el NAMESPACE de destino y no por el
 * nombre de la etiqueta— es un cambio en `infrastructure` y va en su PR hermano. Mientras no
 * llegue, este valor es lo unico que impide que este sistema desaparezca del grafo.
 *
 * Y se fija con una prueba, y no con un comentario, porque el valor «raro» es exactamente el que
 * alguien normaliza al copiar de un hermano: los cuatro ponen `componente: <sistema>`, y aqui eso
 * seria el defecto.
 */
describe("AC-7 · la etiqueta `componente` no colisiona con la de Keycloak", () => {
  it("los pods de este sistema llevan `componente: identidad-sistema` y `sistema: identidad`", () => {
    const pods = plantillasDePod([
      ...identidad.despliegue(ENTORNO),
      ...identidad.migracion(ENTORNO),
      ...identidad.implantacion(ENTORNO),
    ]);
    expect(pods.length, "no hay ninguna plantilla de pod: esta guarda no mediria nada").toBeGreaterThan(0);
    for (const etiquetas of pods) {
      expect(
        etiquetas["componente"],
        "`componente: identidad` es KEYCLOAK en la plataforma, y `descriptor/sistemas.ts:35` " +
          "descarta esa etiqueta del grafo de egreso como infraestructura: con ella, las cuatro " +
          "aristas que la etapa 4 de ADR-0039 va a traer desaparecerian del grafo y este sistema " +
          "figuraria como uno al que no llama nadie.",
      ).toBe("identidad-sistema");
      // Y la que SI dice de quien es el pod, que la pone `infrastructure` en `commonLabels`.
      expect(etiquetas["sistema"]).toBe("identidad");
    }
  });

  /**
   * El contraste, y hace falta: sin el, «no uses `identidad`» se podria cumplir no nombrandola en
   * ningun sitio — y entonces este sistema se quedaria sin poder traerse el JWKS, o sea sin poder
   * validar un token. `componente: identidad` es correcto **como destino** y solo como destino.
   */
  it("y como DESTINO sigue nombrando a Keycloak, que es donde esa cadena si es correcta", () => {
    const aKeycloak = destinosCrudos().filter(
      (d) => d.podSelector?.matchLabels?.["componente"] === "identidad",
    );
    expect(
      aKeycloak,
      "este sistema no declara egreso hacia Keycloak: sin el JWKS no puede validar un token, y " +
        "un backend que atiende sin poder validarlo responde a la sonda y no atiende a nadie " +
        "(ADR-0005). La AUTENTICACION sigue siendo de Keycloak (ADR-0039 §«No toca»).",
    ).toHaveLength(1);
    // Y en el namespace de la PLATAFORMA, que es lo que lo distingue de este sistema.
    expect(aKeycloak[0]?.namespaceSelector?.matchLabels).toEqual({
      "kubernetes.io/metadata.name": ENTORNO.plataforma.namespace,
    });
  });

  /**
   * La politica de egreso selecciona los pods de este sistema por la etiqueta que llevan.
   *
   * Es la mitad que se rompe en silencio si las dos se separan: una politica cuyo `podSelector`
   * no case con ningun pod **no da error** —Kubernetes la admite— y deja a los pods sin ninguna
   * restriccion declarada, o con la de otro. Se comparan las dos derivadas, no un literal.
   */
  it("y la politica de egreso selecciona exactamente esos pods", () => {
    const selectores = identidad
      .egreso(ENTORNO)
      .map((p) => p.spec.podSelector?.matchLabels?.["componente"]);
    expect(selectores).toEqual(["identidad-sistema"]);
    for (const etiquetas of plantillasDePod(identidad.despliegue(ENTORNO))) {
      expect(selectores).toContain(etiquetas["componente"]);
    }
  });
});

/** Los SISTEMAS a los que este descriptor declara egreso. El motor y Keycloak no cuentan. */
function destinosDeEgreso(): string[] {
  // La MISMA lista que `grafoDeEgreso` de `infrastructure` descarta como infraestructura. Se
  // escribe igual a proposito: es la lista contra la que se mide la colision de AC-7.
  const infra = ["postgres", "identidad"];
  return destinosCrudos()
    .map((s) => s.podSelector?.matchLabels?.["componente"])
    .filter((c): c is string => c !== undefined && !infra.includes(c))
    .sort();
}

/** Todos los destinos de todas las reglas de egreso, sin filtrar. */
function destinosCrudos() {
  return identidad
    .egreso(ENTORNO)
    .flatMap((p) => p.spec.egress ?? [])
    .flatMap((r) => r.to ?? []);
}

/** Las etiquetas de cada plantilla de pod: `Deployment` y `Job`. */
function plantillasDePod(manifiestos: readonly Manifiesto[]): Record<string, string>[] {
  return manifiestos.flatMap((m) =>
    m.kind === "Deployment" || m.kind === "Job"
      ? [(m.spec.template.metadata?.labels ?? {}) as Record<string, string>]
      : [],
  );
}

describe("C-14 — que esto se pueda desplegar", () => {
  /**
   * El Job de migracion corre la imagen del MIGRADOR, no la de la aplicacion.
   *
   * Hasta C-14 los cuatro corrian la misma que el `Deployment` con `KAMAYUK_DB_USUARIO=kamayuk_owner`
   * y sin perfil: arrancaban el proceso web con las credenciales del unico rol con DDL, y la
   * aplicacion tiene `spring.flyway.enabled: false` a proposito (ARQ-03 §4). O sea que ese Job
   * **no migraba**.
   */
  it("el Job de migracion corre el migrador, con las variables que el migrador lee", () => {
    const contenedores = contenedoresDe(identidad.migracion(ENTORNO));
    expect(contenedores).toHaveLength(1);
    const c = contenedores[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe("identidad-migrador"));
    expect(valorDe(c, "KAMAYUK_DB_OWNER_USUARIO")).toBe("kamayuk_owner");
    expect(declara(c, "KAMAYUK_DB_OWNER_CLAVE")).toBe(true);
    // La de la APLICACION. El migrador no la lee, y ponerla es lo que hacia que este Job
    // pareciera correcto sin migrar nada.
    expect(declara(c, "KAMAYUK_DB_USUARIO")).toBe(false);
  });

  it("y las dos imagenes son los dos objetivos del Dockerfile", () => {
    expect(identidad.imagenes).toEqual(["identidad", "identidad-migrador"]);
  });

  /**
   * El Job de implantacion (C-7 §2.3): la fila de `municipalidad` en SU base.
   *
   * Con el migrador de contenedor de inicializacion: un `Deployment` no sabe esperar a un `Job`,
   * y la salida del monolito —un contenedor con `psql`— no vale aqui, porque un descriptor solo
   * puede nombrar SUS imagenes (prohibicion (b)).
   */
  it("implanta la municipalidad del ambiente, detras del esquema", () => {
    const jobs = identidad.implantacion(ENTORNO).filter((m) => m.kind === "Job");
    expect(jobs).toHaveLength(1);
    const job = jobs[0]!;
    expect(job.metadata.name).toContain("0eee58e43e04");
    const pod = job.spec.template.spec;
    expect((pod.initContainers ?? []).map((c) => c.image)).toEqual([
      ENTORNO.imagenDe("identidad-migrador"),
    ]);
    const c = pod.containers[0]!;
    expect(c.image).toBe(ENTORNO.imagenDe("identidad"));
    expect(valorDe(c, "SPRING_PROFILES_ACTIVE")).toBe("batch");
    expect(valorDe(c, "KAMAYUK_IMPLANTACION_UBIGEO")).toBe("200105");
    expect(valorDe(c, "KAMAYUK_IMPLANTACION_ESDEMOSTRACION")).toBe("true");
    // `DatosDeImplantacion` valida en su constructor compacto: sin una de estas el bean falla y
    // el contexto no arranca. No es un Job degradado, es un Job que no corre.
    for (const variable of [
      "KAMAYUK_IMPLANTACION_NOMBRE",
      "KAMAYUK_IMPLANTACION_TIPO",
      "KAMAYUK_IMPLANTACION_ADMINISTRADOR",
      "KAMAYUK_IMPLANTACION_NOMBREDELADMINISTRADOR",
      "KAMAYUK_IMPLANTACION_URL",
      "KAMAYUK_IMPLANTACION_OWNERCLAVE",
    ]) {
      expect(declara(c, variable), `falta ${variable}`).toBe(true);
    }
  });

  /**
   * Un `podSelector` sin `namespaceSelector` selecciona pods **del mismo namespace**, y desde
   * ADR-0031 cada sistema tiene el suyo. Una regla escrita asi no abre nada: el sintoma es
   * trafico denegado con una politica que dice permitirlo.
   */
  it("toda regla de egreso nombra el namespace de su destino", () => {
    const destinos = destinosCrudos();
    expect(destinos.length).toBeGreaterThan(0);
    for (const destino of destinos) {
      expect(destino.namespaceSelector, JSON.stringify(destino)).toBeDefined();
    }
  });
});

/** Los contenedores de una lista de manifiestos, los de inicializacion aparte. */
function contenedoresDe(manifiestos: readonly Manifiesto[]) {
  return manifiestos.flatMap((m) =>
    m.kind === "Deployment"
      ? m.spec.template.spec.containers
      : m.kind === "Job"
        ? m.spec.template.spec.containers
        : m.kind === "CronJob"
          ? m.spec.jobTemplate.spec.template.spec.containers
          : [],
  );
}

function valorDe(c: Contenedor, nombre: string): string | undefined {
  return (c.env ?? []).find((e) => e.name === nombre)?.value;
}

function declara(c: Contenedor, nombre: string): boolean {
  return (c.env ?? []).some((e) => e.name === nombre);
}

describe("C-14 §3 — identidad no corre nada de madrugada", () => {
  /**
   * Vacio es una respuesta legitima, y no es lo mismo que un `CronJob` suspendido: lo primero
   * dice «este sistema no corre nada» y lo segundo «corre esto, y hoy no puede» — que es el
   * defecto que #21 encontro en el ingestor de `catastro`, suspendido desde su primer dia.
   *
   * Conceder y revocar son actos de una persona. Lo que la etapa 4 de ADR-0039 traiga tendra que
   * llegar con la guarda que mida que corre (su §«Lo que cuesta», punto 4).
   */
  it("no declara ningun proceso por lotes, y es una afirmacion", () => {
    expect(identidad.lotes(ENTORNO)).toEqual([]);
  });
});

describe("C-17 — que el despliegue pase de verdad", () => {
  /**
   * El anfitrion del motor **se pide**, y este descriptor no escribe ninguno.
   *
   * Es la mutacion que este criterio existe para cazar: hasta C-17 los cuatro descriptores decian
   * `jdbc:postgresql://postgres:5432/...`, y en Kubernetes no hay ningun `Service` llamado
   * `postgres` —ese nombre viene del `compose.yaml` local—. Medido en el cluster:
   * `UnknownHostException` en los ocho Jobs de los cuatro sistemas y en sus `Deployment`.
   */
  it("toda URL de base sale del anfitrion que entrega el entorno, y es la SUYA", () => {
    const urls = contenedoresDe([
      ...identidad.despliegue(ENTORNO),
      ...identidad.migracion(ENTORNO),
      ...identidad.implantacion(ENTORNO),
      ...identidad.lotes(ENTORNO),
    ])
      .flatMap((c) => (c.env ?? []).map((v) => v.value ?? ""))
      .filter((v) => v.startsWith("jdbc:"));

    expect(urls.length, "ninguna variable lleva una URL de base: ¿se dejo de leer?").toBeGreaterThan(0);
    for (const url of urls) {
      expect(url).toBe(`jdbc:postgresql://${ENTORNO.plataforma.motor}/identidad`);
    }
  });

  /**
   * DNS, sin el cual las demas reglas de egreso no sirven de nada.
   *
   * Una politica de egreso convierte a los pods que selecciona en «solo lo declarado», y todo lo
   * que estas reglas nombran —el motor, Keycloak— se alcanza por el nombre de un `Service`.
   * Resolverlo es una consulta a CoreDNS, en `kube-system`. Con la regla anadida a mano sobre el
   * cluster, las ocho tareas de los cuatro sistemas pasaron de `Failed` a `Complete` (C-17,
   * punto 3), y el sintoma es **intermitente** porque la resolucion se cachea.
   */
  it("abre DNS hacia kube-system, en UDP y en TCP", () => {
    const reglas = identidad.egreso(ENTORNO).flatMap((p) => p.spec.egress ?? []);
    const dns = reglas.filter((r) =>
      (r.to ?? []).some(
        (d) => d.namespaceSelector?.matchLabels?.["kubernetes.io/metadata.name"] === "kube-system",
      ),
    );

    expect(dns, "sin DNS ninguna de las demas reglas de egreso puede resolver un nombre").toHaveLength(1);
    expect(
      (dns[0]?.ports ?? []).map((p) => `${p.protocol}/${p.port}`).sort(),
      "TCP tambien: una respuesta que no cabe en un datagrama se reintenta por TCP",
    ).toEqual(["TCP/53", "UDP/53"]);
  });

  /**
   * Las tres sondas piden rutas del grupo `health`, y las tres tienen que estar entre las que
   * `SeguridadWeb` atiende sin token. Esa comparacion la hace `sondas-contra-la-cadena.ts` de
   * `infrastructure`, que tiene los dos clones; aqui se fija la mitad que este archivo decide.
   *
   * Y la de VIDA es la del grupo `liveness` a proposito (C-17, punto 2): apuntarla a
   * `/actuator/health` —el grupo entero, que incluye el indicador de la base— convierte una base
   * caida en un proceso que el kubelet mata en bucle, y matar el proceso no devuelve la base.
   */
  it("las tres sondas piden rutas del grupo de salud, y la de vida es la de `liveness`", () => {
    const web = identidad
      .despliegue(ENTORNO)
      .flatMap((m) => (m.kind === "Deployment" ? m.spec.template.spec.containers : []))
      .find((c) => c.ports !== undefined);
    expect(web, "ningun contenedor del despliegue abre un puerto: no hay sonda que mirar").toBeDefined();
    expect(web?.startupProbe?.httpGet?.path).toBe("/actuator/health");
    expect(web?.readinessProbe?.httpGet?.path).toBe("/actuator/health/readiness");
    expect(web?.livenessProbe?.httpGet?.path).toBe("/actuator/health/liveness");
    // El valor por omision del kubelet es 1 s, y en un nodo ocupado un contenedor sano pero
    // atareado no contesta en 1 s: tres fallos y lo mata con codigo 143, que se parece a un OOM.
    for (const sonda of [web?.startupProbe, web?.readinessProbe, web?.livenessProbe]) {
      expect(sonda?.timeoutSeconds ?? 0).toBeGreaterThanOrEqual(3);
    }
  });

  /**
   * `SPRING_PROFILES_ACTIVE: web` en el `Deployment`, y no `batch` (C-17 §5).
   *
   * Un `Deployment` solo admite `restartPolicy: Always`, asi que Kubernetes no puede distinguir
   * «termino» de «se murio». Medido en el cluster con `kamayuk-rentas-batch`: arranca, sale con
   * **codigo 0** a los once segundos y Kubernetes lo vuelve a crear — `CrashLoopBackOff` con siete
   * reinicios sobre un proceso que hizo exactamente lo que tenia que hacer.
   */
  it("ningun Deployment corre un perfil que termina", () => {
    for (const m of identidad.despliegue(ENTORNO)) {
      if (m.kind !== "Deployment") continue;
      for (const c of m.spec.template.spec.containers) {
        expect(valorDe(c, "SPRING_PROFILES_ACTIVE"), m.metadata.name).toBe("web");
      }
    }
  });

  /**
   * El emisor y el JWKS **se piden**, y no son la misma cadena.
   *
   * El emisor es una IDENTIDAD —es lo que se compara con el `iss` del token, y lo que hace que un
   * token de otro realm no valga— y el JWKS es una DIRECCION DE RED. En un despliegue con
   * contenedores las dos no coinciden, y usar la publica para las dos cosas deja al backend
   * saliendo por el ingreso para volver a entrar: **todo token invalido, por un motivo que no se
   * parece a su causa**.
   */
  it("el emisor y el JWKS salen del entorno y son cosas distintas", () => {
    const web = identidad
      .despliegue(ENTORNO)
      .flatMap((m) => (m.kind === "Deployment" ? m.spec.template.spec.containers : []))[0]!;
    expect(valorDe(web, "KAMAYUK_OIDC_EMISOR")).toBe(ENTORNO.plataforma.emisor);
    expect(valorDe(web, "KAMAYUK_OIDC_JWKS")).toBe(ENTORNO.plataforma.jwks);
    expect(valorDe(web, "KAMAYUK_OIDC_EMISOR")).not.toBe(valorDe(web, "KAMAYUK_OIDC_JWKS"));
  });

  /**
   * El inventario de claves nombra los MISMOS `Secret` que los manifiestos montan (C-17, punto 4).
   *
   * En los cuatro del corte esta lista decia `kamayuk-<sistema>-app` —sin el ambiente— mientras
   * los `secretKeyRef` pedian `kamayuk-<sistema>-<ambiente>-app`: la interseccion entre lo
   * declarado y lo referenciado era **cero**, y el sintoma no es un error sino un pod en `Pending`
   * esperando un `Secret` que nadie genera. Se derivan las dos mitades y se comparan.
   */
  it("todo `secretKeyRef` que se monta esta en el inventario, y al reves", () => {
    const montados = new Set(
      contenedoresDe([
        ...identidad.despliegue(ENTORNO),
        ...identidad.migracion(ENTORNO),
        ...identidad.implantacion(ENTORNO),
      ])
        .flatMap((c) => c.env ?? [])
        .map((v) => v.valueFrom?.secretKeyRef?.name)
        .filter((n): n is string => n !== undefined),
    );
    const declarados = new Set(identidad.claves(ENTORNO).map((c) => c.nombre));
    expect(montados.size, "ningun contenedor monta un `Secret`: esto no mediria nada").toBeGreaterThan(0);
    expect([...montados].sort()).toEqual([...declarados].sort());
  });
});
