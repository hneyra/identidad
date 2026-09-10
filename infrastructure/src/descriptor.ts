/**
 * El descriptor de infraestructura de `identidad` (`ADR-0031` §2, `ADR-0039`).
 *
 * Usuarios, grupos, permisos y accesos: **quien puede hacer que, en que municipalidad**. Es el
 * quinto sistema del producto, y nace de contestar D-19 (`ADR-0039`).
 *
 * ## Que es esto, y por que son funciones puras
 *
 * `infrastructure` lo importa, **fija su version**, lo compone y **lo audita con las mismas
 * reglas que audita los suyos**. Eso solo es posible porque lo que hay aqui son **funciones
 * puras que devuelven objetos planos de Kubernetes**: `infrastructure` recibe datos, puede
 * leerlos y puede negarse a aplicarlos. Si este archivo creara recursos —un `pulumi.Input`, una
 * conexion, una lectura de `process.env`—, la auditoria no tendria nada que leer y la unica
 * garantia seria la confianza en quien lo escribio.
 *
 * ## Lo que este archivo NO puede hacer
 *
 * Cinco cosas, y `infrastructure` las rechaza: una ruta fuera de su prefijo, **la etiqueta de la
 * imagen** —la pone `infrastructure`, o cada liberacion vuelve a ser un `pulumi up`—, privilegios
 * sobre la base de otro sistema, un `Deployment` sin limites ni sondas, y un `Secret` en claro.
 *
 * ## ESTE SISTEMA NO AUTENTICA, Y POR ESO SU EGRESO SE PARECE AL DE `normativa`
 *
 * La **autenticacion** es de Keycloak y ya era una sola para los cuatro (`ADR-0030` §3);
 * `ADR-0039` no la toca. Lo que este sistema es dueno de decidir es la **autorizacion**: a quien
 * se le concede cada opcion del catalogo de cada sistema. Y en la etapa 1 eso todavia no habla
 * con nadie: no llama a ningun sistema hermano, asi que su egreso es DNS, su motor y el emisor
 * —exactamente lo mismo que necesita cualquier backend para validar el token que recibe—.
 *
 * **Y la etapa 4 no la hizo crecer, medido** (`ADR-0039` §Etapas): el buzon existe desde la 3 y
 * los cuatro sistemas son quienes leen de aqui, asi que las aristas nuevas aparecieron en SUS
 * descriptores —cada uno declara su egreso hacia `identidad-sistema`— y no en este. Un egreso
 * vacio hacia los sistemas sigue siendo una afirmacion, y ahora esta contrastada con cuatro
 * descriptores que la cumplen por el otro lado.
 *
 * ## LA COLISION DE NOMBRE CON KEYCLOAK, y por que este archivo no usa `componente: identidad`
 *
 * En la plataforma, `identidad` YA SIGNIFICA KEYCLOAK. Esta escrito en cuatro sitios de
 * `infrastructure` y ninguno se puede cambiar desde aqui:
 *
 *   - `infra/componentes/Identidad.ts` etiqueta el `Deployment` de Keycloak con
 *     `commonLabels(environment, "identidad")`, o sea `componente: identidad`;
 *   - `infra/componentes/convenciones.ts` (`servicioDeIdentidad`) e `infra/componentes/Ingreso.ts`
 *     componen su nombre de recurso, `kamayuk-<ambiente>-identidad`;
 *   - `infra/componentes/Red.ts` deja salir hacia esos pods con `permitir-salida-identidad`;
 *   - y —lo que de verdad muerde— `infra/descriptor/sistemas.ts:35` declara
 *     `const infraestructura = ["postgres", "identidad"]` y **descarta de `grafoDeEgreso` toda
 *     arista cuyo `podSelector` lleve esa etiqueta**.
 *
 * Ese ultimo es el que decide el valor de aqui. El grafo de egreso **ES** el grafo de
 * dependencias del producto (`ADR-0029`), y filtra por el NOMBRE de la etiqueta y no por el
 * namespace de destino. Con `componente: identidad` en los pods de este sistema, las cuatro
 * aristas que la etapa 4 hizo declarar a los cuatro hacia aqui **se filtrarian
 * como si fueran infraestructura**: el grafo diria que nadie llama a `identidad` mientras los
 * cuatro lo llaman. No es un error que se vea; es un grafo que miente, que es peor.
 *
 * Asi que los pods de este sistema llevan **`componente: identidad-sistema`** (AC-7). La etiqueta
 * `sistema: identidad` la pone `infrastructure` en `e.etiquetas` y no se toca: es la que dice de
 * quien es el pod, y es la que `grafoDeEgreso` deberia mirar.
 *
 * **La otra mitad no es de este repositorio y se dice en vez de suponerse**: que `grafoDeEgreso`
 * distinga la plataforma por el NAMESPACE de destino y no por el nombre de la etiqueta es un
 * cambio en `infrastructure` (AC-7 del issue #1, y su PR hermano). Mientras no llegue, este valor
 * es lo unico que impide que este sistema desaparezca del grafo — y por eso `descriptor.test.ts`
 * lo fija con una prueba que explica la colision, en vez de dejarlo como una preferencia de
 * nombres que alguien "normalice" al copiar de un hermano.
 *
 * **Y hay una tercera cara de la misma colision, en el compose**: ver la cabecera de
 * `despliegue/compose.yaml`.
 *
 * ## Todavia no hay codigo de negocio
 *
 * Los `Deployment` apuntan a imagenes que aun no existen el dia que esto se escribe. Es correcto
 * en esta etapa: describe como se desplegaria este sistema, y no se despliega nada.
 */

import type {
  BaseDeDatosDeclarada,
  ClaveDeclarada,
  Contenedor,
  DescriptorDeSistema,
  EntornoDelDescriptor,
  Manifiesto,
  NetworkPolicy,
  PanelDeclarado,
  ReglaDeAlerta,
  VariableDeEntorno,
} from "@kamayuk/infra-contrato";

const SISTEMA = "identidad";

/** La imagen del migrador: el otro objetivo del mismo `Dockerfile` (C-14, punto 1). */
const MIGRADOR = `${SISTEMA}-migrador`;

/**
 * La etiqueta `componente` de los pods de ESTE sistema. **No es `identidad`**, y el motivo entero
 * esta en la cabecera: `infra/descriptor/sistemas.ts:35` descarta del grafo de egreso toda arista
 * cuyo `podSelector` lleve `componente: identidad`, porque ahi eso significa Keycloak.
 *
 * Con el nombre colisionando, las cuatro aristas que la etapa 4 de `ADR-0039` va a traer
 * —`rentas`, `catastro`, `normativa` y `caja` leyendo de aqui— se filtrarian como
 * infraestructura, y el grafo diria que a este sistema no lo llama nadie.
 *
 * Es el mismo recurso que `rentas` usa para su interfaz (`componente: rentas-interfaz`): la
 * etiqueta separa procesos que no comparten politica de red, y aqui ademas separa este sistema
 * del servidor de autenticacion que ya se llamaba asi.
 */
const COMPONENTE = `${SISTEMA}-sistema`;

/** El componente con que la PLATAFORMA etiqueta a Keycloak. Aqui solo se nombra como DESTINO. */
const KEYCLOAK = "identidad";

/**
 * Su base, en el motor de la plataforma. Una por sistema (ADR-0029, ADR-0032).
 *
 * **El anfitrion lo pide, no lo escribe** (C-17, punto 1). Escribir
 * `jdbc:postgresql://postgres:5432/...` es lo que hicieron los cuatro descriptores del corte, y
 * en Kubernetes **no hay ningun `Service` llamado `postgres`**: ese nombre viene del
 * `compose.yaml` local. El servicio real es `kamayuk-<ambiente>-postgres` y vive en el namespace
 * de la PLATAFORMA, asi que ni siquiera un nombre corto correcto resolveria desde aqui. Lo medido
 * entonces fue `UnknownHostException` en los ocho Jobs y en los `Deployment` de los cuatro: nada
 * del producto podia arrancar.
 *
 * Componerlo aqui seria repetir dos convenciones que son de `infrastructure` —como se nombra un
 * recurso del ambiente y como se llama su namespace—, y dos copias de una convencion se separan.
 * Lo que si es de este sistema, y por eso se escribe aqui, es el nombre de su base.
 */
function urlDeLaBase(e: EntornoDelDescriptor): string {
  return `jdbc:postgresql://${e.plataforma.motor}/${SISTEMA}`;
}

/**
 * Lo que piden los Jobs de un solo uso —migrar e implantar—.
 *
 * Mismos `limits` que el perfil web y `requests` mas bajos, que es el reparto que
 * `RECURSOS.arranque` del monolito documenta desde el 2026-08-26: el `request` es lo que el
 * planificador **reserva y bloquea**, y estos Jobs corren a la vez que todos los `Deployment`
 * durante un `pulumi up`. Con el nodo justo, un `request` alto no es lentitud: es que no entran,
 * y como llevan la clase `lote` —la mas baja del cluster— no pueden desalojar a nadie para
 * hacerlo. Nadie cede y el despliegue se cuelga (`capacidad.ts`, issue #252).
 *
 * Y aqui importa mas que en los otros cuatro por una razon medida: **el nodo de `prod` ya no
 * cabe** (#1, D-25), asi que este sistema entra empeorando una brecha que ya estaba declarada.
 */
const RECURSOS_DE_ARRANQUE = {
  requests: { cpu: "50m", memory: "128Mi" },
  limits: { cpu: "1", memory: "1Gi" },
};

/**
 * Lo que pide y lo que puede gastar. Sin esto, el planificador no reserva nada.
 *
 * ## Por que pide la MITAD que los otros cuatro (identidad#1 AC-8)
 *
 * Los cuatro sistemas piden 512Mi para el proceso web y 256Mi por Job, y con eso este sistema
 * anadia 1 024Mi al pico de arranque de cada ambiente. Medido en el PR hermano de
 * `infrastructure` (#54): `stg` cabia con **96Mi** de margen y con el quinto sistema deja de
 * caber por 928Mi. La direccion decidio bajar la demanda de ESTE sistema y no la de los otros
 * cuatro: `identidad` guarda ocho tablas de autorizacion y no calcula nada —ni un padron, ni
 * una geometria, ni una determinacion—, asi que es el que menos pierde con un `request` bajo.
 *
 * Lo que se baja es solo el `request` —lo que el planificador RESERVA—; los `limits` no se
 * tocan, asi que el proceso puede seguir usando hasta 1Gi si el nodo lo tiene libre. Lo que
 * cuesta, dicho: con 256Mi reservados y un nodo apretado, este pod es el primero al que le
 * falta memoria bajo carga. Es una cifra sin medir contra el proceso de verdad; el dia que se
 * mida, se sustituye.
 *
 * Y NO cierra el hueco de `stg` por si sola: los 512Mi que ahorra (256 del web y 128 por
 * cada uno de los dos Jobs) dejan 416Mi por cubrir, que es lo que el nodo real de `stg` tiene
 * que aportar cuando se mida (su `Pulumi.stg.yaml` declara una cota inferior sin medir).
 */
const RECURSOS = {
  requests: { cpu: "100m", memory: "256Mi" },
  limits: { cpu: "1", memory: "1Gi" },
};

/** El endurecimiento que no admite excepcion (issue #157). */
const SEGURIDAD = {
  runAsNonRoot: true,
  allowPrivilegeEscalation: false as const,
  capabilities: { drop: ["ALL"] as ["ALL"] },
};

/** La conexion de la aplicacion: `kamayuk_app` y solo `kamayuk_app` (ARQ-03 §4). */
function credencialesDeLaAplicacion(e: EntornoDelDescriptor): VariableDeEntorno[] {
  return [
    { name: "KAMAYUK_DB_URL", value: urlDeLaBase(e) },
    { name: "KAMAYUK_DB_USUARIO", value: "kamayuk_app" },
    {
      name: "KAMAYUK_DB_CLAVE",
      valueFrom: { secretKeyRef: { name: e.secretoDe("app"), key: "clave" } },
    },
  ];
}

/**
 * El contenedor del migrador: **la imagen del migrador, no la de la aplicacion** (C-14, punto 1).
 *
 * Lee `KAMAYUK_DB_OWNER_USUARIO` y `KAMAYUK_DB_OWNER_CLAVE` —lo dice el `main` de
 * `kamayuk.identidad.esquema.Migrador`, que rechaza argumentos a proposito para que una clave no
 * quede en el historial del proceso—, y **no** `KAMAYUK_DB_USUARIO`, que es lo que los cuatro
 * descriptores del corte ponian sobre la imagen de la aplicacion: aquello arrancaba el proceso web
 * con las credenciales de `kamayuk_owner` y con `spring.flyway.enabled: false`, o sea DDL al
 * alcance de un servidor HTTP y ninguna migracion aplicada.
 */
function contenedorDelMigrador(e: EntornoDelDescriptor): Contenedor {
  return {
    name: "migrador",
    image: e.imagenDe(MIGRADOR),
    env: [
      { name: "KAMAYUK_DB_URL", value: urlDeLaBase(e) },
      // Migrar es lo unico que corre como `kamayuk_owner`: es el unico rol con DDL.
      { name: "KAMAYUK_DB_OWNER_USUARIO", value: "kamayuk_owner" },
      {
        name: "KAMAYUK_DB_OWNER_CLAVE",
        valueFrom: { secretKeyRef: { name: e.secretoDe("owner"), key: "clave" } },
      },
    ],
    resources: RECURSOS_DE_ARRANQUE,
    securityContext: SEGURIDAD,
  };
}

/**
 * Las propiedades de `DatosDeImplantacion`, tal como Spring las lee del entorno.
 *
 * El prefijo es `kamayuk.implantacion`, y **no se elige aqui**: lo declara el
 * `@ConfigurationProperties` del Java de este sistema, y `prefijo-de-la-implantacion.ts` de
 * `infrastructure` compara las dos mitades. Es la guarda que C-18 escribio despues de medir lo que
 * cuesta que no cuadren: `ImplantarMunicipalidad` esta condicionado a
 * `@ConditionalOnProperty("<prefijo>.ubigeo")`, asi que con el prefijo ajeno el runner **ni
 * siquiera se registra** — el proceso arranca, no hace nada y sale con codigo 0, y el `Job` queda
 * `Complete` sin haber implantado nada.
 */
function variablesDeImplantacion(e: EntornoDelDescriptor): VariableDeEntorno[] {
  const i = e.implantacion;
  return [
    { name: "SPRING_PROFILES_ACTIVE", value: "batch" },
    ...credencialesDeLaAplicacion(e),
    { name: "KAMAYUK_IMPLANTACION_UBIGEO", value: i.ubigeo },
    { name: "KAMAYUK_IMPLANTACION_NOMBRE", value: i.nombre },
    { name: "KAMAYUK_IMPLANTACION_TIPO", value: i.tipo },
    // No crea ninguna contrasena: la credencial vive en Keycloak, y esta cuenta tiene que ser
    // la misma que exista alli.
    { name: "KAMAYUK_IMPLANTACION_ADMINISTRADOR", value: i.administrador },
    { name: "KAMAYUK_IMPLANTACION_NOMBREDELADMINISTRADOR", value: i.nombreDelAdministrador },
    { name: "KAMAYUK_IMPLANTACION_ESDEMOSTRACION", value: String(i.esDemostracion) },
    { name: "KAMAYUK_IMPLANTACION_URL", value: urlDeLaBase(e) },
    // OWNERCLAVE sin guion bajo: en una variable de entorno el `_` se traduce a punto, asi que
    // `KAMAYUK_IMPLANTACION_OWNER_CLAVE` seria `kamayuk.implantacion.owner.clave` y no
    // `owner-clave`. Es la misma nota que llevan los cuatro hermanos, y por el mismo motivo.
    {
      name: "KAMAYUK_IMPLANTACION_OWNERCLAVE",
      valueFrom: { secretKeyRef: { name: e.secretoDe("owner"), key: "clave" } },
    },
  ];
}

/**
 * `timeoutSeconds` entre 3 y 5, y no es decorativo: el valor por omision del kubelet es **1 s**,
 * y en un nodo ocupado un contenedor sano pero atareado no contesta en 1 s. Tres fallos de la
 * sonda de vida y lo mata con codigo 143, que se parece a un OOM sin serlo.
 *
 * Las tres rutas tienen que estar entre las que `SeguridadWeb` permite sin token, y eso lo
 * comprueba `sondas-contra-la-cadena.ts` de `infrastructure` **leyendo el Java**. Con la cadena
 * anterior a C-17 los cuatro pods arrancaban, conectaban a la base y el kubelet los mataba a los
 * ~45 s: `CrashLoopBackOff` para siempre, con la aplicacion sana y sin un error en su registro.
 */
function sondas() {
  return {
    startupProbe: {
      timeoutSeconds: 3,
      httpGet: { path: "/actuator/health", port: 8080 },
      failureThreshold: 30,
      periodSeconds: 5,
    },
    readinessProbe: {
      timeoutSeconds: 3,
      httpGet: { path: "/actuator/health/readiness", port: 8080 },
      periodSeconds: 10,
    },
    livenessProbe: {
      timeoutSeconds: 5,
      httpGet: { path: "/actuator/health/liveness", port: 8080 },
      periodSeconds: 20,
    },
  };
}

function despliegueDelPerfil(
  e: EntornoDelDescriptor,
  perfil: string,
  atiendeHttp: boolean,
): Manifiesto[] {
  const nombre = `kamayuk-${SISTEMA}-${perfil}`;
  // `componente: identidad-sistema`, y no `identidad`: ver la cabecera y `COMPONENTE`.
  const etiquetas = { ...e.etiquetas, componente: COMPONENTE, perfil };
  const manifiestos: Manifiesto[] = [
    {
      apiVersion: "apps/v1",
      kind: "Deployment",
      metadata: { name: nombre, namespace: e.namespace, labels: etiquetas },
      spec: {
        replicas: 1,
        // `maxSurge: 0` obliga a matar el pod viejo antes de crear el nuevo: en un nodo sin
        // holgura, un pod extra durante el despliegue no agenda y el rollout se cuelga.
        strategy: { type: "RollingUpdate", rollingUpdate: { maxSurge: 0, maxUnavailable: 1 } },
        selector: { matchLabels: { app: nombre } },
        template: {
          metadata: { labels: { ...etiquetas, app: nombre } },
          spec: {
            priorityClassName: e.prioridadDe(perfil === "batch" ? "lote" : "servicio"),
            containers: [
              {
                name: SISTEMA,
                // La etiqueta la pone `infrastructure`. Ver la cabecera.
                image: e.imagenDe(SISTEMA),
                env: [
                  { name: "SPRING_PROFILES_ACTIVE", value: perfil },
                  ...credencialesDeLaAplicacion(e),
                  // Sin el emisor la aplicacion se niega a arrancar, y es deliberado: un backend
                  // que atiende sin poder validar un token responde a la sonda, se declara sano y
                  // no atiende a nadie (ADR-0005). Aqui pesa doble: este es el sistema que decide
                  // quien puede hacer que, asi que atender sin poder validar un token seria
                  // servir autorizacion a nombre de nadie.
                  { name: "KAMAYUK_OIDC_EMISOR", value: e.plataforma.emisor },
                  // El JWKS por la red INTERNA, cruzando el namespace de la plataforma (C-14).
                  // El emisor es una IDENTIDAD —es lo que se compara con el `iss`— y el JWKS es
                  // una DIRECCION DE RED: en un despliegue con contenedores las dos no coinciden.
                  // Apuntar las dos al nombre publico deja al backend saliendo por el ingreso para
                  // volver a entrar, y con la politica de egreso declarada —que nombra el pod de
                  // Keycloak, no internet— no saldria en absoluto: todo token invalido, por un
                  // motivo que no se parece a su causa.
                  { name: "KAMAYUK_OIDC_JWKS", value: e.plataforma.jwks },
                ],
                ...(atiendeHttp ? { ports: [{ name: "http", containerPort: 8080 }] } : {}),
                resources: RECURSOS,
                ...(atiendeHttp ? sondas() : {}),
                securityContext: SEGURIDAD,
              },
            ],
          },
        },
      },
    },
  ];
  if (atiendeHttp) {
    manifiestos.push({
      apiVersion: "v1",
      kind: "Service",
      metadata: { name: nombre, namespace: e.namespace, labels: etiquetas },
      spec: {
        type: "ClusterIP",
        selector: { app: nombre },
        ports: [{ name: "http", port: 80, targetPort: 8080 }],
      },
    });
  }
  return manifiestos;
}

export const identidad: DescriptorDeSistema = {
  sistema: SISTEMA,
  prefijo: SISTEMA,
  // DOS imagenes, y son dos objetivos del mismo `Dockerfile` (C-14, punto 1): las
  // credenciales de `kamayuk_owner` existen durante la migracion y desaparecen con ella.
  imagenes: [SISTEMA, MIGRADOR],

  /**
   * Su base y sus roles. **Solo la suya**: pedir privilegios sobre la de otro sistema es una
   * base compartida disfrazada, y deja el aislamiento entre municipalidades en una promesa.
   *
   * `superusuario: false` no es una formalidad: un superusuario OMITE RLS incluso con
   * `FORCE ROW LEVEL SECURITY` (DAT-01 §0, hallazgo 1). Y aqui eso significaria que un proceso
   * pudiera leer los permisos de todas las municipalidades a la vez.
   *
   * **`kamayuk_app` no recibe `DELETE`, y en este sistema es la regla 4 en su forma mas literal**:
   * un acceso no se borra, se revoca — si se borrara, la auditoria no podria decir quien lo tuvo.
   */
  baseDeDatos(): BaseDeDatosDeclarada {
    return {
      nombre: SISTEMA,
      roles: [
        { nombre: "kamayuk_owner", sobre: [SISTEMA], privilegios: ["ALL"], superusuario: false },
        {
          nombre: "kamayuk_app",
          sobre: [SISTEMA],
          privilegios: ["SELECT", "INSERT", "UPDATE"],
          superusuario: false,
        },
        {
          nombre: "kamayuk_readonly",
          sobre: [SISTEMA],
          privilegios: ["SELECT"],
          superusuario: false,
        },
      ],
    };
  },

  despliegue: (e) => [...despliegueDelPerfil(e, "web", true)],

  /**
   * Su Job de migracion. Cada base tiene sus migraciones y su prueba de aislamiento.
   *
   * **El nombre lleva la version**, y no es cosmetico: un `Job` de Kubernetes es INMUTABLE —su
   * plantilla de pod no se puede modificar—, asi que un nombre fijo hace fallar el `pulumi up` de
   * la version siguiente al intentar actualizarlo, porque la imagen lleva la etiqueta dentro. El
   * monolito lo resolvio asi desde el issue #150; los cuatro descriptores del corte nacieron sin
   * ello y hubo que anadirselo en C-14.
   */
  migracion(e): Manifiesto[] {
    const nombre = e.nombreConVersion(`kamayuk-${SISTEMA}-migracion`);
    const etiquetas = { ...e.etiquetas, componente: COMPONENTE };
    return [
      {
        apiVersion: "batch/v1",
        kind: "Job",
        metadata: { name: nombre, namespace: e.namespace, labels: etiquetas },
        spec: {
          backoffLimit: 3,
          ttlSecondsAfterFinished: 86400,
          template: {
            metadata: { labels: { ...etiquetas, app: nombre } },
            spec: {
              restartPolicy: "Never",
              priorityClassName: e.prioridadDe("lote"),
              containers: [contenedorDelMigrador(e)],
            },
          },
        },
      },
    ];
  },

  /**
   * Su Job de implantacion: la fila de `municipalidad` en SU base, y la copia local de usuarios,
   * grupos y accesos (C-7 §2.3, C-14 punto 4).
   *
   * ## Por que el migrador va de contenedor de inicializacion
   *
   * Un `Deployment` no sabe esperar a un `Job` y Kubernetes no tiene `dependsOn`. El monolito lo
   * resuelve con un contenedor que consulta la base con `psql`; aqui esa salida no existe, porque
   * un descriptor solo puede nombrar SUS imagenes —la prohibicion (b)— y la del motor no es suya.
   *
   * Lo que se hace es mas fuerte que esperar: se **asegura** que el esquema esta, corriendo el
   * migrador, que es idempotente y devuelve cero cuando no falta nada. Si el Job de migracion aun
   * no termino, Flyway toma su propio candado y uno de los dos espera al otro; cuando este
   * contenedor sale con exito **el esquema ESTA**, que es lo que la espera del monolito solo
   * puede suponer.
   *
   * **La espera al MOTOR va delante de este, y no la pone este archivo**: la inyecta
   * `infrastructure` (`componentes/espera-al-motor.ts`, #44) como primer `initContainer`. El orden
   * importa y no es intercambiable — el migrador es quien abre la conexion, asi que una espera
   * puesta detras no protegeria a quien falla primero; en `caja` fue justo ese, y su Job murio con
   * `Connection refused` sin ejecutar una sentencia.
   */
  implantacion(e): Manifiesto[] {
    const nombre = e.nombreConVersion(`kamayuk-${SISTEMA}-implantacion`);
    const etiquetas = { ...e.etiquetas, componente: COMPONENTE };
    return [
      {
        apiVersion: "batch/v1",
        kind: "Job",
        metadata: { name: nombre, namespace: e.namespace, labels: etiquetas },
        spec: {
          backoffLimit: 3,
          ttlSecondsAfterFinished: 86400,
          template: {
            metadata: { labels: { ...etiquetas, app: nombre } },
            spec: {
              restartPolicy: "Never",
              priorityClassName: e.prioridadDe("lote"),
              initContainers: [contenedorDelMigrador(e)],
              containers: [
                {
                  name: "implantacion",
                  // La MISMA imagen que la aplicacion, con el perfil `batch` (ADR-0003: un
                  // artefacto, dos perfiles). No abre puerto ninguno.
                  image: e.imagenDe(SISTEMA),
                  env: variablesDeImplantacion(e),
                  resources: RECURSOS_DE_ARRANQUE,
                  securityContext: SEGURIDAD,
                },
              ],
            },
          },
        },
      },
    ];
  },

  /**
   * Sus procesos por lotes con ventana. **Ninguno, y es una afirmacion, no una casilla.**
   *
   * En la etapa 1 este sistema no corre nada de madrugada: no publica, no ingesta y no entrega.
   * Conceder y revocar son actos de una persona, no tareas programadas.
   *
   * **Y la etapa 4 tampoco trajo ninguno aqui**: `ADR-0039` decide que la replica viaja por el
   * buzon de `ADR-0028` §3, y quien lo consume es cada sistema — el `CronJob` del consumidor vive
   * en el descriptor de cada uno de los cuatro, no en este. Si algun
   * dia hace falta un proceso periodico aqui, lo que hay que anadir con el es la guarda que mida
   * que corre — el punto 4 de «Lo que cuesta» de `ADR-0039`, que es literalmente el defecto que
   * #21 encontro en el ingestor de `catastro`, suspendido desde su primer dia.
   *
   * Una lista vacia no es lo mismo que un `CronJob` suspendido: lo primero dice «este sistema no
   * corre nada de madrugada» y lo segundo «corre esto, y hoy no puede».
   */
  lotes: (): Manifiesto[] => [],

  /** Sus rutas, **bajo su prefijo**. Reclamar el de otro no falla: se lo queda. */
  ingreso(e): Manifiesto[] {
    return [
      {
        apiVersion: "traefik.io/v1alpha1",
        kind: "IngressRoute",
        metadata: { name: `kamayuk-${SISTEMA}`, namespace: e.namespace, labels: e.etiquetas },
        spec: {
          // Solo `websecure`: 80 redirige, no coexiste. Un formulario de acceso servido por
          // HTTP es una credencial regalada.
          entryPoints: ["websecure"],
          routes: [
            {
              match: `Host(\`${e.dominio}\`) && PathPrefix(\`/${SISTEMA}\`)`,
              kind: "Rule",
              services: [{ name: `kamayuk-${SISTEMA}-web`, port: 80 }],
            },
          ],
          tls: { certResolver: "letsencrypt" },
        },
      },
    ];
  },

  /**
   * A quien puede llamar. **El egreso declarado ES el grafo de dependencias** (ADR-0029). Cada
   * arista, con su motivo:
   *
   * - **DNS**, sin el cual las otras dos no resuelven ningun nombre;
   * - **su motor**, en el namespace de la plataforma;
   * - **Keycloak**, para traerse el JWKS con que valida los tokens que recibe;
   * - **ningun sistema hermano.** Ver la cabecera: es una afirmacion, y la etapa 4 de
   *   `ADR-0039` no la cambio —las aristas nuevas aparecieron en los descriptores de los CUATRO,
   *   que son quienes leen de aqui—.
   */
  egreso(e): NetworkPolicy[] {
    return [
      {
        apiVersion: "networking.k8s.io/v1",
        kind: "NetworkPolicy",
        metadata: {
          name: `kamayuk-${SISTEMA}-egreso`,
          namespace: e.namespace,
          labels: e.etiquetas,
        },
        spec: {
          // Los pods de ESTE sistema, que llevan `identidad-sistema` y no `identidad`. Con la
          // etiqueta colisionando, esta politica seguiria funcionando —el `podSelector` de una
          // NetworkPolicy solo mira su propio namespace, y Keycloak vive en el de la
          // plataforma—; lo que se rompe es el grafo. Ver la cabecera.
          podSelector: { matchLabels: { componente: COMPONENTE } },
          policyTypes: ["Egress"],
          egress: [
            // ── DNS, y va primero porque todo lo demas depende de el ──────────────────
            //
            // Sin esta regla las dos que siguen NO SIRVEN DE NADA. Una politica de egreso
            // convierte a los pods que selecciona en «solo lo declarado», y el motor y Keycloak
            // se nombran por su `Service`: resolver ese nombre es una consulta a CoreDNS, que
            // vive en `kube-system`, y ninguna de las reglas de abajo la permite. El sintoma
            // medido es `UnknownHostException`, y es **intermitente** —la resolucion se cachea,
            // asi que a veces sale y a veces no—, que es peor que fallar siempre.
            //
            // Con esta regla anadida a mano sobre el cluster, las OCHO tareas de los cuatro
            // sistemas pasaron de `Failed` a `Complete` (C-17, punto 3). Los cuatro descriptores
            // se escribieron de cero y esa parte no se copio; aqui se escribe desde el principio.
            //
            // Sin `podSelector` en el destino, a proposito: lo que se abre es el PUERTO 53 hacia
            // el namespace, no un pod concreto. Nombrar `k8s-app: kube-dns` ataria esta politica
            // a como etiqueta sus pods una distribucion de Kubernetes.
            {
              to: [
                {
                  namespaceSelector: {
                    matchLabels: { "kubernetes.io/metadata.name": "kube-system" },
                  },
                },
              ],
              ports: [
                { protocol: "UDP", port: 53 },
                // TCP tambien: una respuesta que no cabe en un datagrama se reintenta por TCP,
                // y una politica que solo abriera UDP funcionaria hasta el dia que dejara de
                // hacerlo, por el tamano de una respuesta.
                { protocol: "TCP", port: 53 },
              ],
            },
            // Su motor. Los cinco lo necesitan; cada uno a SU base.
            {
              to: [
                {
                  // El `namespaceSelector` NO es un adorno: desde ADR-0031 cada sistema tiene su
                  // namespace, y un `podSelector` a secas selecciona pods del MISMO. Sin el, esta
                  // regla no abre nada y el sintoma es trafico denegado con una politica que dice
                  // permitirlo (C-14, punto 3).
                  namespaceSelector: {
                    matchLabels: { "kubernetes.io/metadata.name": e.plataforma.namespace },
                  },
                  podSelector: { matchLabels: { componente: "postgres" } },
                },
              ],
              ports: [{ protocol: "TCP", port: 5432 }],
            },
            // Keycloak: valida los tokens que recibe, igual que los otros cuatro. Este sistema
            // decide la AUTORIZACION; la AUTENTICACION sigue siendo de Keycloak (ADR-0039 §«No
            // toca»), asi que esta arista existe por el mismo motivo que en los otros cuatro y no
            // por ser el sistema de identidad.
            //
            // `componente: identidad` aqui es KEYCLOAK, no este sistema. Es el unico sitio de
            // este archivo donde esa cadena es correcta, y por eso va por la constante.
            {
              to: [
                {
                  namespaceSelector: {
                    matchLabels: { "kubernetes.io/metadata.name": e.plataforma.namespace },
                  },
                  podSelector: { matchLabels: { componente: KEYCLOAK } },
                },
              ],
              ports: [{ protocol: "TCP", port: 8080 }],
            },
            // Y ningun sistema hermano. Ver la cabecera: es una afirmacion.
          ],
        },
      },
    ];
  },

  alertas: (): ReglaDeAlerta[] => [
    {
      alert: `${SISTEMA}SinResponder`,
      expr: `up{job="kamayuk-${SISTEMA}"} == 0`,
      for: "5m",
      labels: { severity: "critical", sistema: SISTEMA },
      annotations: {
        summary: `${SISTEMA} lleva 5 minutos sin responder`,
        description:
          "Con un solo nodo no hay a donde mover la carga: hay que mirar el pod. Cada sistema " +
          "autoriza contra SU copia local (`ADR-0039`), asi que esto no deja a nadie sin poder " +
          "trabajar: deja de poderse conceder y revocar, y los cuatro consumidores del buzon " +
          "acumulan retraso hasta que vuelva.",
      },
    },
  ],

  panel: (): PanelDeclarado => ({
    nombre: `kamayuk-${SISTEMA}`,
    // Vacio a proposito: un panel se llena con las metricas que el sistema publica, y todavia
    // no publica ninguna. Inventarle paneles ahora seria dibujar cifras que nadie emite.
    json: { title: `Kamayuk · ${SISTEMA}`, panels: [] },
  }),

  /**
   * Su inventario de claves: metadatos, **nunca un valor** (INF-06, ADR-0011 §3).
   *
   * **El nombre sale de `e.secretoDe(...)`, el mismo que usan los manifiestos** (C-17, punto 4).
   * En los cuatro del corte esta lista decia `kamayuk-<sistema>-app` —sin el ambiente— mientras
   * los `secretKeyRef` pedian `kamayuk-<sistema>-<ambiente>-app`: el inventario nombraba un
   * `Secret` que nadie monta, y los que se montan no estaban en ningun inventario. La interseccion
   * entre lo declarado y lo referenciado era **cero**, y el sintoma no es un error sino un pod en
   * `Pending` esperando un `Secret` que nadie genera.
   *
   * **Dos, y ninguna con `emisor: "keycloak"`.** Este sistema no pide ningun token a nadie: no
   * llama a ningun hermano (ver el egreso), asi que no tiene cliente confidencial. La distincion
   * existe desde #21 porque los dos casos son indistinguibles hasta que se despliega —los dos son
   * un `secretKeyRef` a una cadena de 32 bytes— y declararla aqui sin consumidor pediria un
   * cliente de servicio que nadie usaria.
   */
  claves: (e): ClaveDeclarada[] => [
    {
      nombre: e.secretoDe("app"),
      clave: "clave",
      rol: "kamayuk_app",
      rotacion: "trimestral",
      proposito: `la conexion de ${SISTEMA} a su base`,
    },
    {
      nombre: e.secretoDe("owner"),
      clave: "clave",
      rol: "kamayuk_owner",
      rotacion: "anual",
      proposito: `migrar la base de ${SISTEMA}; es el unico rol con DDL`,
    },
  ],
};

export default identidad;
