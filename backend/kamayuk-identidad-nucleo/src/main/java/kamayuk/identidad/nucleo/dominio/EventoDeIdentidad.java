package kamayuk.identidad.nucleo.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un hecho ya escrito en el buzon: lo de {@link HechoDeIdentidad} mas lo que pone el motor.
 *
 * <p>Son dos tipos y no uno porque hay dos momentos: antes de escribir no hay secuencia —la asigna
 * el {@code IDENTITY} de la tabla— y darle un valor por omision aqui obligaria a que alguien
 * decidiera cual, con un cero que se leeria como «el primero». La secuencia es lo que ordena la
 * entrega, asi que un cero inventado la desordena entera.
 *
 * @param secuencia el {@code id} de la tabla, monotono por ser {@code IDENTITY}
 * @param creadoEn cuando se emitio, con el reloj de quien escribe y no con el de la base
 */
public record EventoDeIdentidad(
        long secuencia,
        UUID eventoId,
        TipoDeEventoDeIdentidad tipo,
        long sujetoId,
        String cuerpo,
        String huella,
        Instant creadoEn) {

    public EventoDeIdentidad {
        Objects.requireNonNull(eventoId, "Un evento escrito tiene su identidad");
        Objects.requireNonNull(tipo, "Un evento escrito tiene su tipo");
        Objects.requireNonNull(cuerpo, "Un evento escrito lleva su cuerpo");
        Objects.requireNonNull(huella, "Un evento escrito lleva su huella");
        Objects.requireNonNull(creadoEn, "Un evento escrito dice cuando se emitio");
    }
}
