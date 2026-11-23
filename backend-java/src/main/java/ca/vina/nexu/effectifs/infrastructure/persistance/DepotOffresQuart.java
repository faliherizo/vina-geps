package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotOffresQuartPort;
import ca.vina.nexu.effectifs.domaine.OffreQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatOffre;
import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatRang;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptateur SQLite des offres de quart (AD-07). Le quart se lit sur la place et son équipage, le nom sur la ressource. */
@Repository("effectifsDepotOffresQuart")
public class DepotOffresQuart implements DepotOffresQuartPort {

    private static final String OFFRE = "SELECT o.id, o.comblementId, o.absenceId, o.placeId, o.urgente, o.etat, o.creeLe, o.attribueeA, "
            + "e.uniteCode, e.date, e.debut, e.fin FROM eff_offres o JOIN eff_places p ON p.id = o.placeId AND p.tenantId = o.tenantId "
            + "JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId WHERE o.tenantId = ? AND o.id = ?";

    private final JdbcClient jdbc;

    public DepotOffresQuart(JdbcClient jdbc, SchemaOffresQuart schema) {
        this.jdbc = jdbc;
    }

    @Override
    public void ouvrir(String tenantId, OffreQuart o) {
        jdbc.sql("INSERT INTO eff_offres (id, tenantId, comblementId, absenceId, placeId, urgente, etat, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(o.id(), tenantId, o.comblementId(), o.absenceId(), o.quart().placeId(), o.urgente() ? 1 : 0, o.etat().name(), o.creeLe()).update();
        for (OffreQuart.Rang r : o.rangs()) {
            jdbc.sql("INSERT INTO eff_offre_rangs (tenantId, offreId, rang, ressourceId, anciennete, heuresComptees, heuresOffertes, etat) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(tenantId, o.id(), r.rang(), r.ressourceId(), r.anciennete(), r.heuresComptees(), r.heuresOffertes(), r.etat().name()).update();
        }
    }

    @Override
    public Optional<OffreQuart> lire(String tenantId, String offreId) {
        return jdbc.sql(OFFRE).params(tenantId, offreId).query((rs, i) -> offre(rs, rangs(tenantId, offreId))).optional();
    }

    private static OffreQuart offre(ResultSet rs, List<OffreQuart.Rang> rangs) throws SQLException {
        return new OffreQuart(rs.getString("id"), rs.getString("comblementId"), rs.getString("absenceId"),
                new OffreQuart.Quart(rs.getString("placeId"), rs.getString("uniteCode"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin")),
                rs.getInt("urgente") == 1, EtatOffre.valueOf(rs.getString("etat")), rs.getString("creeLe"), rs.getString("attribueeA"), rangs);
    }

    private List<OffreQuart.Rang> rangs(String tenantId, String offreId) {
        return jdbc.sql("SELECT g.*, r.nom FROM eff_offre_rangs g LEFT JOIN eff_ressources r ON r.id = g.ressourceId AND r.tenantId = g.tenantId "
                        + "WHERE g.tenantId = ? AND g.offreId = ? ORDER BY g.rang")
                .params(tenantId, offreId)
                .query((rs, i) -> new OffreQuart.Rang(rs.getInt("rang"), rs.getString("ressourceId"), rs.getString("nom"), rs.getDouble("anciennete"),
                        rs.getDouble("heuresComptees"), rs.getDouble("heuresOffertes"), EtatRang.valueOf(rs.getString("etat")), rs.getString("offertLe"),
                        rs.getString("echeance"), rs.getString("reponduLe"), rs.getString("motif"))).list();
    }

    @Override
    public void proposer(String tenantId, Proposition p) {
        jdbc.sql("UPDATE eff_offre_rangs SET etat = ?, offertLe = ?, echeance = ? WHERE tenantId = ? AND offreId = ? AND ressourceId = ?")
                .params(EtatRang.OFFERT.name(), p.offertLe(), p.echeance(), tenantId, p.offreId(), p.ressourceId()).update();
    }

    @Override
    public void repondre(String tenantId, Reponse r) {
        jdbc.sql("UPDATE eff_offre_rangs SET etat = ?, reponduLe = ?, motif = ? WHERE tenantId = ? AND offreId = ? AND ressourceId = ?")
                .params(r.etat().name(), r.le(), r.motif(), tenantId, r.offreId(), r.ressourceId()).update();
    }

    @Override
    public void fermer(String tenantId, Fermeture f) {
        jdbc.sql("UPDATE eff_offres SET etat = ?, fermeeLe = ?, attribueeA = ? WHERE tenantId = ? AND id = ?")
                .params(f.etat().name(), f.le(), f.attribueeA(), tenantId, f.offreId()).update();
        jdbc.sql("UPDATE eff_offre_rangs SET etat = ? WHERE tenantId = ? AND offreId = ? AND etat IN (?, ?)")
                .params(EtatRang.CLOS.name(), tenantId, f.offreId(), EtatRang.EN_ATTENTE.name(), EtatRang.OFFERT.name()).update();
    }

    @Override
    public Map<String, Double> heuresComptees(String tenantId, Collection<String> ressourceIds, String depuis, Collection<EtatRang> etats) {
        if (ressourceIds.isEmpty() || etats.isEmpty()) {
            return Map.of();
        }
        List<Object> params = new ArrayList<>(List.of(tenantId, depuis));
        params.addAll(ressourceIds);
        etats.forEach(e -> params.add(e.name()));
        Map<String, Double> heures = new HashMap<>();
        jdbc.sql("SELECT ressourceId, SUM(heuresOffertes) AS heures FROM eff_offre_rangs WHERE tenantId = ? AND reponduLe >= ? AND ressourceId IN ("
                        + marques(ressourceIds.size()) + ") AND etat IN (" + marques(etats.size()) + ") GROUP BY ressourceId")
                .params(params).query((ResultSet rs) -> {
                    heures.put(rs.getString("ressourceId"), rs.getDouble("heures"));
                });
        return heures;
    }

    /** Des marques de paramètre, jamais des valeurs : la requête reste liée. */
    private static String marques(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }

    /** L'échéance se compare en instant, pas en texte : un instant reçu sans millisecondes trierait mal. */
    @Override
    public List<String> ouvertesEchues(String tenantId, String maintenant) {
        Instant t = Instant.parse(maintenant);
        return jdbc.sql("SELECT DISTINCT g.offreId, g.echeance FROM eff_offre_rangs g JOIN eff_offres o ON o.id = g.offreId AND o.tenantId = g.tenantId "
                        + "WHERE g.tenantId = ? AND o.etat = ? AND g.etat = ?")
                .params(tenantId, EtatOffre.OUVERTE.name(), EtatRang.OFFERT.name())
                .query((rs, i) -> Map.entry(rs.getString("offreId"), Instant.parse(rs.getString("echeance")))).list().stream()
                .filter(e -> !t.isBefore(e.getValue())).map(Map.Entry::getKey).distinct().toList();
    }

    @Override
    public List<OffreQuart> offertesA(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT offreId FROM eff_offre_rangs WHERE tenantId = ? AND ressourceId = ? AND etat = ?")
                .params(tenantId, ressourceId, EtatRang.OFFERT.name()).query(String.class).list().stream()
                .flatMap(id -> lire(tenantId, id).stream())
                .sorted(Comparator.comparing((OffreQuart o) -> echeanceDe(o, ressourceId))).toList();
    }

    private static String echeanceDe(OffreQuart o, String ressourceId) {
        return o.rangs().stream().filter(r -> r.ressourceId().equals(ressourceId)).map(OffreQuart.Rang::echeance).findFirst().orElse("");
    }

    @Override
    public List<OffreQuart> deLAbsence(String tenantId, String absenceId) {
        return jdbc.sql("SELECT id FROM eff_offres WHERE tenantId = ? AND absenceId = ? ORDER BY creeLe, id").params(tenantId, absenceId)
                .query(String.class).list().stream().flatMap(id -> lire(tenantId, id).stream())
                .sorted(Comparator.comparing((OffreQuart o) -> o.quart().date()).thenComparingInt(o -> o.quart().debut())).toList();
    }

    /** Le réglage s'écrit d'un bloc : un seul organisme, une seule ligne. */
    static OffresQuart.Parametres parametres(ResultSet rs) throws SQLException {
        return new OffresQuart.Parametres(OffresQuart.Mode.de(rs.getString("mode")), rs.getInt("delaiReponseMinutes"), rs.getInt("seuilUrgenceMinutes"),
                rs.getInt("fenetreRepartitionJours"));
    }
}
