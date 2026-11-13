package ca.vina.nexu.partage.audit.infrastructure;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Le journal d'audit du Node ({@code audit_log}), avec sa <strong>chaîne d'empreintes</strong>
 * (exigence 39) : chaque entrée porte le SHA-256 de l'empreinte précédente et de son contenu,
 * champs joints par NUL — exactement {@code empreinteDe} de {@code auth/audit.ts}. Écrire ici
 * depuis Java sans la chaîne casserait la vérification d'intégrité que le Node fait sur la même
 * table.
 */
@Repository
public class JournalAudit {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SEPARATEUR = "\u0000";
    private final JdbcClient jdbc;

    public JournalAudit(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Entree(String id, String at, String userId, String username, String action, String target, String details) { }

    /** SHA-256 de (précédente, id, at, userId, username, action, target, details), joints par U+0000. */
    public static String empreinteDe(String precedente, Entree e) {
        String texte = String.join(SEPARATEUR, nulVide(precedente), e.id(), e.at(), nulVide(e.userId()), nulVide(e.username()), e.action(), nulVide(e.target()), nulVide(e.details()));
        return HexFormat.of().formatHex(condensat("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
    }

    /** SHA-256 est obligatoire sur toute JVM (JCA) : son absence est une erreur de plateforme, pas un cas métier. */
    static MessageDigest condensat(String algorithme) {
        try {
            return MessageDigest.getInstance(algorithme);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("algorithme de condensat indisponible : " + algorithme, ex);
        }
    }

    private static String nulVide(String s) {
        return s == null ? "" : s;
    }

    private Optional<String> derniereEmpreinte() {
        return jdbc.sql("SELECT empreinte FROM audit_log WHERE empreinte IS NOT NULL ORDER BY rowid DESC LIMIT 1").query(String.class).optional();
    }

    public void consigner(String userId, String username, String action, String target, Map<String, Object> details) {
        consigner(userId, username, action, target, details, acteurReelCourant());
    }

    /** L'acteur réel nommé (ADR 0038) : hors de la chaîne d'empreintes, pour ne pas rompre les journaux existants. */
    public void consigner(String userId, String username, String action, String target, Map<String, Object> details, String acteurReel) {
        String id = "aud-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1_000_000);
        Entree e = new Entree(id, Iso.maintenant(), userId, username, action, target, details == null ? null : JSON.writeValueAsString(details));
        jdbc.sql("INSERT INTO audit_log (id, at, userId, username, action, target, details, empreinte, acteurReel) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(e.id(), e.at(), e.userId(), e.username(), e.action(), e.target(), e.details(), empreinteDe(derniereEmpreinte().orElse(null), e), acteurReel).update();
    }

    /** Sous une session empruntée, l'administrateur qui agit vraiment — lu du contexte de la requête courante ; {@code null} hors requête ou session ordinaire. */
    static String acteurReelCourant() {
        RequestAttributes attributs = RequestContextHolder.getRequestAttributes();
        if (attributs == null) {
            return null;
        }
        Object contexte = attributs.getAttribute(ContexteRequete.ATTRIBUT, RequestAttributes.SCOPE_REQUEST);
        return contexte instanceof ContexteRequete c ? c.emprunteur() : null;
    }
}
