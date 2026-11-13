package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.FeuilleDetailleeTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.RelevesEffectifs;
import ca.vina.nexu.effectifs.domaine.RelevesEffectifs.PersonneLue;
import ca.vina.nexu.effectifs.domaine.RelevesEffectifs.QuartLu;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <strong>Les fournisseurs de faits des relevés d'effectifs</strong> (P-RF-1) — jumeau de
 * {@code backend/src/effectifs/releves/faits.ts}.
 *
 * <p>Chaque source rend les faits d'une période, <b>bornés au périmètre</b> d'unités du demandeur (niveau
 * LECTURE ; un administrateur voit tout ; refus par défaut) et calculés par les <b>mêmes</b> lectures que
 * les écrans : la feuille de temps lue ici est celle que l'écran affiche, avec ses heures supplémentaires
 * et ses écarts. Lecture seule : rien n'est écrit ni journalisé.
 */
@Component
public class FaitsRelevesEffectifs implements FournisseurFaits {

    /** Les lectures du contexte qu'un relevé réutilise. */
    public record Lectures(LectureFeuillesTemps feuilles, CalculFeuillesTemps calcul, LecturePerimetres perimetres, LectureReferentiels referentiels) {
    }

    private final JdbcClient jdbc;
    private final Lectures lectures;

    public FaitsRelevesEffectifs(JdbcClient jdbc, Lectures lectures) {
        this.jdbc = jdbc;
        this.lectures = lectures;
    }

    @Override
    public Set<String> sources() {
        return Arrays.stream(RelevesEffectifs.Source.values()).map(RelevesEffectifs.Source::code).collect(Collectors.toSet());
    }

    @Override
    public List<Map<String, Object>> faits(String source, Demandeur d, CatalogueRapports.Periode p) {
        // La source se convertit d'abord, à la frontière : un code inconnu est refusé avant toute lecture.
        RelevesEffectifs.Source s = RelevesEffectifs.Source.de(source);
        Cadre c = cadre(d, new RelevesEffectifs.Periode(p.du(), p.au()));
        return switch (s) {
            case HEURES -> heures(c);
            case JOURNEES -> journees(c);
            case POINTAGES -> pointages(c);
            case PLACES -> places(c);
            case ABSENCES -> absences(c);
        };
    }

    // ── Le cadre : contexte, périmètre, attributs rattachés à chaque fait ───────────────────

    /** Où se situe un fait : son jour, son unité, sa personne et sa place (nulles quand inconnues). */
    private record Lieu(String date, String uniteCode, String ressourceId, String placeId) {
    }

    private record Cadre(Contexte ctx, RelevesEffectifs.Periode periode, Set<String> visibles, Map<String, String> parents,
                         Map<String, PersonneLue> gens, Map<String, QuartLu> quarts) {

        boolean visible(String unite) {
            return visibles.contains(unite);
        }

        Map<String, Object> fait(Lieu l, Map<String, Object> propres) {
            Map<String, Object> m = new HashMap<>(RelevesEffectifs.axesDePeriode(l.date()));
            m.putAll(RelevesEffectifs.axesDUnite(parents, l.uniteCode()));
            m.putAll(RelevesEffectifs.axesDePersonne(l.ressourceId() == null ? null : gens.get(l.ressourceId())));
            m.putAll(RelevesEffectifs.axesDeQuart(l.placeId() == null ? null : quarts.get(l.placeId())));
            m.putAll(propres);
            return m;
        }
    }

    private static String decaler(String date, int jours) {
        return LocalDate.parse(date).plusDays(jours) + "T00:00:00.000Z";
    }

    /** Le refus par défaut : la capacité est vérifiée en amont ; ici, le relevé se borne aux unités que l'acteur voit. */
    private Cadre cadre(Demandeur d, RelevesEffectifs.Periode p) {
        Contexte ctx = new Contexte(d.tenantId(), d.acteurId(), d.role(), d.maintenant());
        Map<String, String> parents = new HashMap<>();
        lectures.referentiels().unitesEnVigueur(d.tenantId(), p.au()).forEach(u -> parents.put(u.code(), u.parent()));
        return new Cadre(ctx, p, lectures.perimetres().unitesVisibles(ctx, Perimetres.Niveau.LECTURE), parents, personnes(d.tenantId()), quarts(d.tenantId(), p));
    }

    private Map<String, PersonneLue> personnes(String tenantId) {
        Map<String, PersonneLue> m = new HashMap<>();
        jdbc.sql("SELECT id, matricule, nom, corpsEmploiCode, titreEmploiCode, statutEmploiCode, conventionCode FROM eff_ressources WHERE tenantId = ?")
                .param(tenantId).query((rs, i) -> m.put(rs.getString("id"), new PersonneLue(rs.getString("matricule"), rs.getString("nom"),
                        rs.getString("corpsEmploiCode"), rs.getString("titreEmploiCode"), rs.getString("statutEmploiCode"), rs.getString("conventionCode"))))
                .list();
        return m;
    }

    /** La nature et le milieu de chaque place des quarts de la période (le temps se rattache à sa place). */
    private Map<String, QuartLu> quarts(String tenantId, RelevesEffectifs.Periode p) {
        Map<String, QuartLu> m = new HashMap<>();
        jdbc.sql("SELECT pl.id, e.natureCode, mi.code AS milieu FROM eff_places pl JOIN eff_equipages e ON e.id = pl.equipageId AND e.tenantId = pl.tenantId "
                        + "LEFT JOIN eff_milieux mi ON mi.id = e.milieuId AND mi.tenantId = e.tenantId WHERE pl.tenantId = ? AND e.date BETWEEN ? AND ?")
                .params(tenantId, LocalDate.parse(p.du()).minusDays(1).toString(), p.au())
                .query((rs, i) -> m.put(rs.getString("id"), new QuartLu(rs.getString("natureCode"), rs.getString("milieu")))).list();
        return m;
    }

    // ── Temps ─────────────────────────────────────────────────────────────────────────────

    private List<FeuilleDetailleeTemps> feuilles(Cadre c) {
        return lectures.feuilles().lister(c.ctx(), null, null).stream()
                .filter(f -> c.visible(f.uniteCode()) && f.debut().compareTo(c.periode().au()) <= 0 && f.fin().compareTo(c.periode().du()) >= 0)
                .map(f -> lectures.calcul().lire(c.ctx(), f.id())).toList();
    }

    private List<Map<String, Object>> heures(Cadre c) {
        return feuilles(c).stream().flatMap(f -> f.segments().stream().filter(s -> c.periode().contient(s.date())).map(s -> {
            Map<String, Object> propres = new HashMap<>();
            propres.put("typeHeure", s.typeHeureCode());
            propres.put("comportementHeure", s.comportement().name());
            propres.put("origine", s.source().name());
            propres.put("statutFeuille", f.statut().name());
            propres.put("heures", RelevesEffectifs.heures(TempsEffectifs.minutesDe(s.debut(), s.fin())));
            return c.fait(new Lieu(s.date(), f.uniteCode(), f.ressourceId(), s.placeId()), propres);
        })).toList();
    }

    private List<Map<String, Object>> journees(Cadre c) {
        return feuilles(c).stream().flatMap(f -> {
            Set<String> dates = new TreeSet<>(f.heuresSupplementaires().parJour().keySet());
            f.ecarts().forEach(e -> dates.add(e.date()));
            f.segments().forEach(s -> dates.add(s.date()));
            return dates.stream().filter(c.periode()::contient).map(j -> journee(c, f, j));
        }).toList();
    }

    /** Une journée d'une feuille : le planifié, le déclaré payé, l'écart, les heures supplémentaires, les anomalies. */
    private static Map<String, Object> journee(Cadre c, FeuilleDetailleeTemps f, String date) {
        List<TempsEffectifs.EcartPlanifie> ecarts = f.ecarts().stream().filter(e -> e.date().equals(date)).toList();
        List<? extends TempsEffectifs.SegmentDeTemps> segments = f.segments().stream().filter(s -> s.date().equals(date)).toList();
        long planifie = ecarts.stream().mapToLong(TempsEffectifs.EcartPlanifie::planifie).sum();
        long declare = segments.stream().filter(s -> TempsEffectifs.estPaye(s.comportement())).mapToLong(s -> TempsEffectifs.minutesDe(s.debut(), s.fin())).sum();
        String placeId = !ecarts.isEmpty() ? ecarts.get(0).placeId()
                : segments.stream().map(TempsEffectifs.SegmentDeTemps::placeId).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        Map<String, Object> propres = new HashMap<>();
        propres.put("statutFeuille", f.statut().name());
        propres.put("journees", 1);
        propres.put("heuresPlanifiees", RelevesEffectifs.heures(planifie));
        propres.put("heuresDeclarees", RelevesEffectifs.heures(declare));
        propres.put("ecartHeures", RelevesEffectifs.heures(declare - planifie));
        propres.put("heuresSupplementaires", RelevesEffectifs.heures(f.heuresSupplementaires().parJour().getOrDefault(date, 0L)));
        propres.put("anomalies", f.anomalies().stream().filter(a -> date.equals(a.date())).count());
        return c.fait(new Lieu(date, f.uniteCode(), f.ressourceId(), placeId), propres);
    }

    // ── Présence ──────────────────────────────────────────────────────────────────────────

    private record PointageLu(String ressourceId, String placeId, String milieuId, String uniteCode, String sens, String horodatage, String origine,
                              String resultat, String ecart, String statut, String activiteCode) {
    }

    private List<Map<String, Object>> pointages(Cadre c) {
        String du = decaler(c.periode().du(), -1);
        String au = decaler(c.periode().au(), 2);
        List<PointageLu> lignes = jdbc.sql("SELECT * FROM eff_pointages WHERE tenantId = ? AND horodatage >= ? AND horodatage < ? ORDER BY horodatage")
                .params(c.ctx().tenantId(), du, au).query((rs, i) -> new PointageLu(rs.getString("ressourceId"), rs.getString("placeId"),
                        rs.getString("milieuId"), rs.getString("uniteCode"), rs.getString("sens"), rs.getString("horodatage"), rs.getString("origine"),
                        rs.getString("resultat"), rs.getString("ecart"), rs.getString("statut"), activite(rs))).list();
        Map<String, String> milieux = new HashMap<>();
        jdbc.sql("SELECT id, code FROM eff_milieux WHERE tenantId = ?").param(c.ctx().tenantId())
                .query((rs, i) -> milieux.put(rs.getString("id"), rs.getString("code"))).list();
        Map<String, List<PresenceEffectifs.PointageCourt>> externes = new HashMap<>();
        return lignes.stream().filter(p -> c.visible(p.uniteCode()) && c.periode().contient(TempsEffectifs.dateLocaleDe(p.horodatage())))
                .map(p -> pointage(c, p, milieux, externes.computeIfAbsent(p.ressourceId(), r -> externes(c.ctx().tenantId(), r, du, au)))).toList();
    }

    /**
     * L'activité d'un pointage (E-GP-15). La colonne n'existe que là où le schéma de présence l'a posée : la lire par
     * les métadonnées évite qu'un relevé échoue sur une base qui ne la porte pas encore.
     */
    private static String activite(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.ResultSetMetaData m = rs.getMetaData();
        for (int i = 1; i <= m.getColumnCount(); i++) {
            if (COLONNE_ACTIVITE.equalsIgnoreCase(m.getColumnLabel(i))) {
                return rs.getString(i);
            }
        }
        return null;
    }

    private static final String COLONNE_ACTIVITE = "activiteCode";

    private List<PresenceEffectifs.PointageCourt> externes(String tenantId, String ressourceId, String du, String au) {
        return jdbc.sql("SELECT sens, horodatage FROM eff_horodatages_externes WHERE tenantId = ? AND ressourceId = ? AND horodatage >= ? AND horodatage < ?")
                .params(tenantId, ressourceId, du, au)
                .query((rs, i) -> new PresenceEffectifs.PointageCourt(PresenceEffectifs.Sens.valueOf(rs.getString("sens")), rs.getString("horodatage"))).list();
    }

    private static Map<String, Object> pointage(Cadre c, PointageLu p, Map<String, String> milieux, List<PresenceEffectifs.PointageCourt> externes) {
        Map<String, Object> propres = new HashMap<>();
        propres.put("sens", p.sens());
        propres.put("resultat", p.resultat());
        propres.put("ecart", p.ecart());
        propres.put("origine", p.origine());
        propres.put("statut", p.statut());
        propres.put("activite", p.activiteCode());
        propres.put("pointages", 1);
        propres.put("ecarts", PresenceEffectifs.Resultat.ECART.name().equals(p.resultat()) ? 1 : 0);
        boolean corrobore = PresenceEffectifs.corrobore(new PresenceEffectifs.PointageCourt(PresenceEffectifs.Sens.valueOf(p.sens()), p.horodatage()), externes);
        propres.put("corrobores", corrobore ? 1 : 0);
        Map<String, Object> f = c.fait(new Lieu(TempsEffectifs.dateLocaleDe(p.horodatage()), p.uniteCode(), p.ressourceId(), p.placeId()), propres);
        // Le milieu **où l'on a pointé** prime sur celui du quart : c'est lui que l'écart de lieu concerne.
        if (p.milieuId() != null) {
            f.put("milieu", milieux.getOrDefault(p.milieuId(), (String) f.get("milieu")));
        }
        return f;
    }

    // ── Planification (comblement) ─────────────────────────────────────────────────────────

    private record PlaceLue(String id, String equipageId, String corpsEmploiCode, String ressourceId, String date, String uniteCode, String plageCode,
                            String modeAttribution, int minimumOperationnel, String etatPeriode, int pourvues) {
    }

    private List<Map<String, Object>> places(Cadre c) {
        List<PlaceLue> lignes = jdbc.sql("SELECT pl.id, pl.equipageId, pl.corpsEmploiCode, pl.ressourceId, e.date, e.uniteCode, e.plageCode, e.modeAttribution, "
                        + "e.minimumOperationnel, pe.etat AS etatPeriode, (SELECT COUNT(*) FROM eff_places x WHERE x.tenantId = pl.tenantId "
                        + "AND x.equipageId = pl.equipageId AND x.ressourceId IS NOT NULL) AS pourvues FROM eff_places pl "
                        + "JOIN eff_equipages e ON e.id = pl.equipageId AND e.tenantId = pl.tenantId "
                        + "JOIN eff_periodes pe ON pe.id = e.periodeId AND pe.tenantId = e.tenantId "
                        + "WHERE pl.tenantId = ? AND e.date BETWEEN ? AND ? ORDER BY e.date, pl.equipageId, pl.rang")
                .params(c.ctx().tenantId(), c.periode().du(), c.periode().au()).query(PlaceLue.class).list();
        Map<String, String> premiere = new HashMap<>();
        lignes.forEach(l -> premiere.putIfAbsent(l.equipageId(), l.id()));
        return lignes.stream().filter(l -> c.visible(l.uniteCode())).map(l -> place(c, l, premiere.get(l.equipageId()).equals(l.id()) ? 1 : 0)).toList();
    }

    /** Les mesures d'équipage ne comptent qu'une fois par équipage : sur sa première place ({@code tete}). */
    private static Map<String, Object> place(Cadre c, PlaceLue l, int tete) {
        Planification.StatutOperationnel statut = Planification.statutOperationnel(l.pourvues(), l.minimumOperationnel());
        boolean sousMinimum = statut == Planification.StatutOperationnel.SOUS_MINIMUM;
        int pourvue = l.ressourceId() == null ? 0 : 1;
        Map<String, Object> propres = new HashMap<>();
        propres.put("corpsEmploi", l.corpsEmploiCode());
        propres.put("plageHoraire", l.plageCode());
        propres.put("modeAttribution", l.modeAttribution());
        propres.put("statutOperationnel", statut.name());
        propres.put("etatPeriode", l.etatPeriode());
        propres.put("places", 1);
        propres.put("pourvues", pourvue);
        propres.put("vacantes", 1 - pourvue);
        propres.put("equipages", tete);
        propres.put("equipagesSousMinimum", sousMinimum ? tete : 0);
        return c.fait(new Lieu(l.date(), l.uniteCode(), l.ressourceId(), l.id()), propres);
    }

    // ── Absences ─────────────────────────────────────────────────────────────────────────

    private record AbsenceLue(String ressourceId, String uniteCode, String codeAbsence, String du, String au, String statut) {
    }

    private List<Map<String, Object>> absences(Cadre c) {
        Map<String, String> types = new HashMap<>();
        for (ValeurReferentiel v : lectures.referentiels().lister(c.ctx(), TypeReferentiel.CODES_ABSENCE.code(), c.periode().au(), false)) {
            types.put(v.code(), v.comportement());
        }
        List<AbsenceLue> lignes = jdbc.sql("SELECT ressourceId, uniteCode, codeAbsence, du, au, statut FROM eff_absences WHERE tenantId = ? AND du <= ? AND au >= ? ORDER BY du")
                .params(c.ctx().tenantId(), c.periode().au(), c.periode().du()).query(AbsenceLue.class).list();
        return lignes.stream().filter(a -> c.visible(a.uniteCode())).flatMap(a -> {
            List<String> jours = RelevesEffectifs.joursEntre(a.du(), a.au(), c.periode());
            // Une absence compte une fois, sur son premier jour dans la période ; ses jours, chacun.
            return IntStream.range(0, jours.size()).mapToObj(i -> {
                Map<String, Object> propres = new HashMap<>();
                propres.put("codeAbsence", a.codeAbsence());
                propres.put("typeAbsence", types.get(a.codeAbsence()));
                propres.put("statut", a.statut());
                propres.put("jours", 1);
                propres.put("absences", i == 0 ? 1 : 0);
                return c.fait(new Lieu(jours.get(i), a.uniteCode(), a.ressourceId(), null), propres);
            });
        }).toList();
    }
}
