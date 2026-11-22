package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.domaine.ParametresOffresPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptateur SQLite du réglage de l'offre des quarts (AD-07) : une ligne par organisme, les défauts sans ligne. */
@Repository("effectifsDepotParametresOffres")
public class DepotParametresOffres implements ParametresOffresPort {

    private final JdbcClient jdbc;

    public DepotParametresOffres(JdbcClient jdbc, SchemaOffresQuart schema) {
        this.jdbc = jdbc;
    }

    @Override
    public OffresQuart.Parametres lire(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_offres_parametres WHERE tenantId = ?").param(tenantId).query((rs, i) -> DepotOffresQuart.parametres(rs))
                .optional().orElse(OffresQuart.Parametres.PAR_DEFAUT);
    }

    @Override
    public void enregistrer(String tenantId, OffresQuart.Parametres p, String acteurId, String le) {
        jdbc.sql("INSERT INTO eff_offres_parametres (tenantId, mode, delaiReponseMinutes, seuilUrgenceMinutes, fenetreRepartitionJours, modifieLe, modifiePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId) DO UPDATE SET mode = excluded.mode, "
                        + "delaiReponseMinutes = excluded.delaiReponseMinutes, seuilUrgenceMinutes = excluded.seuilUrgenceMinutes, "
                        + "fenetreRepartitionJours = excluded.fenetreRepartitionJours, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, p.mode().name(), p.delaiReponseMinutes(), p.seuilUrgenceMinutes(), p.fenetreRepartitionJours(), le, acteurId).update();
    }
}
