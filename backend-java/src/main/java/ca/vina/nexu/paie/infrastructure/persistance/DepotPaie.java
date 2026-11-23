package ca.vina.nexu.paie.infrastructure.persistance;

import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.CalculRetenues;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.EcriturePaiePort;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.Frequence;
import ca.vina.nexu.paie.domaine.Gain;
import ca.vina.nexu.paie.domaine.LecturePaiePort;
import ca.vina.nexu.paie.domaine.ModeRemuneration;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.paie.domaine.Retenues;
import ca.vina.nexu.paie.domaine.Trace;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** La paie en base : dossiers, réglages, cycles et bulletins (changesets 0012 et 0018). */
public class DepotPaie implements LecturePaiePort, EcriturePaiePort {

    private static final String DOSSIERS = "SELECT ressourceId, mode, frequence, tauxHoraire, salaireAnnuel, montantPersonnelFederal, "
            + "montantPersonnelQuebec, echelon FROM paie_dossiers WHERE tenantId = ?";
    private static final String CYCLES = "SELECT id, debut, fin, datePaie, etat, prepareLe, preparePar, traiteLe, traitePar FROM paie_cycles WHERE tenantId = ?";
    private static final String BULLETINS = "SELECT b.id, b.cycleId, b.ressourceId, b.matricule, b.nom, b.datePaie, b.heuresRegulieres, "
            + "b.heuresSupplementaires, b.calcul, b.feuilles, b.gains FROM paie_bulletins b";
    private static final String TRAITES = BULLETINS + " JOIN paie_cycles c ON c.tenantId = b.tenantId AND c.id = b.cycleId "
            + "WHERE b.tenantId = ? AND c.etat = ?";
    private static final TypeReference<List<String>> LISTE = new TypeReference<>() { };
    private static final TypeReference<List<Gain>> GAINS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper json = JsonMapper.builder().build();

    public DepotPaie(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Lecture ──────────────────────────────────────────────────────────────

    @Override
    public List<DossierRemuneration> dossiers(String tenantId) {
        return jdbc.sql(DOSSIERS).params(tenantId).query((rs, i) -> dossier(rs)).list();
    }

    @Override
    public Optional<ReglagesPaie> reglages(String tenantId) {
        return jdbc.sql("SELECT tauxFss, tauxCnesst, majorationSupplementaire, validationTempsExigee FROM paie_reglages WHERE tenantId = ?").params(tenantId)
                .query((rs, i) -> new ReglagesPaie(decimal(rs, "tauxFss"), decimal(rs, "tauxCnesst"), decimal(rs, "majorationSupplementaire"),
                        rs.getInt("validationTempsExigee") == 1))
                .optional();
    }

    @Override
    public List<CyclePaie> cycles(String tenantId) {
        return jdbc.sql(CYCLES + " ORDER BY datePaie DESC, prepareLe DESC").params(tenantId).query((rs, i) -> cycle(rs)).list();
    }

    @Override
    public Optional<CyclePaie> cycle(String tenantId, String id) {
        return jdbc.sql(CYCLES + " AND id = ?").params(tenantId, id).query((rs, i) -> cycle(rs)).optional();
    }

    @Override
    public List<Bulletin> bulletins(String tenantId, String cycleId) {
        return jdbc.sql(BULLETINS + " WHERE b.tenantId = ? AND b.cycleId = ? ORDER BY b.nom").params(tenantId, cycleId)
                .query((rs, i) -> bulletin(rs)).list();
    }

    @Override
    public List<Bulletin> bulletinsTraites(String tenantId, int annee) {
        return jdbc.sql(TRAITES + " AND substr(b.datePaie, 1, 4) = ? ORDER BY b.datePaie, b.nom")
                .params(tenantId, EtatCycle.TRAITE.code(), String.valueOf(annee)).query((rs, i) -> bulletin(rs)).list();
    }

    @Override
    public List<Bulletin> bulletinsTraitesDe(String tenantId, String ressourceId) {
        return jdbc.sql(TRAITES + " AND b.ressourceId = ? ORDER BY b.datePaie DESC")
                .params(tenantId, EtatCycle.TRAITE.code(), ressourceId).query((rs, i) -> bulletin(rs)).list();
    }

    // ── Écriture ─────────────────────────────────────────────────────────────

    @Override
    public void enregistrerDossier(Trace t, DossierRemuneration d) {
        jdbc.sql("INSERT INTO paie_dossiers (tenantId, ressourceId, mode, frequence, tauxHoraire, salaireAnnuel, montantPersonnelFederal, "
                        + "montantPersonnelQuebec, echelon, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId, ressourceId) DO UPDATE SET "
                        + "mode = excluded.mode, frequence = excluded.frequence, tauxHoraire = excluded.tauxHoraire, salaireAnnuel = excluded.salaireAnnuel, "
                        + "montantPersonnelFederal = excluded.montantPersonnelFederal, montantPersonnelQuebec = excluded.montantPersonnelQuebec, "
                        + "echelon = excluded.echelon, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(t.tenantId(), d.ressourceId(), d.mode().code(), d.frequence().code(), texte(d.tauxHoraire()), texte(d.salaireAnnuel()),
                        texte(d.montants().federal()), texte(d.montants().quebec()), d.echelon(), t.instant(), t.acteur())
                .update();
    }

    @Override
    public void enregistrerReglages(Trace t, ReglagesPaie r) {
        jdbc.sql("INSERT INTO paie_reglages (tenantId, tauxFss, tauxCnesst, majorationSupplementaire, validationTempsExigee, modifieLe, modifiePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (tenantId) DO UPDATE SET tauxFss = excluded.tauxFss, tauxCnesst = excluded.tauxCnesst, "
                        + "majorationSupplementaire = excluded.majorationSupplementaire, validationTempsExigee = excluded.validationTempsExigee, "
                        + "modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(t.tenantId(), texte(r.tauxFss()), texte(r.tauxCnesst()), texte(r.majorationSupplementaire()), r.validationTempsExigee() ? 1 : 0,
                        t.instant(), t.acteur())
                .update();
    }

    @Override
    public void creerCycle(String tenantId, CyclePaie c) {
        jdbc.sql("INSERT INTO paie_cycles (id, tenantId, debut, fin, datePaie, etat, prepareLe, preparePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(c.id(), tenantId, c.debut(), c.fin(), c.datePaie(), c.etat().code(), c.prepareLe(), c.preparePar()).update();
    }

    @Override
    public void remplacerBulletins(String tenantId, String cycleId, List<Bulletin> bulletins) {
        jdbc.sql("DELETE FROM paie_bulletins WHERE tenantId = ? AND cycleId = ?").params(tenantId, cycleId).update();
        bulletins.forEach(b -> jdbc.sql("INSERT INTO paie_bulletins (id, tenantId, cycleId, ressourceId, matricule, nom, datePaie, heuresRegulieres, "
                        + "heuresSupplementaires, calcul, feuilles, gains) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(b.id(), tenantId, cycleId, b.ressourceId(), b.matricule(), b.nom(), b.datePaie(), texte(b.heuresRegulieres()),
                        texte(b.heuresSupplementaires()), json.writeValueAsString(b.retenues()), json.writeValueAsString(b.feuilles()),
                        json.writeValueAsString(b.gains()))
                .update());
    }

    /** Le traitement note qui et quand ; l'annulation ne change que l'état. */
    @Override
    public void changerEtat(Trace t, String cycleId, EtatCycle etat) {
        boolean traite = etat == EtatCycle.TRAITE;
        jdbc.sql("UPDATE paie_cycles SET etat = ?, traiteLe = ?, traitePar = ? WHERE tenantId = ? AND id = ?")
                .params(etat.code(), traite ? t.instant() : null, traite ? t.acteur() : null, t.tenantId(), cycleId).update();
    }

    // ── Lignes ───────────────────────────────────────────────────────────────

    private static DossierRemuneration dossier(ResultSet rs) throws SQLException {
        return new DossierRemuneration(rs.getString("ressourceId"), ModeRemuneration.de(rs.getString("mode")), Frequence.de(rs.getString("frequence")),
                decimal(rs, "tauxHoraire"), decimal(rs, "salaireAnnuel"),
                new CalculRetenues.MontantsPersonnels(decimal(rs, "montantPersonnelFederal"), decimal(rs, "montantPersonnelQuebec")), entier(rs, "echelon"));
    }

    private static CyclePaie cycle(ResultSet rs) throws SQLException {
        return new CyclePaie(rs.getString("id"), rs.getString("debut"), rs.getString("fin"), rs.getString("datePaie"), EtatCycle.de(rs.getString("etat")),
                rs.getString("prepareLe"), rs.getString("preparePar"), rs.getString("traiteLe"), rs.getString("traitePar"));
    }

    private Bulletin bulletin(ResultSet rs) throws SQLException {
        return new Bulletin(rs.getString("id"), rs.getString("cycleId"), rs.getString("ressourceId"), rs.getString("matricule"), rs.getString("nom"),
                rs.getString("datePaie"), decimal(rs, "heuresRegulieres"), decimal(rs, "heuresSupplementaires"),
                json.readValue(rs.getString("calcul"), Retenues.class), json.readValue(rs.getString("feuilles"), LISTE), gains(rs.getString("gains")));
    }

    /** Un bulletin d'avant le changeset 0018 n'a pas de gains détaillés : son brut reste dans son calcul. */
    private List<Gain> gains(String texte) {
        return texte == null ? List.of() : json.readValue(texte, GAINS);
    }

    private static Integer entier(ResultSet rs, String colonne) throws SQLException {
        int v = rs.getInt(colonne);
        return rs.wasNull() ? null : v;
    }

    private static BigDecimal decimal(ResultSet rs, String colonne) throws SQLException {
        String v = rs.getString(colonne);
        return v == null ? null : new BigDecimal(v);
    }

    private static String texte(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}
