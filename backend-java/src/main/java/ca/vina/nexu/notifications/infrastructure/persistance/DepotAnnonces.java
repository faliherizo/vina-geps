package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Annonces;
import ca.vina.nexu.notifications.domaine.TypeDestinataire;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les annonces (CSSMB 21 ; ADR 0039) : publiées par l'administration, lues à l'accueil, poussées aux
 * navigateurs des comptes visés à la publication — port de {@code notifications/serviceAnnonces.ts}.
 */
@Repository
public class DepotAnnonces {

    private static final String URL_ACCUEIL = "/admin/dashboard";

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotAbonnementsPoussee poussee;

    public DepotAnnonces(JdbcClient jdbc, Clock horloge, DepotAbonnementsPoussee poussee) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.poussee = poussee;
    }

    private String aujourdhui() {
        return LocalDate.now(horloge).toString();
    }

    private static Annonces.Annonce enAnnonce(Map<String, Object> l) {
        return new Annonces.Annonce((String) l.get("id"), (String) l.get("tenantId"), (String) l.get("titre"), (String) l.get("corps"),
                Json.liste(String.valueOf(l.getOrDefault("rolesJson", "[]"))).stream().map(String::valueOf).toList(),
                (String) l.get("du"), (String) l.get("au"), (String) l.get("auteur"), (String) l.get("creeeLe"));
    }

    public Optional<Annonces.Annonce> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM annonces WHERE tenantId = ? AND id = ?").params(tenantId, id).query().listOfRows().stream().findFirst().map(DepotAnnonces::enAnnonce);
    }

    /** Publie : l'annonce est écrite ; chaque compte visé reçoit la poussée (titre) si la période a commencé — son échec ne touche pas l'annonce. */
    @Transactional
    public Annonces.Annonce publier(String tenantId, String auteurId, Map<String, Object> brut) {
        Map.Entry<Optional<Annonces.Nouvelle>, List<String>> lue = Annonces.lire(brut);
        Annonces.Nouvelle n = lue.getKey().orElseThrow(() -> new IllegalArgumentException(String.join("; ", lue.getValue())));
        String id = UUID.randomUUID().toString();
        String instant = Iso.de(horloge.instant());
        jdbc.sql("INSERT INTO annonces (id, tenantId, titre, corps, rolesJson, du, au, auteur, creeeLe) VALUES (?,?,?,?,?,?,?,?,?)")
                .params(id, tenantId, n.titre(), n.corps(), Json.texte(n.roles()), n.du(), n.au(), auteurId, instant).update();
        if (n.du().compareTo(aujourdhui()) <= 0) {
            for (String compte : comptesVises(tenantId, n.roles())) {
                poussee.pousserPourNotification(tenantId, TypeDestinataire.EMPLOYEE.code(), compte, n.titre(), n.corps(), URL_ACCUEIL);
            }
        }
        return lire(tenantId, id).orElseThrow();
    }

    private List<String> comptesVises(String tenantId, List<String> roles) {
        return jdbc.sql("SELECT id, role FROM users WHERE tenantId = ? AND active = 1").param(tenantId).query().listOfRows().stream()
                .filter(u -> roles.isEmpty() || roles.contains(String.valueOf(u.get("role")))).map(u -> (String) u.get("id")).toList();
    }

    /** Toutes les annonces de l'organisme (administration), les plus récentes d'abord, avec le nombre de lectures. */
    public List<Map<String, Object>> toutes(String tenantId) {
        return jdbc.sql("SELECT a.*, (SELECT COUNT(*) FROM annonces_lectures l WHERE l.annonceId = a.id) AS lectures FROM annonces a WHERE a.tenantId = ? ORDER BY a.du DESC, a.creeeLe DESC")
                .param(tenantId).query().listOfRows().stream().map(l -> {
                    Map<String, Object> m = enAnnonce(l).vue();
                    m.put("lectures", ((Number) l.get("lectures")).intValue());
                    return m;
                }).toList();
    }

    /** Ce qu'un compte voit à l'accueil : les annonces en cours qui visent son rôle (ou tous), les non lues d'abord. */
    public List<Map<String, Object>> pourCompte(String tenantId, String userId, String role) {
        String jour = aujourdhui();
        return jdbc.sql("SELECT a.*, (SELECT COUNT(*) FROM annonces_lectures l WHERE l.annonceId = a.id AND l.userId = ?) AS lue FROM annonces a WHERE a.tenantId = ? AND a.du <= ? AND a.au >= ? ORDER BY a.du DESC, a.creeeLe DESC")
                .params(userId, tenantId, jour, jour).query().listOfRows().stream()
                .filter(l -> enAnnonce(l).vise(role))
                .sorted((x, y) -> Long.compare(((Number) x.get("lue")).longValue() > 0 ? 1 : 0, ((Number) y.get("lue")).longValue() > 0 ? 1 : 0))
                .map(l -> {
                    Map<String, Object> m = enAnnonce(l).vue();
                    m.put("lue", ((Number) l.get("lue")).longValue() > 0);
                    return m;
                }).toList();
    }

    /** Marquer lue : une fois par compte ; relire ne casse rien. */
    @Transactional
    public Map<String, Object> marquerLue(String tenantId, String id, String userId) {
        Annonces.Annonce a = lire(tenantId, id).orElseThrow(() -> new IntrouvableException("annonce introuvable"));
        jdbc.sql("INSERT OR IGNORE INTO annonces_lectures (annonceId, userId, luLe) VALUES (?,?,?)").params(id, userId, Iso.de(horloge.instant())).update();
        Map<String, Object> m = new LinkedHashMap<>(a.vue());
        m.put("lue", true);
        return m;
    }

    @Transactional
    public void retirer(String tenantId, String id) {
        lire(tenantId, id).orElseThrow(() -> new IntrouvableException("annonce introuvable"));
        jdbc.sql("DELETE FROM annonces_lectures WHERE annonceId = ?").param(id).update();
        jdbc.sql("DELETE FROM annonces WHERE tenantId = ? AND id = ?").params(tenantId, id).update();
    }
}
