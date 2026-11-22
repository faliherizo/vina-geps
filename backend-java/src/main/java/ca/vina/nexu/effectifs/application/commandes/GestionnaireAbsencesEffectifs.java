package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.domaine.SoldesBanquesPort;
import ca.vina.nexu.effectifs.domaine.SoldesBanques;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.PlanningAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PorteesDuDossier;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Les gestes sur les absences d'effectifs (lot R5) — jumeau de {@code declarer} et {@code decider},
 * {@code backend/src/effectifs/absences/service.ts}.
 * <ul>
 *   <li>{@link #declarer} : le code est vérifié (en vigueur, admissible au titre d'emploi), la demande est résumée en
 *   faits et soumise aux règles {@code ABSENCE} ; la décision automatique est <strong>toujours enregistrée</strong>,
 *   refus compris (E-GN-6). Acceptée, l'absence rend vacantes les places de la personne (E-CH-6).</li>
 *   <li>{@link #decider} : approuver, refuser, déroger (motif + code attribué, E-GN-5), annuler.</li>
 * </ul>
 */
public class GestionnaireAbsencesEffectifs {

    /** Le mécanisme qui vide les places d'une personne absente : c'est lui, pas l'acteur, qui les libère. */
    public static final String MECANISME_VACANCE = "vacance-sur-absence";
    private static final String OBJET = "absence";

    /** Ce que la déclaration reçoit (champs bruts de la façade : la ressource, le code, les dates, un motif). */
    public record Declaration(Object ressourceId, Object codeAbsence, Object du, Object au, Object motif) { }

    /** Une absence après un geste, les places que ce geste a rendues vacantes, et ce que le comblement en a fait aussitôt. */
    public record Resultat(AbsenceEffectifs absence, int placesLiberees, AbsencesEffectifs.BilanComblement comblement) {
        public Map<String, Object> vue() {
            Map<String, Object> m = LectureAbsencesEffectifs.vue(absence, placesLiberees);
            m.put("comblement", comblement.vue());
            return m;
        }
    }

    /** Les lectures dont les gestes ont besoin. */
    /** {@code soldes} : les soldes des banques reçus de l'organisme (AD-06b), lus pour les faits de la demande. */
    public record Lectures(LectureAbsencesEffectifs absences, LectureRessources ressources, LectureReferentiels referentiels, LecturePerimetres perimetres,
                           SoldesBanquesPort soldes) { }

    /** Les ports d'écriture : les absences, les places qu'une absence libère, et le mécanisme qui les comble (lot U5). */
    public record Ports(DepotAbsencesEffectifsPort absences, PlanningAbsencesEffectifsPort planning, DepotEquipagesPort equipages,
                        GestionnaireComblementAbsences comblement) { }

    private final Ports ports;
    private final Lectures lire;
    private final EvaluateurRegles regles;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireAbsencesEffectifs(Ports ports, Lectures lire, EvaluateurRegles regles, JournalEffectifsPort journal, Sequence sequence) {
        this.ports = ports;
        this.lire = lire;
        this.regles = regles;
        this.journal = journal;
        this.sequence = sequence;
    }

    public Resultat declarer(Contexte ctx, Declaration entree) {
        Ressource r = entree.ressourceId() instanceof String id && !id.isEmpty() ? lire.ressources().exiger(ctx, id) : lire.ressources().exigerDeLActeur(ctx);
        lire.ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.PLANIFICATION);
        String du = Referentiels.dateValide(entree.du(), "du");
        String au = Referentiels.dateValide(entree.au() == null ? entree.du() : entree.au(), "au");
        AbsencesEffectifs.bornesValides(du, au);
        ValeurReferentiel code = codeAdmis(ctx, r, entree.codeAbsence(), du);
        AbsencesEffectifs.verifierPlanifiable(code.code(), code.attributs().get("planifiable"), AbsencesEffectifs.joursPreavis(ctx.aujourdhui(), du), du);
        refuserChevauchement(ctx, r, du, au);
        String id = sequence.suivant("eabs");
        Regles.Verdict verdict = regles.evaluerDossier(ctx, dossier(ctx, r, code, id, du, au));
        AbsencesEffectifs.Statut statut = AbsencesEffectifs.statutDepuisAction(verdict.action());
        ports.absences().inserer(ctx.tenantId(), new AbsenceEffectifs(id, r.id(), null, r.uniteCode(), code.code(), du, au,
                AbsencesEffectifs.motifLibre(entree.motif()), statut, verdict.action(), verdict.motifs(),
                new AbsenceEffectifs.Trace(ctx.maintenant(), ctx.acteurId(), null, null, null, null, null)));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("codeAbsence", code.code());
        apres.put("du", du);
        apres.put("au", au);
        apres.put("statut", statut.code());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("absence.declaree", OBJET, id, r.uniteCode(), null, apres));
        return apresGeste(ctx, id, statut == AbsencesEffectifs.Statut.ACCEPTEE);
    }

    private ValeurReferentiel codeAdmis(Contexte ctx, Ressource r, Object brut, String du) {
        ValeurReferentiel code = lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CODES_ABSENCE, brut instanceof String s ? s.trim() : "", du);
        List<String> titres = code.attributs().get("titres") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
        if (!AbsencesEffectifs.codeAdmissible(titres, r.titreEmploiCode())) {
            throw new EffectifsInvalideException("le code « " + code.code() + " » n'est pas admissible pour le titre d'emploi "
                    + Optional.ofNullable(r.titreEmploiCode()).orElse("(aucun)") + " : titres admis " + String.join(", ", titres));
        }
        return code;
    }

    private void refuserChevauchement(Contexte ctx, Ressource r, String du, String au) {
        ports.absences().deLaRessource(ctx.tenantId(), r.id()).stream()
                .filter(a -> a.statut().estActive() && AbsencesEffectifs.chevauche(a, du, au)).findFirst()
                .ifPresent(a -> {
                    throw new ConflitException("une absence couvre déjà une partie de ces dates (" + a.id() + ") : annulez-la ou modifiez-la");
                });
    }

    /** Les faits du solde de la banque que nomme le code (AD-06b) : toujours présents, faux et zéro sans solde reçu. */
    private Map<String, Object> faitsDuSolde(Contexte ctx, Ressource r, ValeurReferentiel code, int joursDemandes) {
        Optional<SoldesBanques.Solde> solde = SoldesBanques.FaitsSolde.banqueDe(code.attributs()).flatMap(b -> lire.soldes().solde(ctx.tenantId(), r.id(), b));
        SoldesBanques.FaitsSolde f = SoldesBanques.FaitsSolde.de(solde, joursDemandes, SoldesBanques.FaitsSolde.heuresParJourDe(code.attributs()));
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("soldeBanqueConnu", f.connu());
        faits.put("soldeBanqueHeures", f.soldeHeures());
        faits.put("heuresDemandees", f.heuresDemandees());
        faits.put("soldeBanqueApresHeures", f.soldeApresHeures());
        return faits;
    }

    /** La demande résumée en faits, pour les règles {@code ABSENCE} : ce que le moteur juge, sans lire aucune table. */
    private Regles.Dossier dossier(Contexte ctx, Ressource r, ValeurReferentiel code, String id, String du, String au) {
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("codeAbsence", code.code());
        faits.put("dureeAbsence", code.attributs().get("duree") == null ? "" : String.valueOf(code.attributs().get("duree")));
        int preavis = AbsencesEffectifs.joursPreavis(ctx.aujourdhui(), du);
        faits.put("joursPreavis", preavis);
        faits.put("joursDemandes", AbsencesEffectifs.joursDemandes(du, au));
        faits.put("absencesSimultanees", ports.absences().accepteesSimultanees(ctx.tenantId(), r.uniteCode(), du, au));
        faits.put("tauxComblementApres", lire.absences().calculerImpact(ctx, r, du, au).tauxApres());
        faits.put("titreEmploi", r.titreEmploiCode() == null ? "" : r.titreEmploiCode());
        faits.put("premierJour", du);
        faits.put("planifiable", AbsencesEffectifs.codePlanifiable(code.attributs().get("planifiable")));
        faits.put("absencePlanifiee", AbsencesEffectifs.estPlanifiee(preavis));
        faitsDuSolde(ctx, r, code, AbsencesEffectifs.joursDemandes(du, au)).forEach(faits::put);
        Map<String, String> portees = new HashMap<>();
        portees.put(PorteesDuDossier.CONVENTION, r.conventionCode());
        portees.put(PorteesDuDossier.STATUT_EMPLOI, r.statutEmploiCode());
        portees.put(PorteesDuDossier.CORPS_EMPLOI, r.corpsEmploiCode());
        portees.put(PorteesDuDossier.TITRE_EMPLOI, r.titreEmploiCode());
        return new Regles.Dossier(ContexteRegle.ABSENCE, OBJET, id, du, r.uniteCode(), faits, portees);
    }

    /**
     * Une décision humaine : l'approbation et le refus d'une absence en attente, la dérogation d'un refus automatique
     * (niveau GESTION), l'annulation (par la personne ou son planificateur). Une absence qui devient acceptée libère
     * aussitôt les places.
     */
    public Resultat decider(Contexte ctx, String id, AbsencesEffectifs.Decision decision, Map<String, Object> entree) {
        AbsenceEffectifs a = lire.absences().exiger(ctx, id);
        exigerDroitDeDecider(ctx, a, decision);
        AbsencesEffectifs.Statut vers = AbsencesEffectifs.transition(a.statut(), decision);
        AbsencesEffectifs.Derogation derogation = decision == AbsencesEffectifs.Decision.DEROGER ? AbsencesEffectifs.derogationDe(entree) : null;
        String codeAbsence = derogation == null ? a.codeAbsence()
                : lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CODES_ABSENCE, derogation.codeAbsence(), a.du()).code();
        String motif = derogation == null ? AbsencesEffectifs.motifLibre(entree.get("motif")) : derogation.motif();
        ports.absences().decider(ctx.tenantId(), id, new DepotAbsencesEffectifsPort.DecisionEnregistree(vers, codeAbsence, ctx.maintenant(), ctx.acteurId(),
                motif, derogation));
        journaliserDecision(ctx, a, decision, vers, codeAbsence, motif);
        return apresGeste(ctx, id, vers == AbsencesEffectifs.Statut.ACCEPTEE);
    }

    private void exigerDroitDeDecider(Contexte ctx, AbsenceEffectifs a, AbsencesEffectifs.Decision decision) {
        Ressource r = lire.ressources().exiger(ctx, a.ressourceId());
        if (decision == AbsencesEffectifs.Decision.ANNULER) {
            lire.ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.PLANIFICATION);
        } else {
            lire.perimetres().exiger(ctx, a.uniteCode(), Perimetres.Niveau.GESTION);
        }
    }

    private void journaliserDecision(Contexte ctx, AbsenceEffectifs a, AbsencesEffectifs.Decision decision, AbsencesEffectifs.Statut vers, String code,
                                     String motif) {
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("statut", a.statut().code());
        avant.put("codeAbsence", a.codeAbsence());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("statut", vers.code());
        apres.put("codeAbsence", code);
        apres.put("motif", motif);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("absence." + decision.name().toLowerCase(Locale.ROOT), OBJET, a.id(), a.uniteCode(), avant, apres));
    }

    private AbsenceEffectifs relire(Contexte ctx, String id) {
        return ports.absences().lire(ctx.tenantId(), id).orElseThrow();
    }

    /**
     * Rend vacantes les places de la personne pendant l'absence acceptée. Le geste est celui du mécanisme,
     * journalisé comme tel : l'employé qui déclare n'a pas, lui, le droit de libérer une place.
     */
    private List<AbsencesEffectifs.PlaceTenue> rendreVacantes(Contexte ctx, AbsenceEffectifs a) {
        List<AbsencesEffectifs.PlaceTenue> places = ports.planning().placesTenues(ctx.tenantId(), a.ressourceId(), a.du(), a.au());
        places.forEach(p -> ports.equipages().liberer(ctx.tenantId(), p.id()));
        if (!places.isEmpty()) {
            Map<String, Object> avant = new LinkedHashMap<>();
            avant.put("ressourceId", a.ressourceId());
            journal.ecrire(ctx, new JournalEffectifs.Consignation("places.vacantes", OBJET, a.id(), a.uniteCode(), avant,
                    Map.of("places", places.stream().map(AbsencesEffectifs.PlaceTenue::id).toList()), MECANISME_VACANCE));
        }
        return places;
    }

    /**
     * Après l'écriture : l'absence accordée — par les règles ou par une autorisation manuelle — rend ses places vacantes
     * et déclenche aussitôt leur comblement (E-GN-6).
     */
    private Resultat apresGeste(Contexte ctx, String id, boolean accordee) {
        AbsenceEffectifs a = relire(ctx, id);
        List<AbsencesEffectifs.PlaceTenue> liberees = accordee ? rendreVacantes(ctx, a) : List.of();
        return new Resultat(a, liberees.size(), ports.comblement().combler(ctx, a, liberees));
    }
}
