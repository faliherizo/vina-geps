package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureCongesAnnuels;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.CongesAnnuels;
import ca.vina.nexu.effectifs.domaine.CongesAnnuelsPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <strong>Mener une campagne de choix des congés</strong> (addenda 8, M-02) : l'encadrement de l'unité la crée, l'ouvre et
 * la ferme ; chacun du groupe classe ses semaines ; l'octroi suit l'ancienneté sous les plafonds, puis chaque semaine
 * octroyée est <em>déclarée</em> comme absence — les règles de l'organisme, soldes de banque compris, la jugent encore.
 */
public class GestionnaireCongesAnnuels {

    private static final String OBJET = "conges";
    private static final String MOTIF_ABSENCE = "Congé annuel — ";

    /** Ce que le gestionnaire lit. */
    public record Lectures(LectureCongesAnnuels campagnes, LectureRessources ressources, LectureReferentiels referentiels, LecturePerimetres perimetres) { }

    /** Ce qu'il écrit : la campagne, le journal, et les absences par la déclaration habituelle. */
    public record Ecritures(CongesAnnuelsPort depot, JournalEffectifsPort journal, Sequence sequence, GestionnaireAbsencesEffectifs absences) { }

    private final Lectures lire;
    private final Ecritures ecrire;

    public GestionnaireCongesAnnuels(Lectures lire, Ecritures ecrire) {
        this.lire = lire;
        this.ecrire = ecrire;
    }

    public CongesAnnuels.Campagne creer(Contexte ctx, Map<String, Object> entree) {
        CongesAnnuels.Campagne c = CongesAnnuels.saisie(ecrire.sequence().suivant("ecng"), entree);
        lire.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, c.uniteCode(), c.du());
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CORPS_EMPLOI, c.corpsEmploiCode(), c.du());
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CODES_ABSENCE, c.codeAbsence(), c.du());
        ecrire.depot().enregistrer(ctx.tenantId(), c);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("conges.campagne.creee", OBJET, c.id(), c.uniteCode(), null, c));
        return c;
    }

    public CongesAnnuels.Campagne changerEtat(Contexte ctx, String id, Map<String, Object> entree) {
        CongesAnnuels.Campagne avant = gerable(ctx, id);
        CongesAnnuels.Campagne apres = avant.avec(avant.etat().vers(CongesAnnuels.Etat.de(entree.get("etat"))));
        ecrire.depot().enregistrer(ctx.tenantId(), apres);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("conges.campagne.etat", OBJET, id, apres.uniteCode(), avant.etat().name(), apres.etat().name()));
        return apres;
    }

    /** La personne classe ses semaines ; une nouvelle saisie remplace la précédente tant que les choix sont ouverts. */
    public List<CongesAnnuels.Choix> choisir(Contexte ctx, String id, Map<String, Object> entree) {
        Ressource r = lire.ressources().exigerDeLActeur(ctx);
        CongesAnnuels.Campagne c = lire.campagnes().exiger(ctx, id);
        if (!c.visePour(r)) {
            throw new ca.vina.nexu.effectifs.domaine.HorsPerimetreException("« " + c.nom() + " » ne vise pas votre unité ni votre corps d'emploi");
        }
        List<CongesAnnuels.Choix> choix = CongesAnnuels.choix(c, r.id(), entree.get("semaines"));
        ecrire.depot().remplacerChoix(ctx.tenantId(), c.id(), r.id(), choix);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("conges.choix", OBJET, c.id() + ":" + r.id(), c.uniteCode(), null,
                choix.stream().map(CongesAnnuels.Choix::semaine).toList()));
        return choix;
    }

    /** L'octroi d'une campagne fermée : par ancienneté, sous les plafonds ; chaque semaine octroyée devient une absence déclarée. */
    public Map<String, Object> octroyer(Contexte ctx, String id) {
        CongesAnnuels.Campagne c = gerable(ctx, id);
        if (c.etat() != CongesAnnuels.Etat.FERMEE) {
            throw new ca.vina.nexu.effectifs.domaine.ConflitException("seule une campagne aux choix fermés s'octroie (elle est « " + c.etat().name() + " »)");
        }
        List<CongesAnnuels.Candidat> candidats = lire.ressources().lister(ctx, c.uniteCode(), c.corpsEmploiCode()).stream()
                .map(r -> new CongesAnnuels.Candidat(r.id(), r.nom(), r.anciennete())).toList();
        List<CongesAnnuels.Choix> decides = CongesAnnuels.octroi(c, candidats, ecrire.depot().choix(ctx.tenantId(), id)).stream()
                .map(x -> x.statut() == CongesAnnuels.StatutChoix.OCTROYE ? declarer(ctx, c, x) : x).toList();
        decides.forEach(x -> ecrire.depot().deciderChoix(ctx.tenantId(), id, x));
        CongesAnnuels.Campagne octroyee = c.avec(CongesAnnuels.Etat.OCTROYEE);
        ecrire.depot().enregistrer(ctx.tenantId(), octroyee);
        Map<CongesAnnuels.StatutChoix, Long> bilan = decides.stream().collect(Collectors.groupingBy(CongesAnnuels.Choix::statut, Collectors.counting()));
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("conges.campagne.octroyee", OBJET, id, c.uniteCode(), null, bilan));
        return Map.of("campagne", octroyee, "octroyes", bilan.getOrDefault(CongesAnnuels.StatutChoix.OCTROYE, 0L),
                "refuses", bilan.getOrDefault(CongesAnnuels.StatutChoix.REFUSE, 0L));
    }

    /** La semaine octroyée passe par la déclaration d'absence ; si les règles la refusent, le choix est refusé avec leur motif. */
    private CongesAnnuels.Choix declarer(Contexte ctx, CongesAnnuels.Campagne c, CongesAnnuels.Choix x) {
        try {
            AbsencesEffectifs.Statut s = ecrire.absences().declarer(ctx, new GestionnaireAbsencesEffectifs.Declaration(x.ressourceId(), c.codeAbsence(), x.semaine(),
                    CongesAnnuels.finDeSemaine(x.semaine()), MOTIF_ABSENCE + c.nom())).absence().statut();
            return switch (s) {
                case ACCEPTEE -> x;
                case A_APPROUVER -> x.decide(CongesAnnuels.StatutChoix.OCTROYE, "absence à approuver selon les règles");
                case REFUSEE_AUTO, REFUSEE, ANNULEE -> x.decide(CongesAnnuels.StatutChoix.REFUSE, "refusée par les règles de l'organisme");
            };
        } catch (RuntimeException e) {
            return x.decide(CongesAnnuels.StatutChoix.REFUSE, e.getMessage());
        }
    }

    private CongesAnnuels.Campagne gerable(Contexte ctx, String id) {
        CongesAnnuels.Campagne c = lire.campagnes().exiger(ctx, id);
        lire.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        return c;
    }
}
