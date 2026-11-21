package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphChoix;
import ca.vina.nexu.effectifs.domaine.AgphPosteNonPourvu;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adaptateur SQLite des campagnes AGPH ; les rangs et les choix se lisent joints aux ressources et aux postes. */
@Repository("effectifsDepotAgph")
public class DepotAgph implements DepotCampagnesAgphPort {

    private final JdbcClient jdbc;

    public DepotAgph(JdbcClient jdbc, SchemaAgph schema, SchemaPlanification planification) {
        this.jdbc = jdbc;
    }

    private static AgphCampagne campagne(ResultSet rs, int i) throws SQLException {
        return new AgphCampagne(rs.getString("id"), rs.getString("uniteCode"), rs.getInt("annee"), rs.getString("nom"), rs.getString("corpsEmploiCode"),
                Agph.EtatCampagne.valueOf(rs.getString("etat")), rs.getInt("delaiChoixMinutes"), rs.getString("creeLe"), rs.getString("ouverteLe"),
                rs.getString("closeLe"), budget(rs), rs.getString("reviseeLe"), Agph.TraitementDelaiEchu.valueOf(rs.getString("traitementDelaiEchu")));
    }

    /** Le budget est nul tant qu'il n'est pas fixé : {@code getDouble} le lirait 0. */
    private static Double budget(ResultSet rs) throws SQLException {
        double b = rs.getDouble("budgetHeuresSemaine");
        return rs.wasNull() ? null : b;
    }

    @Override
    public Optional<AgphCampagne> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM eff_agph_campagnes WHERE tenantId = ? AND id = ?").params(tenantId, id).query(DepotAgph::campagne).optional();
    }

    @Override
    public List<AgphCampagne> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_agph_campagnes WHERE tenantId = ? ORDER BY annee DESC, uniteCode").param(tenantId).query(DepotAgph::campagne).list();
    }

    @Override
    public boolean existe(String tenantId, String uniteCode, String corpsEmploiCode, int annee) {
        return !jdbc.sql("SELECT 1 FROM eff_agph_campagnes WHERE tenantId = ? AND uniteCode = ? AND corpsEmploiCode = ? AND annee = ?")
                .params(tenantId, uniteCode, corpsEmploiCode, annee).query().listOfRows().isEmpty();
    }

    @Override
    public void inserer(String tenantId, AgphCampagne c) {
        jdbc.sql("INSERT INTO eff_agph_campagnes (id, tenantId, uniteCode, annee, nom, corpsEmploiCode, etat, delaiChoixMinutes, creeLe, budgetHeuresSemaine, "
                        + "traitementDelaiEchu) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(c.id(), tenantId, c.uniteCode(), c.annee(), c.nom(), c.corpsEmploiCode(), c.etat().name(), c.delaiChoixMinutes(), c.creeLe(),
                        c.budgetHeuresSemaine(), c.traitementDelaiEchu().name()).update();
    }

    @Override
    public List<Agph.Candidat> candidats(String tenantId, String uniteCode, String corpsEmploiCode) {
        return jdbc.sql("SELECT id, anciennete, matricule FROM eff_ressources WHERE tenantId = ? AND uniteCode = ? AND corpsEmploiCode = ? AND actif = 1")
                .params(tenantId, uniteCode, corpsEmploiCode)
                .query((rs, i) -> new Agph.Candidat(rs.getString("id"), rs.getDouble("anciennete"), rs.getString("matricule"))).list();
    }

    @Override
    public List<String> postesOffrables(String tenantId, String uniteCode, String corpsEmploiCode) {
        return jdbc.sql("SELECT id FROM eff_postes WHERE tenantId = ? AND uniteCode = ? AND corpsEmploiCode = ? AND etat IN (?, ?) AND titulaireId IS NULL")
                .params(tenantId, uniteCode, corpsEmploiCode, Planification.EtatPoste.PUBLIE.name(), Planification.EtatPoste.VACANT.name())
                .query(String.class).list();
    }

    @Override
    public void ouvrir(String tenantId, String id, Ouverture o) {
        for (Agph.RangCalcule r : o.rangs()) {
            jdbc.sql("INSERT INTO eff_agph_rangs (campagneId, tenantId, ressourceId, rang, anciennete, etat) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(id, tenantId, r.candidat().ressourceId(), r.rang(), r.candidat().anciennete(), Agph.EtatRang.EN_ATTENTE.name()).update();
        }
        for (String posteId : o.postes()) {
            jdbc.sql("INSERT INTO eff_agph_postes (campagneId, tenantId, posteId) VALUES (?, ?, ?)").params(id, tenantId, posteId).update();
        }
        jdbc.sql("UPDATE eff_agph_campagnes SET etat = ?, ouverteLe = ? WHERE tenantId = ? AND id = ?")
                .params(Agph.EtatCampagne.DEPOT.name(), o.instant(), tenantId, id).update();
    }

    @Override
    public void poserEtat(String tenantId, String id, Agph.EtatCampagne etat) {
        jdbc.sql("UPDATE eff_agph_campagnes SET etat = ? WHERE tenantId = ? AND id = ?").params(etat.name(), tenantId, id).update();
    }

    @Override
    public void clore(String tenantId, String id, String instant) {
        jdbc.sql("UPDATE eff_agph_campagnes SET etat = ?, closeLe = ? WHERE tenantId = ? AND id = ?")
                .params(Agph.EtatCampagne.CLOSE.name(), instant, tenantId, id).update();
    }

    @Override
    public List<AgphRang> rangs(String tenantId, String campagneId) {
        return jdbc.sql("SELECT g.ressourceId, r.nom, r.matricule, g.rang, g.anciennete, g.etat, g.tourDebut, g.tourFin FROM eff_agph_rangs g "
                        + "JOIN eff_ressources r ON r.id = g.ressourceId AND r.tenantId = g.tenantId WHERE g.tenantId = ? AND g.campagneId = ? ORDER BY g.rang")
                .params(tenantId, campagneId)
                .query((rs, i) -> new AgphRang(rs.getString("ressourceId"), rs.getString("nom"), rs.getString("matricule"), rs.getInt("rang"),
                        rs.getDouble("anciennete"), Agph.EtatRang.valueOf(rs.getString("etat")), rs.getString("tourDebut"), rs.getString("tourFin"))).list();
    }

    @Override
    public Set<String> campagnesDe(String tenantId, String ressourceId) {
        return new LinkedHashSet<>(jdbc.sql("SELECT campagneId FROM eff_agph_rangs WHERE tenantId = ? AND ressourceId = ?").params(tenantId, ressourceId)
                .query(String.class).list());
    }

    @Override
    public void donnerTour(String tenantId, String campagneId, String ressourceId, String debut, String fin) {
        jdbc.sql("UPDATE eff_agph_rangs SET etat = ?, tourDebut = ?, tourFin = ? WHERE tenantId = ? AND campagneId = ? AND ressourceId = ?")
                .params(Agph.EtatRang.A_SON_TOUR.name(), debut, fin, tenantId, campagneId, ressourceId).update();
    }

    @Override
    public void poserEtatRang(String tenantId, String campagneId, String ressourceId, Agph.EtatRang etat) {
        jdbc.sql("UPDATE eff_agph_rangs SET etat = ? WHERE tenantId = ? AND campagneId = ? AND ressourceId = ?")
                .params(etat.name(), tenantId, campagneId, ressourceId).update();
    }

    @Override
    public List<String> postesLibres(String tenantId, String campagneId) {
        return jdbc.sql("SELECT o.posteId FROM eff_agph_postes o JOIN eff_postes p ON p.id = o.posteId AND p.tenantId = o.tenantId "
                        + "WHERE o.tenantId = ? AND o.campagneId = ? AND NOT EXISTS (SELECT 1 FROM eff_agph_choix c WHERE c.tenantId = o.tenantId "
                        + "AND c.campagneId = o.campagneId AND c.posteId = o.posteId) ORDER BY p.code")
                .params(tenantId, campagneId).query(String.class).list();
    }

    @Override
    public void insererChoix(String tenantId, String campagneId, ChoixAConsigner c) {
        jdbc.sql("INSERT INTO eff_agph_choix (campagneId, tenantId, ressourceId, posteId, mode, choisiLe, choisiPar) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(campagneId, tenantId, c.ressourceId(), c.posteId(), c.mode().name(), c.choisiLe(), c.choisiPar()).update();
    }

    @Override
    public List<AgphChoix> choix(String tenantId, String campagneId) {
        return jdbc.sql("SELECT c.ressourceId, c.posteId, p.code AS posteCode, c.mode, c.choisiLe FROM eff_agph_choix c "
                        + "JOIN eff_postes p ON p.id = c.posteId AND p.tenantId = c.tenantId WHERE c.tenantId = ? AND c.campagneId = ? ORDER BY c.choisiLe")
                .params(tenantId, campagneId)
                .query((rs, i) -> new AgphChoix(rs.getString("ressourceId"), rs.getString("posteId"), rs.getString("posteCode"),
                        Agph.ModeChoix.valueOf(rs.getString("mode")), rs.getString("choisiLe"))).list();
    }

    @Override
    public void parametrer(String tenantId, String id, Double budget) {
        jdbc.sql("UPDATE eff_agph_campagnes SET budgetHeuresSemaine = ? WHERE tenantId = ? AND id = ?").params(budget, tenantId, id).update();
    }

    @Override
    public void parametrerDelaiEchu(String tenantId, String id, Agph.TraitementDelaiEchu traitement) {
        jdbc.sql("UPDATE eff_agph_campagnes SET traitementDelaiEchu = ? WHERE tenantId = ? AND id = ?").params(traitement.name(), tenantId, id).update();
    }

    @Override
    public void reviser(String tenantId, String id, String instant) {
        jdbc.sql("UPDATE eff_agph_campagnes SET etat = ?, reviseeLe = ? WHERE tenantId = ? AND id = ?")
                .params(Agph.EtatCampagne.REVISION.name(), instant, tenantId, id).update();
    }

    @Override
    public void retirerChoix(String tenantId, String campagneId, String ressourceId) {
        jdbc.sql("DELETE FROM eff_agph_choix WHERE tenantId = ? AND campagneId = ? AND ressourceId = ?").params(tenantId, campagneId, ressourceId).update();
        poserEtatRang(tenantId, campagneId, ressourceId, Agph.EtatRang.PASSE);
    }

    @Override
    public List<AgphPosteNonPourvu> postesNonPourvus(String tenantId, String campagneId) {
        return jdbc.sql("SELECT o.posteId, p.code AS posteCode, p.etat FROM eff_agph_postes o JOIN eff_postes p ON p.id = o.posteId AND p.tenantId = o.tenantId "
                        + "WHERE o.tenantId = ? AND o.campagneId = ? AND NOT EXISTS (SELECT 1 FROM eff_agph_choix c WHERE c.tenantId = o.tenantId "
                        + "AND c.campagneId = o.campagneId AND c.posteId = o.posteId) ORDER BY p.code")
                .params(tenantId, campagneId)
                .query((rs, i) -> new AgphPosteNonPourvu(rs.getString("posteId"), rs.getString("posteCode"), Planification.EtatPoste.valueOf(rs.getString("etat"))))
                .list();
    }
}
