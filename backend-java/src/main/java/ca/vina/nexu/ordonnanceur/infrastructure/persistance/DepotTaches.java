package ca.vina.nexu.ordonnanceur.infrastructure.persistance;

import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.ordonnanceur.domaine.Echeances;
import ca.vina.nexu.ordonnanceur.domaine.Executeur;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistance et exécution des tâches différées (ADR 0014) : planifier, et faire un tic. Le tic
 * prend ce qui est échu, pose un verrou <strong>dans la même instruction que la lecture</strong>
 * ({@code UPDATE … WHERE statut = ?}) — deux tics concurrents ne prennent jamais la même tâche —,
 * appelle l'exécuteur, consigne l'issue.
 *
 * <p>Aucune boucle n'est démarrée ici : tant que le Node tient la boucle, un second tic en Java
 * exécuterait chaque tâche deux fois. Le tic existe, éprouvé ; la boucle viendra avec le routage.
 */
@Repository
public class DepotTaches implements TacheDifferee.Taches {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CHARGE = new TypeReference<>() { };


    private final JdbcClient jdbc;
    private final RegistreExecuteurs executeurs;

    public DepotTaches(JdbcClient jdbc, RegistreExecuteurs executeurs) {
        this.jdbc = jdbc;
        this.executeurs = executeurs;
    }

    private static TacheDifferee lire(java.sql.ResultSet rs) throws java.sql.SQLException {
        String charge = rs.getString("charge");
        return new TacheDifferee(rs.getString("id"), rs.getString("tenantId"), rs.getString("type"), rs.getString("cibleId"),
                JSON.readValue(charge.isBlank() ? "{}" : charge, CHARGE), rs.getString("echeanceLe"), rs.getString("statut"),
                rs.getInt("tentatives"), rs.getString("derniereErreur"), rs.getString("verrouLe"), rs.getString("verrouPar"));
    }

    /**
     * Planifie — ou <strong>remplace l'échéance</strong> de la tâche qui attend déjà pour la même
     * cible : reporter une recherche hors fenêtre à chaque tic ne doit pas empiler des tâches.
     */
    public TacheDifferee planifier(String tenantId, String type, String cibleId, String echeanceLe, Map<String, Object> charge, String creePar) {
        List<String> erreurs = Echeances.validerPlanification(tenantId, type, cibleId, echeanceLe);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        Optional<TacheDifferee> existante = jdbc.sql("SELECT * FROM taches_differees WHERE tenantId = ? AND type = ? AND cibleId = ? AND statut = 'en_attente'")
                .params(tenantId, type, cibleId).query((rs, i) -> lire(rs)).optional();
        if (existante.isPresent()) {
            Map<String, Object> nouvelle = charge == null ? existante.get().charge() : charge;
            jdbc.sql("UPDATE taches_differees SET echeanceLe = ?, charge = ? WHERE id = ?").params(echeanceLe, JSON.writeValueAsString(nouvelle), existante.get().id()).update();
            return lireTache(existante.get().id()).orElseThrow();
        }
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO taches_differees (id, tenantId, type, cibleId, charge, echeanceLe, statut, createdAt, creePar) VALUES (?,?,?,?,?,?,'en_attente',?,?)")
                .params(id, tenantId, type, cibleId, JSON.writeValueAsString(charge == null ? Map.of() : charge), echeanceLe, Iso.maintenant(),
                        creePar == null ? "systeme" : creePar).update();
        return lireTache(id).orElseThrow();
    }

    public Optional<TacheDifferee> lireTache(String id) {
        return jdbc.sql("SELECT * FROM taches_differees WHERE id = ?").param(id).query((rs, i) -> lire(rs)).optional();
    }

    /** Annule ce qui attend pour une cible — quand le besoin qui l'avait planifié disparaît. */
    public int annulerPour(String tenantId, String type, String cibleId) {
        return jdbc.sql("UPDATE taches_differees SET statut = 'annulee' WHERE tenantId = ? AND type = ? AND cibleId = ? AND statut = 'en_attente'")
                .params(tenantId, type, cibleId).update();
    }

    public List<TacheDifferee> lister(String tenantId, String statut, String type) {
        StringBuilder sql = new StringBuilder("SELECT * FROM taches_differees WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (statut != null) {
            sql.append(" AND statut = ?");
            params.add(statut);
        }
        if (type != null) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        return jdbc.sql(sql + " ORDER BY echeanceLe").params(params).query((rs, i) -> lire(rs)).list();
    }

    /** Un tic : prend ce qui est échu, exécute, consigne. */
    public BilanTic tic(String maintenant, PolitiqueRetentative p, String identite) {
        List<TacheDifferee> candidates = jdbc.sql("SELECT * FROM taches_differees WHERE statut IN ('en_attente','en_cours') AND echeanceLe <= ? ORDER BY echeanceLe LIMIT 200")
                .param(maintenant).query((rs, i) -> lire(rs)).list();
        int prises = 0;
        int faites = 0;
        int reportees = 0;
        int retentees = 0;
        int echouees = 0;
        int sansExecuteur = 0;
        for (TacheDifferee t : Echeances.aPrendre(candidates, maintenant, p)) {
            if (!prendre(t, maintenant, identite)) {
                continue;
            }
            prises++;
            Optional<Executeur> executeur = executeurs.pour(t.type());
            if (executeur.isEmpty()) {
                // Un type planifié par un module qui ne s'est pas enregistré est une faute de câblage, pas un cas à retenter.
                liberer(t.id(), "echouee", null, t.tentatives(), "aucun exécuteur enregistré pour « " + t.type() + " »");
                sansExecuteur++;
                continue;
            }
            try {
                Optional<String> report = executeur.get().executer(t, maintenant);
                if (report.isPresent()) {
                    jdbc.sql("UPDATE taches_differees SET statut = 'en_attente', echeanceLe = ?, verrouLe = NULL, verrouPar = NULL WHERE id = ?")
                            .params(report.get(), t.id()).update();
                    reportees++;
                    continue;
                }
                Echeances.Issue issue = Echeances.apresTentative(t, true, maintenant, p);
                jdbc.sql("UPDATE taches_differees SET statut = 'faite', tentatives = ?, faiteLe = ?, verrouLe = NULL, verrouPar = NULL WHERE id = ?")
                        .params(issue.tentatives(), maintenant, t.id()).update();
                faites++;
            } catch (RuntimeException e) {
                Echeances.Issue issue = Echeances.apresTentative(t, false, maintenant, p);
                String message = String.valueOf(e.getMessage());
                liberer(t.id(), issue.statut(), issue.echeanceLe(), issue.tentatives(), message.substring(0, Math.min(500, message.length())));
                if ("echouee".equals(issue.statut())) {
                    echouees++;
                } else {
                    retentees++;
                }
            }
        }
        return new BilanTic(prises, faites, reportees, retentees, echouees, sansExecuteur);
    }

    /** Prise atomique : si un autre tic l'a prise entre-temps, aucune ligne ne change et l'on passe. */
    boolean prendre(TacheDifferee t, String maintenant, String identite) {
        return jdbc.sql("UPDATE taches_differees SET statut = 'en_cours', verrouLe = ?, verrouPar = ? WHERE id = ? AND statut = ?")
                .params(maintenant, identite, t.id(), t.statut()).update() == 1;
    }

    private void liberer(String id, String statut, String echeanceLe, int tentatives, String erreur) {
        jdbc.sql("UPDATE taches_differees SET statut = ?, tentatives = ?, echeanceLe = COALESCE(?, echeanceLe), derniereErreur = ?, verrouLe = NULL, verrouPar = NULL WHERE id = ?")
                .params(statut, tentatives, echeanceLe, erreur, id).update();
    }
}
