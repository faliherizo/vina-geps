package ca.vina.nexu.partage.identifiants.infrastructure;

import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Les identifiants préfixés : un préfixe, l'instant et un compteur global persistant ({@code seq}).
 *
 * Le compteur est lu et écrit à chaque tirage, sous verrou : deux instances du serveur partagent
 * la base, et un compteur en mémoire divergerait dès que l'autre en tire un. Les jetons viennent
 * d'un générateur cryptographique ; il est injectable pour que les essais soient rejouables.
 */
@Component
public class SequenceEnBase implements Sequence {

    private static final int OCTETS_JETON_SIGNATURE = 24;

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final RandomGenerator alea;

    @Autowired
    public SequenceEnBase(JdbcClient jdbc, Clock horloge) {
        this(jdbc, horloge, new SecureRandom());
    }

    SequenceEnBase(JdbcClient jdbc, Clock horloge, RandomGenerator alea) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.alea = alea;
    }

    @Override
    public synchronized String suivant(String prefixe) {
        long valeur = jdbc.sql("SELECT value FROM seq WHERE name = 'global'").query(Long.class).optional().orElse(0L) + 1;
        jdbc.sql("INSERT INTO seq (name, value) VALUES ('global', ?) ON CONFLICT(name) DO UPDATE SET value = excluded.value").param(valeur).update();
        return prefixe + "-" + horloge.millis() + "-" + valeur;
    }

    @Override
    public String uuid() {
        return new UUID(alea.nextLong(), alea.nextLong()).toString();
    }

    @Override
    public String jetonSignature() {
        return HexFormat.of().formatHex(octets(OCTETS_JETON_SIGNATURE));
    }

    @Override
    public String jetonBase64Url(int taille) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets(taille));
    }

    private byte[] octets(int taille) {
        byte[] octets = new byte[taille];
        alea.nextBytes(octets);
        return octets;
    }
}
