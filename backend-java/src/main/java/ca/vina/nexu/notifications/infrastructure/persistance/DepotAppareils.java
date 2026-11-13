package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les appareils mobiles d'un compte ({@code notification_appareils}) et la poussée native vers eux
 * (refonte E ★). Un jeton n'appartient qu'à un compte ; le service qui ne le connaît plus le fait retirer.
 */
@Repository
public class DepotAppareils {

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final PousseeNative.PorteurNatif porteur;

    public DepotAppareils(JdbcClient jdbc, Clock horloge, PousseeNative.PorteurNatif porteur) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.porteur = porteur;
    }

    private static Map<String, Object> ligne(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("tenantId", rs.getString("tenantId"));
        m.put("userId", rs.getString("userId"));
        m.put("jeton", rs.getString("jeton"));
        m.put("plateforme", rs.getString("plateforme"));
        m.put("nom", rs.getString("nom"));
        m.put("enregistreLe", rs.getString("enregistreLe"));
        m.put("dernierStatut", rs.getString("dernierStatut"));
        m.put("dernierEnvoiLe", rs.getString("dernierEnvoiLe"));
        return m;
    }

    public List<Map<String, Object>> lister(String tenantId, String userId) {
        return jdbc.sql("SELECT * FROM notification_appareils WHERE tenantId = ? AND userId = ? ORDER BY enregistreLe").params(tenantId, userId).query((rs, i) -> ligne(rs)).list();
    }

    /** Enregistre (ou réattribue) un appareil au compte ; un jeton déjà connu change de main sans doublon. */
    public Map<String, Object> enregistrer(String tenantId, String userId, Map<String, Object> corps) {
        PousseeNative.Appareil a = PousseeNative.lire(corps);
        jdbc.sql("DELETE FROM notification_appareils WHERE jeton = ?").param(a.jeton()).update();
        String id = "app-" + UUID.randomUUID();
        jdbc.sql("INSERT INTO notification_appareils (id, tenantId, userId, jeton, plateforme, nom, enregistreLe) VALUES (?,?,?,?,?,?,?)")
                .params(id, tenantId, userId, a.jeton(), a.plateforme(), a.nom(), Iso.de(horloge.instant())).update();
        return jdbc.sql("SELECT * FROM notification_appareils WHERE id = ?").param(id).query((rs, i) -> ligne(rs)).single();
    }

    /** Retire un appareil du compte appelant seulement : l'identifiant d'un autre compte vaut « inconnu ». */
    public boolean retirer(String tenantId, String userId, String id) {
        return jdbc.sql("DELETE FROM notification_appareils WHERE tenantId = ? AND userId = ? AND id = ?").params(tenantId, userId, id).update() > 0;
    }

    /** Pousse vers tous les appareils de ces comptes ; retire ceux que le service ne connaît plus. */
    public List<Map<String, Object>> pousserVers(String tenantId, List<String> comptes, PousseeWeb.Charge charge) {
        List<Map<String, Object>> resultats = new ArrayList<>();
        if (comptes.isEmpty()) {
            return resultats;
        }
        List<Map<String, Object>> appareils = jdbc.sql("SELECT id, jeton FROM notification_appareils WHERE tenantId = :tenantId AND userId IN (:comptes)")
                .param("tenantId", tenantId).param("comptes", comptes).query().listOfRows();
        for (Map<String, Object> a : appareils) {
            PousseeWeb.Resultat r = porteur.envoyer((String) a.get("jeton"), charge);
            if (PousseeWeb.EXPIRE.equals(r.statut())) {
                jdbc.sql("DELETE FROM notification_appareils WHERE id = ?").param(a.get("id")).update();
            } else {
                jdbc.sql("UPDATE notification_appareils SET dernierStatut = ?, dernierEnvoiLe = ? WHERE id = ?").params(r.detail() != null ? r.statut() + " : " + r.detail() : r.statut(), Iso.de(horloge.instant()), a.get("id")).update();
            }
            Map<String, Object> resultat = new LinkedHashMap<>();
            resultat.put("id", a.get("id"));
            resultat.put("statut", r.statut());
            resultats.add(resultat);
        }
        return resultats;
    }
}
