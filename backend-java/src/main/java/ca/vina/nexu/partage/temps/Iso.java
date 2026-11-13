package ca.vina.nexu.partage.temps;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Les instants s'écrivent comme {@code Date.prototype.toISOString()} : millisecondes toujours
 * présentes, {@code Z} final. {@code Instant.toString()} omet les millisecondes quand elles sont
 * nulles — et un contrat figé sur le Node se compare octet par octet.
 */
public final class Iso {

    private Iso() {
    }

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public static String de(Instant instant) {
        return FORMAT.format(instant);
    }

    public static String maintenant() {
        return de(Instant.now());
    }
}
