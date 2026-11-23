package ca.vina.nexu.partage.securite;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Lecture et émission du jeton de session, au format exact du backend Node.
 *
 * <p>Le front-end n'est pas modifié : il envoie le cookie {@code session} tel que le Node le lui a
 * posé, et le Java doit savoir le lire. Pendant l'étranglement, les deux backends servent le même
 * navigateur — un jeton émis par l'un doit être accepté par l'autre, dans les deux sens.
 *
 * <p>Le format est donc figé par {@code backend/src/auth/auth.ts} :
 * <ul>
 *   <li>HS256, secret partagé ({@code JWT_SECRET}) ;</li>
 *   <li>revendications {@code sub}, {@code username}, {@code role}, {@code employeeId},
 *       {@code tenantId} — les deux dernières pouvant être nulles ;</li>
 *   <li>durée de validité de huit heures.</li>
 * </ul>
 *
 * <p><strong>Le tenant vient du jeton et de nulle part ailleurs.</strong> C'est l'invariante
 * d'isolation du produit : jamais d'un en-tête client, qu'un appelant pourrait forger.
 */
public final class JetonDeSession {

    /** Huit heures, comme {@code JWT_EXPIRES} côté Node. */
    public static final long VALIDITE_SECONDES = 8 * 60 * 60L;
    /**
     * La revendication {@code typ} d'un jeton <em>machine</em> ({@code client_credentials}). Les deux jetons partagent la
     * clé : sans ce refus, un jeton d'application de l'organisme valait session, avec l'établissement et sans rôle — assez
     * pour toute route qui n'exige qu'un établissement (26-015-DO, AD-06).
     */
    public static final String TYP_MACHINE = "client";

    /**
     * Taille minimale du secret : 256 bits, imposée par la RFC 7518 §3.2 pour HS256.
     *
     * <p>La bibliothèque {@code jsonwebtoken} du backend Node ne vérifie pas cette contrainte et
     * accepte un secret plus court ; {@code jjwt} la refuse. Ce n'est pas une incompatibilité
     * d'outillage à contourner : c'est le Node qui signe aujourd'hui avec une clé plus faible que
     * ce que l'algorithme exige. La migration est l'occasion de le corriger, pas de le reproduire.
     */
    private static final int OCTETS_MINIMUM = 32;

    private final SecretKey cle;

    public JetonDeSession(String secret) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < OCTETS_MINIMUM) {
            throw new SecretTropCourtException(octets.length);
        }
        // jsonwebtoken (Node) utilise les octets bruts du secret, sans dérivation ni décodage
        // base64. Toute autre interprétation produirait une signature valide côté Java et
        // rejetée côté Node — l'incompatibilité la plus difficile à diagnostiquer.
        this.cle = new SecretKeySpec(octets, "HmacSHA256");
    }

    /**
     * Levée au démarrage plutôt qu'à la première connexion : un secret trop court doit empêcher
     * le service de démarrer, pas produire des refus d'authentification inexplicables en charge.
     */
    public static class SecretTropCourtException extends IllegalArgumentException {
        public SecretTropCourtException(int octets) {
            super("JWT_SECRET fait %d octets (%d bits) ; HS256 en exige au moins %d (256 bits) — RFC 7518 §3.2. "
                    .formatted(octets, octets * 8, OCTETS_MINIMUM)
                    + "Allonger le secret **dans les deux backends à la fois** : le rendre plus long d'un seul "
                    + "côté rendrait les sessions illisibles par l'autre. La rotation invalide les sessions en "
                    + "cours (durée de vie maximale : 8 h).");
        }
    }

    /**
     * Revendications portées par le jeton. {@code employeeId} et {@code tenantId} sont
     * volontairement nullables : un compte global (superadmin) n'a ni l'un ni l'autre, et
     * confondre « absent » avec « chaîne vide » casserait l'isolation.
     */
    public record Revendications(
            String sub,
            String username,
            String role,
            String employeeId,
            String tenantId,
            String emprunteur,
            String empruntId) {
        public Revendications(String sub, String username, String role, String employeeId, String tenantId) {
            this(sub, username, role, employeeId, tenantId, null, null);
        }
    }

    /**
     * L'identité d'un jeton lu : son identifiant ({@code jti}, absent des jetons émis avant le 22 novembre 2026) et son
     * échéance. C'est ce que la déconnexion révoque (PT-02).
     */
    public record Identite(String id, Instant expireLe) { }

    /** Un jeton lu : ses revendications et son identité. */
    public record Lecture(Revendications revendications, Identite identite) { }

    /** Retourne les revendications, ou {@code null} si le jeton est invalide, altéré ou expiré. */
    public Revendications lire(String jeton) {
        Lecture l = lireAvecIdentite(jeton);
        return l == null ? null : l.revendications();
    }

    /** Comme {@link #lire}, avec l'identité du jeton ; {@code null} sur tout échec. */
    public Lecture lireAvecIdentite(String jeton) {
        try {
            Claims c = Jwts.parser().verifyWith(cle).build().parseSignedClaims(jeton).getPayload();
            if (TYP_MACHINE.equals(c.get("typ", String.class))) {
                return null;
            }
            return new Lecture(revendications(c), new Identite(c.getId(), c.getExpiration() == null ? null : c.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // Le Node répond `null` sur tout échec de vérification, sans distinguer les causes.
            // Reproduire ce silence est un choix de sécurité autant que de compatibilité : le
            // détail de l'échec ne doit pas fuir vers l'appelant.
            return null;
        }
    }

    private static Revendications revendications(Claims c) {
        return new Revendications(c.getSubject(), c.get("username", String.class), c.get("role", String.class), c.get("employeeId", String.class),
                c.get("tenantId", String.class), c.get("emprunteur", String.class), c.get("empruntId", String.class));
    }

    /** Émet un jeton dans le format attendu par le backend Node. */
    public String emettre(Revendications r) {
        return emettre(r, VALIDITE_SECONDES);
    }

    /** Un jeton à durée choisie : la session empruntée (ADR 0038) vaut une heure, pas huit. */
    public String emettre(Revendications r, long validiteSecondes) {
        long maintenant = System.currentTimeMillis();
        var b = Jwts.builder()
                .subject(r.sub())
                .claim("username", r.username())
                .claim("role", r.role())
                .claim("employeeId", r.employeeId())
                .claim("tenantId", r.tenantId());
        if (r.emprunteur() != null) {
            b = b.claim("emprunteur", r.emprunteur()).claim("empruntId", r.empruntId());
        }
        // Un identifiant par jeton : c'est lui que la déconnexion révoque (PT-02).
        return b.id(UUID.randomUUID().toString()).issuedAt(new java.util.Date(maintenant))
                .expiration(new java.util.Date(maintenant + validiteSecondes * 1000))
                .signWith(cle)
                .compact();
    }
}
