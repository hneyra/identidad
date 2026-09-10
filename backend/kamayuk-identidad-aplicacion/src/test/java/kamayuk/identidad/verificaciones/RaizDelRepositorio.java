package kamayuk.identidad.verificaciones;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Donde esta el repositorio, para las pruebas que leen fuera del build de Gradle.
 *
 * <p>Existe porque en los otros cuatro sistemas ya lo buscaban varias pruebas por su cuenta —el
 * contrato del consumidor y las formas de la API—: tres recorridos escritos por separado empiezan
 * iguales y acaban discrepando en el caso raro, y entonces una prueba lee un archivo y otra lee
 * otro.
 *
 * <p><b>En la etapa 1 no lo consumia nadie</b>, y se decia en vez de dejarlo parecer usado: este
 * repositorio no publicaba ninguna operacion. Desde la etapa 3 lo consume {@link
 * ContratosDeLosConsumidoresTest}, que busca a los clones hermanos desde aqui y lee las cuatro
 * pruebas de contrato por su ruta. Sigue sin haber `formas-de-la-api.json` que comparar: ese
 * archivo solo existe en {@code rentas}, que lo deriva de su contrato OpenAPI.
 *
 * <p><b>Se busca `backend/settings.gradle.kts` y no un `.git` ni un `README.md`</b>, y las tres
 * decisiones estan medidas. `.git` <b>es un archivo</b> en un {@code git worktree} —de ahi el
 * {@code Files.exists} y no el {@code Files.isDirectory} que dejo esto inejecutable tres veces— y
 * ademas no existe en un checkout de CI que use {@code sparse-checkout}; un {@code README.md}
 * casaria primero con el de {@code backend/}, que es un ancestro del directorio de trabajo, y
 * devolveria {@code backend/} creyendo que es la raiz. {@code backend/settings.gradle.kts} solo
 * resuelve desde la raiz de verdad.
 */
final class RaizDelRepositorio {

    private RaizDelRepositorio() {}

    static Path ruta() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null) {
            if (Files.exists(actual.resolve("backend/settings.gradle.kts"))) {
                return actual;
            }
            actual = actual.getParent();
        }
        throw new IllegalStateException(
                "No se encontro la raiz del repositorio subiendo desde "
                        + Path.of("").toAbsolutePath()
                        + ": ningun ancestro tiene `backend/settings.gradle.kts`");
    }
}
