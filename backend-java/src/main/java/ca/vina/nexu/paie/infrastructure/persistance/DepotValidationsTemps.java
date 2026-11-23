package ca.vina.nexu.paie.infrastructure.persistance;

import ca.vina.nexu.paie.domaine.EcritureValidationsPort;
import ca.vina.nexu.paie.domaine.LectureValidationsPort;
import ca.vina.nexu.paie.domaine.ValidationTemps;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les validations des temps en base (changeset 0018, ADR 0084). Chaque décision s'ajoute ; la plus récente d'une feuille
 * fait foi, et l'historique reste lisible pour qui vérifie la paie.
 */
public class DepotValidationsTemps implements LectureValidationsPort, EcritureValidationsPort {

    private final JdbcClient jdbc;

    public DepotValidationsTemps(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Toutes les décisions de l'organisme en ordre chronologique, filtrées ici : la dernière lue écrase les précédentes. */
    @Override
    public Map<String, ValidationTemps> dernieres(String tenantId, Collection<String> feuilleIds) {
        Map<String, ValidationTemps> parFeuille = new HashMap<>();
        if (feuilleIds.isEmpty()) {
            return parFeuille;
        }
        List<ValidationTemps> toutes = jdbc.sql("SELECT id, feuilleId, decision, empreinte, motif, acteur, instant FROM paie_validations_temps "
                        + "WHERE tenantId = ? ORDER BY instant, rowid").params(tenantId)
                .query((rs, i) -> new ValidationTemps(rs.getString("id"), rs.getString("feuilleId"), ValidationTemps.Decision.de(rs.getString("decision")),
                        rs.getString("empreinte"), rs.getString("motif"), rs.getString("acteur"), rs.getString("instant"))).list();
        toutes.stream().filter(v -> feuilleIds.contains(v.feuilleId())).forEach(v -> parFeuille.put(v.feuilleId(), v));
        return parFeuille;
    }

    @Override
    public void consigner(String tenantId, ValidationTemps v) {
        jdbc.sql("INSERT INTO paie_validations_temps (id, tenantId, feuilleId, decision, empreinte, motif, acteur, instant) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(v.id(), tenantId, v.feuilleId(), v.decision().code(), v.empreinte(), v.motif(), v.acteur(), v.instant()).update();
    }
}
