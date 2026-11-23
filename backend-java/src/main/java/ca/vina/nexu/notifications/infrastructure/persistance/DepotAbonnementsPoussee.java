package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;

import ca.vina.nexu.notifications.domaine.AbonnementsPoussee;
import ca.vina.nexu.notifications.domaine.AbonnementsPoussee.Abonnement;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.TypeDestinataire;
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
 * Les navigateurs abonnés d'un compte (table {@code push_abonnements}, migration 0080) — un point
 * de terminaison n'appartient qu'à un compte — et la poussée vers tous les navigateurs d'une
 * personne : ceux que le service déclare expirés sont retirés.
 */
@Repository
public class DepotAbonnementsPoussee {

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final ConfigurationFournisseurs.ClesInstance cles;
    private final PousseeWeb.PorteurPoussee porteur;
    private final DepotAppareils appareils;

    public DepotAbonnementsPoussee(JdbcClient jdbc, Clock horloge, ConfigurationFournisseurs.ClesInstance cles, PousseeWeb.PorteurPoussee porteur, DepotAppareils appareils) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.cles = cles;
        this.porteur = porteur;
        this.appareils = appareils;
    }

    private static Map<String, Object> ligne(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("endpoint", rs.getString("endpoint"));
        m.put("navigateur", rs.getString("navigateur"));
        m.put("creeLe", rs.getString("creeLe"));
        m.put("dernierStatut", rs.getString("dernierStatut"));
        m.put("dernierEnvoiLe", rs.getString("dernierEnvoiLe"));
        return m;
    }

    public Map<String, Object> etat() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cle", cles.cles().publique());
        m.put("configuree", cles.configurees());
        return m;
    }

    public List<Map<String, Object>> lister(String tenantId, String userId) {
        return jdbc.sql("SELECT * FROM push_abonnements WHERE tenantId = ? AND userId = ? ORDER BY creeLe").params(tenantId, userId).query((rs, i) -> ligne(rs)).list();
    }

    public Map<String, Object> abonner(String tenantId, String userId, Object abonnement, String navigateur) {
        List<String> erreurs = AbonnementsPoussee.valider(abonnement);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        AbonnementsPoussee.Abonnement a = AbonnementsPoussee.lire((Map<?, ?>) abonnement);
        jdbc.sql("DELETE FROM push_abonnements WHERE endpoint = ?").param(a.endpoint()).update();
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO push_abonnements (id, tenantId, userId, endpoint, p256dh, auth, navigateur, creeLe) VALUES (?,?,?,?,?,?,?,?)")
                .params(id, tenantId, userId, a.endpoint(), a.p256dh(), a.auth(), navigateur == null ? null : navigateur.substring(0, Math.min(200, navigateur.length())), Iso.de(horloge.instant())).update();
        return jdbc.sql("SELECT * FROM push_abonnements WHERE id = ?").param(id).query((rs, i) -> ligne(rs)).single();
    }

    public boolean desabonner(String tenantId, String userId, String endpoint) {
        return jdbc.sql("DELETE FROM push_abonnements WHERE tenantId = ? AND userId = ? AND endpoint = ?").params(tenantId, userId, endpoint).update() > 0;
    }

    /** Les comptes qui « sont » une personne : le compte dont c'est l'identifiant, et ceux rattachés à ce dossier d'employé (ADR 0011). */
    private List<String> comptesDe(String tenantId, String personId) {
        return jdbc.sql("SELECT DISTINCT id FROM users WHERE (id = ? OR employeeId = ?) AND (tenantId = ? OR tenantId IS NULL)").params(personId, personId, tenantId).query(String.class).list();
    }

    /** Pousse vers tous les navigateurs d'une personne ; retire ceux que le service déclare expirés. */
    public List<Map<String, Object>> pousserVers(String tenantId, String personId, PousseeWeb.Charge charge) {
        List<String> comptes = comptesDe(tenantId, personId);
        List<Map<String, Object>> resultats = new ArrayList<>();
        if (comptes.isEmpty()) {
            return resultats;
        }
        List<Map<String, Object>> abonnements = jdbc.sql("SELECT id, endpoint, p256dh, auth FROM push_abonnements WHERE tenantId = :tenantId AND userId IN (:comptes)")
                .param("tenantId", tenantId).param("comptes", comptes).query().listOfRows();
        for (Map<String, Object> a : abonnements) {
            PousseeWeb.Resultat r = porteur.envoyer(new Abonnement((String) a.get("endpoint"), (String) a.get("p256dh"), (String) a.get("auth")), charge);
            if (PousseeWeb.EXPIRE.equals(r.statut())) {
                jdbc.sql("DELETE FROM push_abonnements WHERE id = ?").param(a.get("id")).update();
            } else {
                jdbc.sql("UPDATE push_abonnements SET dernierStatut = ?, dernierEnvoiLe = ? WHERE id = ?").params(r.detail() != null ? r.statut() + " : " + r.detail() : r.statut(), Iso.de(horloge.instant()), a.get("id")).update();
            }
            Map<String, Object> resultat = new LinkedHashMap<>();
            resultat.put("endpoint", a.get("endpoint"));
            resultat.put("statut", r.statut());
            resultats.add(resultat);
        }
        return resultats;
    }

    /** Le raccord avec le journal des notifications : ne lève jamais — un service de poussée en panne ne casse pas une annulation à 23 h. */
    public void pousserPourNotification(String tenantId, String typeDestinataire, String destinataireId, String sujet, String corps) {
        pousserPourNotification(tenantId, typeDestinataire, destinataireId, sujet, corps, "/admin/notifications");
    }

    public void pousserPourNotification(String tenantId, String typeDestinataire, String destinataireId, String sujet, String corps, String url) {
        pousserPourNotification(tenantId, new Poussee(typeDestinataire, destinataireId, sujet, corps, url, ca.vina.nexu.notifications.domaine.Evenements.ETIQUETTE_NOTIFICATION));
    }

    /** Une poussée : à qui, quoi, vers quel écran, et son étiquette (ordinaire, ou critique — ST-G07). */
    public record Poussee(String typeDestinataire, String destinataireId, String sujet, String corps, String url, String etiquette) { }

    public void pousserPourNotification(String tenantId, Poussee p) {
        String destinataireId = p.destinataireId();
        if (TypeDestinataire.de(p.typeDestinataire()).map(t -> !t.personne()).orElse(true)) {
            return;
        }
        try {
            PousseeWeb.Charge charge = new PousseeWeb.Charge(p.sujet(), p.corps(), p.url(), p.etiquette());
            pousserVers(tenantId, destinataireId, charge);
            // Les appareils mobiles de la même personne (refonte E ★) reçoivent la même charge, par le même raccord.
            appareils.pousserVers(tenantId, comptesDe(tenantId, destinataireId), charge);
        } catch (RuntimeException e) {
            // la notification est déjà journalisée
        }
    }
}
