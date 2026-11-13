package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.FeuilleDetailleeTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort.NouveauSegment;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort.SegmentStocke;
import ca.vina.nexu.effectifs.domaine.ModificationHeures.OperationModification;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La <strong>saisie et la correction</strong> d'une feuille (E-RF-2, E-RF-3, E-RF-14, E-GP-3, E-GP-5) — jumeau de
 * {@code ajouterSegment}, {@code corrigerSegment} et {@code retirerSegment} : chaque correction est une version, jugée en
 * temps réel par les règles, motivée quand elle touche le temps d'autrui ou un temps prouvé ; verrouillée dès la soumission.
 */
public class GestionnaireSegmentsTemps {

    /** Les écritures d'une correction. */
    public record Ecritures(EcritureFeuillesTempsPort depot, JournalEffectifsPort journal, Sequence sequence) { }

    private final LectureFeuillesTemps lecture;
    private final CalculFeuillesTemps calcul;
    private final RestrictionModificationHeures restriction;
    private final Ecritures ecrire;

    public GestionnaireSegmentsTemps(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul, RestrictionModificationHeures restriction, Ecritures ecrire) {
        this.lecture = lecture;
        this.calcul = calcul;
        this.restriction = restriction;
        this.ecrire = ecrire;
    }

    /** Le temps qu'une modification touche : sa date, sa fin, son origine, et ce que la trace désigne. */
    private record Touche(OperationModification operation, String date, String fin, TempsEffectifs.Source source, String objetId) { }

    /** E-GP-3 : la modification passe par les règles MODIFICATION_HEURES de l'organisme avant d'écrire. */
    private void exigerModification(Contexte ctx, Ouverte o, Touche t) {
        FeuilleTemps f = o.feuille();
        restriction.exigerPermise(ctx, new RestrictionModificationHeures.Demande(t.operation(), lecture.ressources().exiger(ctx, f.ressourceId()),
                f.uniteCode(), t.date(), t.fin(), !o.soi(), t.source(), t.objetId()));
    }

    /** La feuille ouverte à l'écriture, et si l'acteur y écrit pour lui-même. */
    private record Ouverte(FeuilleTemps feuille, boolean soi) { }

    private Ouverte ouvrir(Contexte ctx, String feuilleId) {
        FeuilleTemps f = lecture.exigerLigne(ctx, feuilleId).feuille();
        boolean soi = lecture.exigerEcriture(ctx, f);
        TempsEffectifs.verifierModifiable(f.statut());
        return new Ouverte(f, soi);
    }

    private void verifier(Contexte ctx, FeuilleTemps f, TempsEffectifs.SegmentSaisi s) {
        if (s.date().compareTo(f.debut()) < 0 || s.date().compareTo(f.fin()) > 0) {
            throw new EffectifsInvalideException("le " + s.date() + " est hors de la période de la feuille (" + f.debut() + " au " + f.fin() + ")");
        }
        if (lecture.typesHeure(ctx, f.debut()).stream().noneMatch(t -> t.code().equals(s.typeHeureCode()))) {
            throw new EffectifsInvalideException("type d'heure « " + s.typeHeureCode() + " » inconnu ou hors vigueur");
        }
    }

    public FeuilleDetailleeTemps ajouter(Contexte ctx, String feuilleId, Map<String, Object> entree) {
        Ouverte o = ouvrir(ctx, feuilleId);
        TempsEffectifs.SegmentSaisi s = TempsEffectifs.segmentSaisi(entree);
        verifier(ctx, o.feuille(), s);
        String motif = TempsEffectifs.motifDeCorrection(entree.get("motif"), !o.soi());
        exigerModification(ctx, o, new Touche(OperationModification.AJOUT, s.date(), s.fin(), TempsEffectifs.Source.SAISIE, feuilleId));
        NouveauSegment nouveau = new NouveauSegment(null, s.date(), s.debut(), s.fin(), s.typeHeureCode(), TempsEffectifs.Source.SAISIE);
        calcul.jugerJournee(ctx, o.feuille(), null, nouveau);
        String id = ecrire.sequence().suivant("eseg");
        ecrire.depot().insererSegment(ctx, feuilleId, nouveau, new EcritureFeuillesTempsPort.Filiation(id, 1, null, motif));
        Map<String, Object> apres = saisi(s);
        apres.put("motif", motif);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.segment.ajoute", "segment", id, o.feuille().uniteCode(), null, apres));
        return calcul.lire(ctx, feuilleId);
    }

    private static Map<String, Object> saisi(TempsEffectifs.SegmentSaisi s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", s.date());
        m.put("debut", s.debut());
        m.put("fin", s.fin());
        m.put("typeHeureCode", s.typeHeureCode());
        return m;
    }

    private SegmentStocke exigerSegment(Contexte ctx, String feuilleId, String segmentId) {
        SegmentStocke s = lecture.depot().segment(ctx.tenantId(), feuilleId, segmentId)
                .orElseThrow(() -> new IntrouvableException("segment « " + segmentId + " » introuvable"));
        if (!s.actif()) {
            throw new ConflitException("ce segment a déjà été remplacé : corrigez sa dernière version");
        }
        return s;
    }

    public FeuilleDetailleeTemps corriger(Contexte ctx, String feuilleId, String segmentId, Map<String, Object> entree) {
        Ouverte o = ouvrir(ctx, feuilleId);
        SegmentStocke avant = exigerSegment(ctx, feuilleId, segmentId);
        Map<String, Object> fusion = saisi(new TempsEffectifs.SegmentSaisi(avant.date(), avant.debut(), avant.fin(), avant.typeHeureCode()));
        fusion.putAll(entree);
        TempsEffectifs.SegmentSaisi s = TempsEffectifs.segmentSaisi(fusion);
        verifier(ctx, o.feuille(), s);
        String motif = TempsEffectifs.motifDeCorrection(entree.get("motif"), !o.soi() || avant.source() == TempsEffectifs.Source.POINTAGE);
        exigerModification(ctx, o, new Touche(OperationModification.CORRECTION, avant.date(), avant.fin(), avant.source(), avant.id()));
        NouveauSegment nouveau = new NouveauSegment(avant.placeId(), s.date(), s.debut(), s.fin(), s.typeHeureCode(), avant.source(), avant.sourceExterne());
        calcul.jugerJournee(ctx, o.feuille(), avant.id(), nouveau);
        String id = ecrire.sequence().suivant("eseg");
        ecrire.depot().desactiverSegment(ctx.tenantId(), avant.id());
        // Corriger un temps proposé par une source tierce, c'est l'avoir relu : la correction le confirme (E-RF-13).
        ecrire.depot().insererSegment(ctx, feuilleId, nouveau, new EcritureFeuillesTempsPort.Filiation(id, avant.version() + 1, avant.id(), motif,
                avant.source() == TempsEffectifs.Source.EXTERNE));
        journaliserCorrection(ctx, new Correction(id, o.feuille().uniteCode(), avant, s, motif));
        return calcul.lire(ctx, feuilleId);
    }

    private record Correction(String id, String uniteCode, SegmentStocke avant, TempsEffectifs.SegmentSaisi apres, String motif) { }

    private void journaliserCorrection(Contexte ctx, Correction c) {
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("debut", c.avant().debut());
        avant.put("fin", c.avant().fin());
        avant.put("typeHeureCode", c.avant().typeHeureCode());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("debut", c.apres().debut());
        apres.put("fin", c.apres().fin());
        apres.put("typeHeureCode", c.apres().typeHeureCode());
        apres.put("motif", c.motif());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.segment.corrige", "segment", c.id(), c.uniteCode(), avant, apres));
    }

    public FeuilleDetailleeTemps retirer(Contexte ctx, String feuilleId, String segmentId, Map<String, Object> entree) {
        Ouverte o = ouvrir(ctx, feuilleId);
        SegmentStocke avant = exigerSegment(ctx, feuilleId, segmentId);
        String motif = TempsEffectifs.motifDeCorrection(entree.get("motif"), !o.soi() || avant.source() != TempsEffectifs.Source.SAISIE);
        exigerModification(ctx, o, new Touche(OperationModification.RETRAIT, avant.date(), avant.fin(), avant.source(), avant.id()));
        ecrire.depot().retirerSegment(ctx.tenantId(), avant.id(), motif);
        Map<String, Object> av = new LinkedHashMap<>();
        av.put("debut", avant.debut());
        av.put("fin", avant.fin());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("motif", motif);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.segment.retire", "segment", avant.id(), o.feuille().uniteCode(), av, apres));
        return calcul.lire(ctx, feuilleId);
    }

    /**
     * <strong>Confirmer un temps proposé par une source tierce</strong> (E-RF-13) : la personne, ou son planificateur, dit que
     * l'horodatage externe dit vrai. Rien d'autre ne change — ni les heures ni la version — : la confirmation est un constat,
     * consigné au journal à son nom.
     */
    public FeuilleDetailleeTemps confirmer(Contexte ctx, String feuilleId, String segmentId) {
        Ouverte o = ouvrir(ctx, feuilleId);
        SegmentStocke s = exigerSegment(ctx, feuilleId, segmentId);
        if (s.source() != TempsEffectifs.Source.EXTERNE) {
            throw new EffectifsInvalideException("seul un temps tiré d'un horodatage externe se confirme");
        }
        if (s.confirmeLe() != null) {
            throw new ConflitException("ce temps est déjà confirmé");
        }
        exigerModification(ctx, o, new Touche(OperationModification.CORRECTION, s.date(), s.fin(), s.source(), s.id()));
        ecrire.depot().confirmerSegment(ctx, s.id());
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("sourceExterne", s.sourceExterne());
        apres.put("debut", s.debut());
        apres.put("fin", s.fin());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.segment.confirme", "segment", s.id(), o.feuille().uniteCode(), null, apres));
        return calcul.lire(ctx, feuilleId);
    }
}
