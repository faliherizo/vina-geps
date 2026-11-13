package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Passkeys;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Les passkeys d'un compte ({@code webauthn_credentials}) et les défis en cours. Les défis sont
 * l'état du processus (cinq minutes), les identifiants sont en base. Isolation par utilisateur :
 * chaque requête porte l'identifiant du compte, jamais celui d'un autre.
 */
@Repository
public class DepotPasskeys {

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final RandomGenerator alea;
    private final String rpId;
    private final Passkeys.Verificateur verificateur;
    private final Map<String, Passkeys.Defi> defis = new ConcurrentHashMap<>();

    /** Le câblage de production : aléa fort, identifiant de partie utilisatrice de l'environnement, vérificateur simulé (#93). */
    @Autowired
    public DepotPasskeys(JdbcClient jdbc, Clock horloge, @Value("${WEBAUTHN_RP_ID:}") String rpId) {
        this(jdbc, horloge, new SecureRandom(), rpId, Passkeys.SIMULE);
    }

    public DepotPasskeys(JdbcClient jdbc, Clock horloge, RandomGenerator alea, String rpId, Passkeys.Verificateur verificateur) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.alea = alea;
        this.rpId = rpId == null || rpId.isEmpty() ? Passkeys.RP_ID_PAR_DEFAUT : rpId;
        this.verificateur = verificateur;
        // La table naît à la première utilisation, comme côté Node : aucune migration ne la porte.
        jdbc.sql("CREATE TABLE IF NOT EXISTS webauthn_credentials (id TEXT PRIMARY KEY, userId TEXT NOT NULL, credentialId TEXT NOT NULL UNIQUE, "
                + "publicKey TEXT NOT NULL, counter INTEGER NOT NULL DEFAULT 0, label TEXT, createdAt TEXT NOT NULL)").update();
        jdbc.sql("CREATE INDEX IF NOT EXISTS idx_webauthn_user ON webauthn_credentials (userId)").update();
    }

    private void purger() {
        long maintenant = horloge.millis();
        defis.values().removeIf(d -> d.expire(maintenant));
    }

    public List<Passkeys.Identifiant> lister(String userId) {
        return jdbc.sql("SELECT id, credentialId, counter, label, createdAt FROM webauthn_credentials WHERE userId = ? ORDER BY createdAt DESC").param(userId)
                .query((rs, i) -> new Passkeys.Identifiant(rs.getString("id"), rs.getString("credentialId"), rs.getInt("counter"), rs.getString("label"), rs.getString("createdAt"))).list();
    }

    public boolean supprimer(String userId, String id) {
        return jdbc.sql("DELETE FROM webauthn_credentials WHERE userId = ? AND id = ?").params(userId, id).update() > 0;
    }

    public Map<String, Object> debuterEnregistrement(String userId) {
        purger();
        String challenge = Passkeys.defi(alea);
        defis.put("reg:" + userId, new Passkeys.Defi(challenge, horloge.millis()));
        return Passkeys.optionsEnregistrement(challenge, rpId, userId);
    }

    public Passkeys.Identifiant terminerEnregistrement(String userId, Map<String, Object> reponse) {
        purger();
        Passkeys.Defi d = defis.remove("reg:" + userId);
        if (d == null) {
            throw new IllegalStateException("no pending registration (challenge expired)");
        }
        if (!verificateur.enregistrement(reponse, d.challenge())) {
            throw new IllegalStateException("registration verification failed");
        }
        String id = UUID.randomUUID().toString();
        String createdAt = Iso.de(horloge.instant());
        int counter = Passkeys.compteur(reponse);
        Object label = reponse.get("label");
        jdbc.sql("INSERT INTO webauthn_credentials (id, userId, credentialId, publicKey, counter, label, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(id, userId, String.valueOf(reponse.get("credentialId")), String.valueOf(reponse.get("publicKey")), counter, label == null ? null : String.valueOf(label), createdAt).update();
        return new Passkeys.Identifiant(id, String.valueOf(reponse.get("credentialId")), counter, label == null ? null : String.valueOf(label), createdAt);
    }

    public Map<String, Object> debuterAuthentification(String userId) {
        purger();
        List<Passkeys.Identifiant> identifiants = lister(userId);
        String challenge = Passkeys.defi(alea);
        Map<String, Object> options = Passkeys.optionsAuthentification(challenge, rpId, identifiants);
        defis.put("auth:" + userId, new Passkeys.Defi(challenge, horloge.millis()));
        return options;
    }

    public Map<String, Object> terminerAuthentification(String userId, Map<String, Object> reponse) {
        purger();
        Passkeys.Defi d = defis.remove("auth:" + userId);
        if (d == null) {
            throw new IllegalStateException("no pending authentication (challenge expired)");
        }
        String credentialId = String.valueOf(reponse.get("credentialId"));
        Map<String, Object> ligne = jdbc.sql("SELECT id, publicKey, counter FROM webauthn_credentials WHERE userId = ? AND credentialId = ?").params(userId, credentialId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("unknown credential"));
        if (!verificateur.authentification(reponse, d.challenge(), String.valueOf(ligne.get("publicKey")))) {
            throw new IllegalStateException("authentication verification failed");
        }
        int recu = Passkeys.compteur(reponse);
        Passkeys.exigerCompteurCroissant(recu, ((Number) ligne.get("counter")).intValue());
        jdbc.sql("UPDATE webauthn_credentials SET counter = ? WHERE id = ?").params(recu, ligne.get("id")).update();
        return Map.of("ok", true, "credentialId", credentialId);
    }
}
