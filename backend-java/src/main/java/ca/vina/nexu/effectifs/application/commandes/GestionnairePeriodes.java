package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPeriodesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PlanificateurFinDeCollecte;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les périodes de planification (E-GR-10, E-GR-11, E-GR-1, P-GH-4) — jumeau de {@code servicePeriodes.ts} : ouvertes
 * et fermées sous contrôle, jamais ramenées en arrière ; la fin de collecte se planifie à l'ordonnanceur.
 */
public class GestionnairePeriodes {

    /**
     * Sans ordonnanceur, la collecte ne se ferme que par le geste du planificateur : c'est le câblage des essais d'autres
     * lots, qui ne passent jamais par la fin de collecte automatique. Le câblage applicatif passe toujours le vrai port.
     */
    private static final String OBJET = "periode";
    private static final PlanificateurFinDeCollecte SANS_ORDONNANCEUR = new PlanificateurFinDeCollecte() {
        @Override
        public void planifier(String tenantId, Echeance e) {
            // Rien à planifier : aucun ordonnanceur n'est câblé.
        }

        @Override
        public void annuler(String tenantId, String periodeId) {
            // Rien à annuler : aucun ordonnanceur n'est câblé.
        }
    };

    /** Le socle qu'empruntent les périodes : référentiels, périmètres, journal, identifiants. */
    public record Socle(LectureReferentiels referentiels, LecturePerimetres perimetres, JournalEffectifsPort journal, Sequence sequence) { }

    private final DepotPeriodesPort depot;
    private final LecturePeriodes lecture;
    private final LectureReferentiels referentiels;
    private final LecturePerimetres perimetres;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;
    private final PlanificateurFinDeCollecte finDeCollecte;

    public GestionnairePeriodes(DepotPeriodesPort depot, LecturePeriodes lecture, Socle socle, PlanificateurFinDeCollecte finDeCollecte) {
        this.depot = depot;
        this.lecture = lecture;
        this.referentiels = socle.referentiels();
        this.perimetres = socle.perimetres();
        this.journal = socle.journal();
        this.sequence = socle.sequence();
        this.finDeCollecte = finDeCollecte;
    }

    public GestionnairePeriodes(DepotPeriodesPort depot, LecturePeriodes lecture, LectureReferentiels referentiels, LecturePerimetres perimetres,
                                JournalEffectifsPort journal, Sequence sequence) {
        this(depot, lecture, new Socle(referentiels, perimetres, journal, sequence), SANS_ORDONNANCEUR);
    }

    public Periode creer(Contexte ctx, Map<String, Object> entree) {
        String nom = GestionnaireRessources.texte(entree.get("nom"));
        if (nom.isEmpty()) {
            throw new EffectifsInvalideException("« nom » est obligatoire");
        }
        String uniteCode = GestionnaireRessources.texte(entree.get("uniteCode"));
        referentiels.exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, ctx.aujourdhui());
        perimetres.exiger(ctx, uniteCode, Perimetres.Niveau.PLANIFICATION);
        String debut = Planification.dateDe(entree.get("debut"), "début");
        String fin = Planification.dateDe(entree.get("fin"), "fin");
        Planification.verifierBornes(debut, fin);
        String id = sequence.suivant("eprd");
        depot.inserer(ctx.tenantId(), id, uniteCode, nom, debut, fin, ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("periode.creee", OBJET, id, uniteCode, null, Map.of("nom", nom, "debut", debut, "fin", fin)));
        return lecture.lire(ctx, id);
    }

    /**
     * Fait avancer la période dans son cycle. Ouvrir la collecte exige sa date de fin, qui ne peut pas être
     * passée ni dépasser le début de la période : on dépose ses disponibilités <strong>avant</strong>. Ouvrir la collecte
     * <strong>planifie</strong> sa fin (P-GH-4) : au lendemain de {@code finCollecte}, l'ordonnanceur ferme la collecte et
     * attribue les quarts vacants depuis les disponibilités, sans geste de personne.
     */
    public Periode changerEtat(Contexte ctx, String id, Map<String, Object> entree) {
        Periode p = lecture.exiger(ctx, id, Perimetres.Niveau.PLANIFICATION);
        Planification.EtatPeriode vers = Planification.etatPeriodeDe(entree.get("etat"));
        Planification.verifierTransition(p.etat(), vers);
        String finCollecte = vers == Planification.EtatPeriode.COLLECTE ? finCollecteDe(ctx, p, entree.get("finCollecte")) : p.finCollecte();
        depot.changerEtat(ctx.tenantId(), id, vers, finCollecte, ctx.maintenant());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("periode.etat", OBJET, id, p.uniteCode(), Map.of("etat", p.etat()), etat(vers, finCollecte), null));
        if (vers == Planification.EtatPeriode.COLLECTE) {
            finDeCollecte.planifier(ctx.tenantId(), new PlanificateurFinDeCollecte.Echeance(id, Disponibilites.echeanceFinDeCollecte(finCollecte), finCollecte,
                    ctx.acteurId()));
        }
        // Publiée, la période ne s'attribue plus depuis les disponibilités : la tâche n'a plus d'objet.
        if (vers == Planification.EtatPeriode.PUBLIEE) {
            finDeCollecte.annuler(ctx.tenantId(), id);
        }
        return lecture.lire(ctx, id);
    }

    private static Map<String, Object> etat(Planification.EtatPeriode etat, String finCollecte) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("etat", etat);
        m.put("finCollecte", finCollecte);
        return m;
    }

    /**
     * Ferme la collecte d'elle-même (P-GH-4) : la période passe en confection, au nom du mécanisme qui le fait. Sans effet
     * sur une période qui n'est plus en collecte — le planificateur l'a devancé.
     */
    public Periode fermerCollecte(Contexte ctx, String id, String systeme) {
        Periode p = lecture.lire(ctx, id);
        if (p.etat() != Planification.EtatPeriode.COLLECTE) {
            return p;
        }
        depot.changerEtat(ctx.tenantId(), id, Planification.EtatPeriode.CONFECTION, p.finCollecte(), ctx.maintenant());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("periode.etat", OBJET, id, p.uniteCode(), Map.of("etat", p.etat()),
                etat(Planification.EtatPeriode.CONFECTION, p.finCollecte()), systeme));
        return lecture.lire(ctx, id);
    }

    private static String finCollecteDe(Contexte ctx, Periode p, Object brut) {
        String fin = Planification.dateDe(brut, "fin de la collecte");
        if (fin.compareTo(ctx.aujourdhui()) < 0) {
            throw new EffectifsInvalideException("la collecte ne peut pas se terminer dans le passé");
        }
        if (fin.compareTo(p.debut()) > 0) {
            throw new EffectifsInvalideException("la collecte doit se terminer au plus tard le premier jour de la période (" + p.debut() + ")");
        }
        return fin;
    }
}
