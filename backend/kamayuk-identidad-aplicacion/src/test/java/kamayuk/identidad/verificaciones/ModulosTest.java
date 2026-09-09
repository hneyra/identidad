package kamayuk.identidad.verificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kamayuk.identidad.SgtmAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Limites entre modulos (ADR-0003, ARQ-01 §4). Bloqueante.
 *
 * <p>Sin esto, "monolito modular" degrada a monolito en pocos meses: nada impide que un contexto
 * llame a las clases internas de otro, y cuando se nota ya hay cincuenta llamadas que desenredar.
 */
@DisplayName("ADR-0003 — Limites entre modulos")
class ModulosTest {

    private static final ApplicationModules MODULOS = ApplicationModules.of(SgtmAplicacion.class);

    @Test
    @DisplayName("los modulos esperados estan detectados")
    void losModulosEsperadosEstanDetectados() {
        List<String> detectados =
                MODULOS.stream().map(m -> m.getIdentifier().toString()).sorted().toList();

        // Si Modulith no detectara ningun modulo, verify() pasaria sin comprobar nada.
        //
        // `nucleo` ESTA en la lista aunque hoy solo tenga su `package-info.java`, y conviene
        // decirlo porque contradice lo que el comentario heredado del SRTM afirmaba —«un paquete
        // con solo package-info.java NO es un modulo para Modulith, hace falta al menos un tipo»—.
        // MEDIDO en la etapa 1 de este repositorio, con Spring Modulith 2: se detecta igual, y la
        // primera version de esta prueba —escrita creyendo lo contrario— salio roja diciendo
        // «Expecting ... not to contain ["nucleo"] but found ["nucleo"]». Se corrige la lista y no
        // la medida.
        assertThat(detectados)
                .as("los modulos que ya tienen codigo")
                .contains(
                        "dominio",
                        "compartido",
                        "plataforma",
                        "persistencia",
                        "auditoria",
                        "autorizacion",
                        // La copia local de usuarios, grupos y permisos (C-7, D-N5): el
                        // `ComprobadorDeAcceso` que el guardia pide y la implantacion que la
                        // siembra. Sin el, el contexto no levanta.
                        "seguridad",
                        "carga",
                        "documentos",
                        "web",
                        // El unico contexto acotado de este sistema (ARQ-01 §3.4), y hoy vacio: la
                        // etapa 1 no trae negocio. Que este declarado desde ya es lo que hace que
                        // su primera clase nazca con los limites de Modulith aplicados, en vez de
                        // llegar con doscientas dentro.
                        "nucleo");
    }

    @Test
    @DisplayName("no hay dependencias no declaradas ni ciclos entre modulos")
    void noHayDependenciasNoDeclaradasNiCiclos() {
        MODULOS.verify();
    }
}
