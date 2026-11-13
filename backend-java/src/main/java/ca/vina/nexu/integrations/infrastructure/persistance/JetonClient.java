package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.partage.securite.JetonDeSession;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Jeton d'accès OAuth2 {@code client_credentials} (AO-5338 #NF-08).
 *
 * <p>Distinct du jeton de session : il porte {@code typ: "client"}, aucune identité humaine, et
 * une durée d'une heure au lieu de huit. Cette distinction est une frontière de sécurité — un
 * jeton machine accepté comme session donnerait à une intégration les droits d'un utilisateur.
 * C'est {@code typ} qui la tient, et il est vérifié à la lecture.
 *
 * <p>La contrainte de longueur du secret est celle de {@link JetonDeSession} : même algorithme,
 * même exigence RFC 7518 §3.2. La réutiliser évite qu'un durcissement d'un côté laisse l'autre
 * en arrière.
 */
@Component
public class JetonClient {

    public static final long VALIDITE_SECONDES = 3600L;

    private final SecretKey cle;

    public JetonClient(@Value("${nexu.jwt.secret}") String secret) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            throw new JetonDeSession.SecretTropCourtException(octets.length);
        }
        this.cle = Keys.hmacShaKeyFor(octets);
    }

    public record Revendications(String sub, String tenantId, List<String> scopes) {
    }

    public String emettre(String clientId, String tenantId, List<String> portees) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .subject(clientId)
                .claim("tenantId", tenantId)
                .claim("scope", String.join(" ", portees))
                .claim("typ", "client")
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plusSeconds(VALIDITE_SECONDES)))
                .signWith(cle)
                .compact();
    }

    /** @return les revendications, ou {@code null} si le jeton est invalide, expiré, ou non machine. */
    public Revendications lire(String jeton) {
        try {
            var charge = Jwts.parser().verifyWith(cle).build().parseSignedClaims(jeton).getPayload();
            if (!"client".equals(charge.get("typ", String.class))) {
                return null;
            }
            // Une revendication `scope` absente et une revendication vide donnent le même
            // résultat — aucune portée — et non deux chemins distincts à maintenir.
            String portees = java.util.Objects.requireNonNullElse(charge.get("scope", String.class), "");
            return new Revendications(charge.getSubject(), charge.get("tenantId", String.class),
                    portees.isBlank() ? List.of() : List.of(portees.split(" ")));
        } catch (Exception e) {
            return null;
        }
    }
}
