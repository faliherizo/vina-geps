package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.application.requetes.VuesRegles;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.ContenuRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReglesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FicheRegle;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PorteeRegle;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Regle;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.VersionRegle;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.Map;
import java.util.Optional;

/**
 * Le paramétrage des règles (lot R2) : créer une règle et sa première version, publier une version datée,
 * retirer une règle. Rien ne s'applique au passé — une règle qui changerait des décisions déjà rendues les
 * rendrait injustifiables — et chaque geste est journalisé dans la transaction du contrôleur.
 */
public class GestionnaireRegles {

    private static final String OBJET = "regle";
    private static final String CONTEXTE = "contexte";
    private static final String CLE = "cle";
    private static final String PORTEE_TYPE = "porteeType";
    private static final String PORTEE_CODE = "porteeCode";
    private static final String EFFECTIF_DEPUIS = "effectifDepuis";
    private static final String DEPUIS = "depuis";
    private static final String DATE = "date";

    private final DepotReglesPort depot;
    private final LectureRegles lecture;
    private final LectureReferentiels referentiels;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireRegles(DepotReglesPort depot, LectureRegles lecture, LectureReferentiels referentiels, JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.lecture = lecture;
        this.referentiels = referentiels;
        this.journal = journal;
        this.sequence = sequence;
    }

    /** Crée une règle et sa première version ; elle ne peut pas prendre effet avant aujourd'hui. */
    public Map<String, Object> creer(Contexte ctx, Map<String, Object> entree) {
        ContexteRegle contexte = ContexteRegle.de(entree.get(CONTEXTE));
        String cle = Regles.cleValide(entree.get(CLE));
        Object brutPortee = entree.get(PORTEE_TYPE);
        PorteeRegle portee = PorteeRegle.de(brutPortee == null ? PorteeRegle.ORGANISME.code() : brutPortee);
        String depuis = debut(ctx, entree.get(EFFECTIF_DEPUIS));
        Regle candidate = new Regle(null, contexte, cle, portee, porteeValide(ctx, portee, entree.get(PORTEE_CODE), depuis));
        ContenuRegle contenu = ContenuRegle.valider(contexte, entree);
        if (depot.existe(ctx.tenantId(), candidate)) {
            throw new ConflitException("une règle « " + cle + " » existe déjà pour cette portée : publiez-en une nouvelle version");
        }
        Regle regle = candidate.avecId(sequence.suivant("ergl"));
        depot.creer(ctx, regle);
        depot.insererVersion(ctx, new VersionRegle(sequence.suivant("ergv"), regle, 1, contenu, depuis, null));
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("regle.creee", OBJET, regle.id(), null, null, VuesRegles.creation(regle, contenu, depuis)));
        return lecture.regle(ctx, regle.id());
    }

    private static String debut(Contexte ctx, Object brut) {
        String depuis = vide(brut) ? ctx.aujourdhui() : Referentiels.dateValide(brut, EFFECTIF_DEPUIS);
        if (depuis.compareTo(ctx.aujourdhui()) < 0) {
            throw new EffectifsInvalideException("une règle ne peut pas prendre effet dans le passé (" + depuis + ")");
        }
        return depuis;
    }

    /** Le code de portée, en vigueur dans son référentiel à la date de prise d'effet ; nul pour l'organisme. */
    private String porteeValide(Contexte ctx, PorteeRegle portee, Object code, String date) {
        Optional<TypeReferentiel> referentiel = portee.referentiel();
        if (referentiel.isEmpty()) {
            if (!vide(code)) {
                throw new EffectifsInvalideException("une règle d'organisme ne porte pas de code de portée");
            }
            return null;
        }
        String c = code instanceof String s ? s.trim() : "";
        referentiels.exigerValeur(ctx.tenantId(), referentiel.get(), c, date);
        return c;
    }

    /** Publie une nouvelle version à partir d'une date (jamais passée) : la précédente se ferme ce jour-là. */
    public Map<String, Object> publierVersion(Contexte ctx, String id, Map<String, Object> entree) {
        FicheRegle fiche = lecture.fiche(ctx, id);
        String depuis = Referentiels.dateValide(entree.get(DEPUIS), DEPUIS);
        String fermerId = Referentiels.planifierRemplacement(fiche.versions(), depuis, ctx.aujourdhui());
        ContenuRegle contenu = ContenuRegle.valider(fiche.regle().contexte(), entree);
        VersionRegle avant = Referentiels.versionOuverte(fiche.versions()).orElseThrow();
        int numero = fiche.versions().size() + 1;
        depot.fermerVersion(ctx.tenantId(), fermerId, depuis);
        depot.insererVersion(ctx, new VersionRegle(sequence.suivant("ergv"), fiche.regle(), numero, contenu, depuis, null));
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("regle.version", OBJET, id, null, VuesRegles.version(avant),
                VuesRegles.nouvelleVersion(contenu, depuis, numero)));
        return lecture.regle(ctx, id);
    }

    /** Retire une règle à une date (défaut : aujourd'hui) : elle cesse de s'appliquer, ses décisions passées restent tracées. */
    public Map<String, Object> retirer(Contexte ctx, String id, Map<String, Object> entree) {
        FicheRegle fiche = lecture.fiche(ctx, id);
        Object brut = entree.get(DATE);
        String date = vide(brut) ? ctx.aujourdhui() : Referentiels.dateValide(brut, DATE);
        String fermerId = Referentiels.planifierFermeture(fiche.versions(), date, ctx.aujourdhui());
        depot.fermerVersion(ctx.tenantId(), fermerId, date);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("regle.retiree", OBJET, id, null, null, Map.of(DATE, date)));
        return lecture.regle(ctx, id);
    }

    private static boolean vide(Object brut) {
        return brut == null || "".equals(brut);
    }
}
