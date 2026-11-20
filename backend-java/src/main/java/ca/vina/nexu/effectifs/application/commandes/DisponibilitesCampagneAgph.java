package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphSemaineDeposee;
import ca.vina.nexu.effectifs.domaine.AgphSemaineType;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotSemainesTypeAgphPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OptimisationBudgetAgph;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <strong>Les disponibilités de la campagne AGPH</strong> (E-CH-1) — jumeau de {@code serviceDepotsCampagne.ts}.
 *
 * <p>Pendant le dépôt (et de nouveau pendant la révision), chaque personne de la campagne émet sa <strong>semaine
 * type</strong>. Le dépôt est résumé en faits — nombre et durée des quarts offerts, fins de semaine, nuits — et en poids
 * sur le budget d'heures que le service de planification a fixé ; les règles {@code DEPOT_AGPH} de l'organisme le jugent.
 * Une règle bloquante le refuse avec ses motifs ; sinon il est enregistré avec sa conformité.
 */
public class DisponibilitesCampagneAgph {

    private static final double CENTIEMES = 100.0;
    private static final double MILLIEMES = 1000.0;
    private static final double DIXIEMES = 10.0;

    /** Ce que le dépôt emprunte hors de la campagne : ressources, périmètres, référentiels, moteur de règles, journal. */
    public record Appuis(LectureRessources ressources, LecturePerimetres perimetres, LectureReferentiels referentiels, EvaluateurRegles regles,
                         JournalEffectifsPort journal) { }

    /** Une personne de la campagne et sa semaine déposée (nulle tant qu'elle n'a rien déposé). */
    public record LigneDepot(String ressourceId, String nom, String matricule, int rang, AgphSemaineDeposee depot) { }

    /** Les dépôts de la campagne : qui a déposé, qui manque, et le total des heures offertes rapporté au budget. */
    public record DepotsDeLaCampagne(String campagneId, Agph.EtatCampagne etat, Double budgetHeuresSemaine,
                                     double heuresCampagne, Double tauxBudget, int deposes, List<LigneDepot> lignes) { }

    private final DepotSemainesTypeAgphPort depot;
    private final LectureAgph lecture;
    private final Appuis appuis;

    public DisponibilitesCampagneAgph(DepotSemainesTypeAgphPort depot, LectureAgph lecture, Appuis appuis) {
        this.depot = depot;
        this.lecture = lecture;
        this.appuis = appuis;
    }

    /** Déposer (ou corriger) la semaine type d'une personne de la campagne. {@code ressourceId} nul : soi-même. */
    public AgphSemaineDeposee deposer(Contexte ctx, String campagneId, String ressourceId, Map<String, Object> entree) {
        AgphCampagne c = ouverteAuDepot(ctx, campagneId);
        Ressource r = deposant(ctx, c, ressourceId);
        List<AgphSemaineType.PlageType> plages = AgphSemaineType.plagesTypeDe(entree.get("plages"));
        AgphSemaineType.Faits faits = AgphSemaineType.faits(plages, depot.heuresDesAutres(ctx.tenantId(), c.id(), r.id()), c.budgetHeuresSemaine());
        Regles.Verdict verdict = juger(ctx, c, r, faits);
        if (verdict.action().estBloquant()) {
            throw new RefusParRegleException(verdict.motifs().stream().map(Regles.Motif::message).collect(Collectors.joining(" ; ")), verdict);
        }
        Disponibilites.Conformite conformite = Disponibilites.conformiteDe(verdict.action());
        depot.enregistrer(ctx.tenantId(), new DepotSemainesTypeAgphPort.NouvelleSemaine(c.id(), r.id(), conformite, verdict.motifs(), faits.heuresParSemaine(),
                ctx.maintenant(), ctx.acteurId(), plages));
        journaliser(ctx, c, new Depose(r.id(), plages.size(), faits, verdict));
        return depot.lire(ctx.tenantId(), c.id(), r.id()).orElseThrow();
    }

    private AgphCampagne ouverteAuDepot(Contexte ctx, String campagneId) {
        AgphCampagne c = lecture.visible(ctx, campagneId);
        if (!c.etat().depotOuvert()) {
            throw new ConflitException("la campagne est en " + c.etat().name().toLowerCase() + " : les disponibilités se déposent pendant le dépôt ou la révision");
        }
        return c;
    }

    /**
     * Qui dépose : la personne pour elle-même, ou son planificateur pour elle (niveau PLANIFICATION de l'unité) ; dans
     * les deux cas, une personne qui figure dans la campagne.
     */
    private Ressource deposant(Contexte ctx, AgphCampagne c, String ressourceId) {
        Ressource r = ressourceId == null
                ? appuis.ressources().deLActeur(ctx).orElseThrow(() -> new EffectifsInvalideException("votre compte n'est relié à aucune ressource planifiable"))
                : appuis.ressources().exiger(ctx, ressourceId);
        if (ressourceId != null && !(r.utilisateurId() != null && r.utilisateurId().equals(ctx.acteurId()))) {
            appuis.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.PLANIFICATION);
        }
        if (lecture.rangDe(ctx, c.id(), r.id()).isEmpty()) {
            throw new EffectifsInvalideException(r.nom() + " ne figure pas dans cette campagne");
        }
        return r;
    }

    private Regles.Verdict juger(Contexte ctx, AgphCampagne c, Ressource r, AgphSemaineType.Faits faits) {
        Map<String, Object> f = faits.enCarte();
        f.put("statutEmploi", comportementDuStatut(ctx, r));
        f.put("corpsEmploi", r.corpsEmploiCode());
        return appuis.regles().evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.DEPOT_AGPH, TourDeRoleAgph.OBJET, c.id() + ":" + r.id(), ctx.aujourdhui(),
                c.uniteCode(), f, TourDeRoleAgph.portees(r)));
    }

    private String comportementDuStatut(Contexte ctx, Ressource r) {
        if (r.statutEmploiCode() == null) {
            return "";
        }
        String c = appuis.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.STATUTS_EMPLOI, r.statutEmploiCode(), ctx.aujourdhui()).comportement();
        return c == null ? "" : c;
    }

    /** Ce qu'un dépôt accepté consigne. */
    private record Depose(String ressourceId, int plages, AgphSemaineType.Faits faits, Regles.Verdict verdict) { }

    private void journaliser(Contexte ctx, AgphCampagne c, Depose d) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", d.ressourceId());
        apres.put("plages", d.plages());
        apres.put("heures", d.faits().heuresParSemaine());
        apres.put("tauxBudget", d.faits().tauxBudget());
        apres.put("conformite", Disponibilites.conformiteDe(d.verdict().action()));
        apres.put("motifs", d.verdict().motifs().stream().map(Regles.Motif::cle).toList());
        appuis.journal().ecrire(ctx, JournalEffectifs.Consignation.de("agph.disponibilites.deposees", TourDeRoleAgph.OBJET, c.id(), c.uniteCode(), null, apres));
    }

    /** Ma semaine type dans une campagne, si j'en ai déposé une. */
    public Optional<AgphSemaineDeposee> monDepot(Contexte ctx, String campagneId) {
        AgphCampagne c = lecture.visible(ctx, campagneId);
        return appuis.ressources().deLActeur(ctx).flatMap(moi -> depot.lire(ctx.tenantId(), c.id(), moi.id()));
    }

    /** Les dépôts de la campagne, pour le service de planification (niveau LECTURE de l'unité). */
    public DepotsDeLaCampagne depotsDeLaCampagne(Contexte ctx, String campagneId) {
        AgphCampagne c = lecture.visible(ctx, campagneId);
        appuis.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.LECTURE);
        List<LigneDepot> lignes = lecture.rangs(ctx, campagneId).stream().map(g -> new LigneDepot(g.ressourceId(), g.nom(), g.matricule(), g.rang(),
                depot.lire(ctx.tenantId(), campagneId, g.ressourceId()).orElse(null))).toList();
        double heures = Math.round(lignes.stream().filter(l -> l.depot() != null).mapToDouble(l -> l.depot().heures()).sum() * CENTIEMES) / CENTIEMES;
        Double budget = c.budgetHeuresSemaine();
        Double taux = budget == null ? null : Math.round(heures / budget * MILLIEMES) / DIXIEMES;
        return new DepotsDeLaCampagne(campagneId, c.etat(), budget, heures, taux, (int) lignes.stream().filter(l -> l.depot() != null).count(), lignes);
    }

    /**
     * La proposition d'optimisation du budget (E-CH-1 ; addenda 7, Q11 ; AD-04) : parmi les postes offerts à la campagne et que
     * couvre au moins une semaine type déposée, ceux qui couvrent le plus d'heures sans dépasser le budget. Rien n'est écrit.
     */
    public Map<String, Object> optimisation(Contexte ctx, String campagneId) {
        AgphCampagne c = lecture.visible(ctx, campagneId);
        appuis.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.LECTURE);
        if (c.budgetHeuresSemaine() == null) {
            throw new ConflitException("fixez d'abord le budget d'heures de la campagne : sans budget, il n'y a rien à optimiser");
        }
        List<OptimisationBudgetAgph.PosteAEvaluer> postes = lecture.postesLibres(ctx, campagneId).stream()
                .map(p -> new OptimisationBudgetAgph.PosteAEvaluer(p.id(), p.code(), p.libelle(), p.cycle().size(),
                        OptimisationBudgetAgph.quartsDuCycle(p.cycle(), code -> plage(ctx, code)))).toList();
        List<List<AgphSemaineType.PlageType>> depots = lecture.rangs(ctx, campagneId).stream()
                .flatMap(g -> depot.lire(ctx.tenantId(), campagneId, g.ressourceId()).stream()).map(AgphSemaineDeposee::plages).toList();
        return vue(OptimisationBudgetAgph.proposer(postes, depots, c.budgetHeuresSemaine()));
    }

    /** Le créneau d'une plage horaire en vigueur aujourd'hui. */
    private Planification.Plage plage(Contexte ctx, String code) {
        Map<String, Object> a = appuis.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.PLAGES_HORAIRES, code, ctx.aujourdhui()).attributs();
        return new Planification.Plage(((Number) a.get("debut")).intValue(), ((Number) a.get("fin")).intValue());
    }

    /** La forme rendue : les motifs d'écart se disent en mots, à côté de leur code. */
    static Map<String, Object> vue(OptimisationBudgetAgph.Proposition p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetHeures", p.budgetHeures());
        m.put("totalHeures", p.totalHeures());
        m.put("resteHeures", p.resteHeures());
        m.put("retenus", p.retenus().stream().map(DisponibilitesCampagneAgph::vue).toList());
        m.put("ecartes", p.ecartes().stream().map(DisponibilitesCampagneAgph::vue).toList());
        return m;
    }

    private static Map<String, Object> vue(OptimisationBudgetAgph.PosteJuge j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.id());
        m.put("code", j.code());
        m.put("libelle", j.libelle());
        m.put("heuresSemaine", j.heuresSemaine());
        m.put("candidats", j.candidats());
        m.put("motif", j.motif() == null ? null : j.motif().name());
        m.put("motifLibelle", j.motif() == null ? null : j.motif().libelle());
        return m;
    }
}
