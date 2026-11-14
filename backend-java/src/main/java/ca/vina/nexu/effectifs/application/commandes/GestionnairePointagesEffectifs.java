package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.EcriturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Milieu;
import ca.vina.nexu.effectifs.domaine.Milieux;
import ca.vina.nexu.effectifs.domaine.ModificationHeures;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.PointageEffectifs;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.RepartitionPresence;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <strong>Pointer et saisir après coup</strong> (lot R8) — jumeau de {@code pointer} et {@code saisirAPosteriori}
 * ({@code presence/service.ts}).
 * <ul>
 *   <li>{@link #pointer} : l'employé pointe <strong>son</strong> quart du moment ; le pointage est jugé aux preuves du
 *       milieu (distance, code QR, réseau) puis aux règles POINTAGE. Un écart de lieu ou une règle qui le veut met le
 *       pointage en attente d'approbation ; une règle bloquante le refuse (409, motifs nommés).</li>
 *   <li>{@link #saisirAPosteriori} : un quart non pointé se déclare en segments, avec une justification ; il attend
 *       l'approbation, sauf saisi par l'encadrement de l'unité (tracé à son nom).</li>
 * </ul>
 */
public class GestionnairePointagesEffectifs {

    private static final String OBJET = "pointage";
    /** La longueur d'une date AAAA-MM-JJ au début d'un instant ISO 8601. */
    private static final int DATE = 10;
    /** Autour d'une saisie hors quart, la fenêtre où chercher le temps déjà déclaré : assez large pour lire des paires entières. */
    private static final Duration MARGE_HORS_QUART = Duration.ofDays(1);

    /** Les lectures que le pointage emprunte. */
    public record Lectures(LecturePresenceEffectifs presence, LectureQuartsPresence quarts, LectureRessources ressources, LecturePerimetres perimetres,
                           LectureMilieux milieux, DepotMilieuxPort secrets, QualificationPointages qualification) { }

    /** Les écritures et services : la présence, le moteur de règles, le journal, les identifiants. */
    public record Ecritures(EcriturePointagesEffectifsPort depot, EvaluateurRegles regles, JournalEffectifsPort journal, Sequence sequence) { }

    /** Ce qui juge et approuve : la restriction de la modification des heures (E-GP-3) et le flux des pointages (E-GP-6). */
    public record Approbation(RestrictionModificationHeures restriction, GestionnaireFluxApprobations flux) { }

    private final Lectures lire;
    private final Ecritures ecrire;
    private final Approbation approbation;

    public GestionnairePointagesEffectifs(Lectures lire, Ecritures ecrire, Approbation approbation) {
        this.lire = lire;
        this.ecrire = ecrire;
        this.approbation = approbation;
    }

    // ── Pointer ──────────────────────────────────────────────────────────────

    public Map<String, Object> pointer(Contexte ctx, Map<String, Object> entree, String ip) {
        Ressource r = lire.ressources().exigerDeLActeur(ctx);
        PresenceEffectifs.Sens sens = PresenceEffectifs.sensDe(entree.get("sens"));
        PresenceEffectifs.QuartPointe q = lire.quarts().autourDe(ctx.tenantId(), r.id(), ctx.maintenant()).orElseThrow(() -> new ConflitException(
                "aucun quart publié ne vous attend en ce moment : un quart non pointé se déclare en saisie a posteriori"));
        lire.qualification().exigerTempsReel(ctx, r, q.date());
        List<PresenceEffectifs.PointageCourt> faits = lire.presence().actifsDeLaPlace(ctx.tenantId(), r.id(), q.placeId());
        PresenceEffectifs.verifierSequence(faits.isEmpty() ? null : faits.getLast().sens(), sens);
        String activite = lire.qualification().activiteDuPointage(ctx, entree.get("activiteCode"), sens, q.date());
        String milieuId = milieuDeclare(entree.get("milieuId"), q.milieuId());
        List<PresenceEffectifs.VerdictPreuve> verdicts = PresenceEffectifs.jugerPreuves(milieuDuQuart(ctx, milieuId),
                PresenceEffectifs.soumissionDe(entree, ip), Instant.parse(ctx.maintenant()).toEpochMilli());
        String id = ecrire.sequence().suivant("epoi");
        Map<String, Object> faitsRegles = PresenceEffectifs.faitsDuPointage(q, sens, ctx.maintenant(), verdicts);
        Regles.Verdict verdict = ecrire.regles().evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.POINTAGE, OBJET, id, q.date(), q.uniteCode(),
                faitsRegles, portees(r)));
        if (verdict.action().estBloquant()) {
            throw new RefusParRegleException(verdict.motifs().stream().map(Regles.Motif::message).collect(Collectors.joining(" ; ")), verdict);
        }
        PresenceEffectifs.Jugement j = PresenceEffectifs.resultatDe(verdicts);
        boolean enAttente = j.ecart() != null || verdict.action() == ActionRegle.EXIGER_APPROBATION;
        inserer(ctx, new EcriturePointagesEffectifsPort.NouveauPointage(id, r.id(), new PointageEffectifs.Lieu(q.placeId(), milieuId, q.uniteCode()),
                new PresenceEffectifs.PointageCourt(sens, ctx.maintenant(), milieuId, null, activite), PresenceEffectifs.OriginePointage.APPAREIL, null,
                new PointageEffectifs.Constat(j.resultat(), j.ecart(), PresenceEffectifs.distanceDe(verdicts).orElse(null), verdict.action(), verdict.motifs()),
                enAttente ? PresenceEffectifs.StatutPointage.A_APPROUVER : PresenceEffectifs.StatutPointage.ENREGISTRE, null));
        verdicts.forEach(v -> ecrire.depot().inscrirePreuve(ctx.tenantId(), id, v));
        if (enAttente) {
            // E-GP-6 : un pointage en attente suit le flux d'approbation des pointages, fixé à ce moment.
            ouvrirFlux(ctx, id, new DemandesFlux.PointageEnAttente(q.uniteCode(), ((Number) faitsRegles.get("ecartDebutMinutes")).longValue(), false, j.ecart() != null,
                    lire.presence().corrobore(ctx.tenantId(), r.id(), new PresenceEffectifs.PointageCourt(sens, ctx.maintenant()))));
        }
        return lire.presence().vue(ctx.tenantId(), lire.presence().lire(ctx.tenantId(), id));
    }

    private void ouvrirFlux(Contexte ctx, String documentId, DemandesFlux.PointageEnAttente p) {
        approbation.flux().ouvrir(ctx, FluxApprobation.ObjetFlux.POINTAGE, documentId, DemandesFlux.contexteDePointage(p),
                new GestionnaireFluxApprobations.Demande(ctx.acteurId(), GestionnaireFluxApprobations.UN_PALIER));
    }

    /**
     * <strong>Le milieu où la personne se trouve</strong>, qui n'est pas toujours celui du quart (E-GP-7) : dépêchée d'une caserne à
     * l'autre, elle pointe sa sortie ailleurs, et la juger contre le milieu du quart la dirait « hors milieu » pour avoir obéi. Seule
     * l'existence du milieu est contrôlée ; ce qui empêche de se déclarer n'importe où est la preuve, jugée contre le milieu déclaré.
     */
    private static String milieuDeclare(Object brut, String duQuart) {
        String declare = brut == null ? "" : String.valueOf(brut).strip();
        return declare.isEmpty() ? duQuart : declare;
    }

    /**
     * Le milieu du quart, lu <strong>sans</strong> contrôle de périmètre : la personne qui pointe n'a pas la lecture de
     * son unité, mais son pointage doit se juger aux preuves du milieu où elle travaille. Seul le verdict en sort.
     */
    private PresenceEffectifs.MilieuPreuves milieuDuQuart(Contexte ctx, String milieuId) {
        Milieu m = lire.milieux().lire(ctx.tenantId(), milieuId);
        Milieux.Coordonnees c = m.latitude() != null && m.longitude() != null && m.rayonMetres() != null
                ? new Milieux.Coordonnees(m.latitude(), m.longitude(), m.rayonMetres()) : null;
        return new PresenceEffectifs.MilieuPreuves(m.id(), c, m.preuves(), lire.secrets().secretQr(ctx.tenantId(), m.id()).orElse(null));
    }

    private static Map<String, String> portees(Ressource r) {
        Map<String, String> p = new HashMap<>();
        p.put("convention", r.conventionCode());
        p.put("statutEmploi", r.statutEmploiCode());
        p.put("corpsEmploi", r.corpsEmploiCode());
        p.put("titreEmploi", r.titreEmploiCode());
        return p;
    }

    private void inserer(Contexte ctx, EcriturePointagesEffectifsPort.NouveauPointage p) {
        ecrire.depot().inserer(ctx, p);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("sens", p.pointage().sens());
        apres.put("horodatage", p.pointage().horodatage());
        apres.put("resultat", p.constat().resultat());
        apres.put("ecart", p.constat().ecart());
        apres.put("statut", p.statut());
        apres.put("naturePause", p.pointage().nature());
        apres.put("activiteCode", p.pointage().activiteCode());
        ecrire.journal().ecrire(ctx, new JournalEffectifs.Consignation(p.origine().actionJournal(), OBJET, p.id(), p.lieu().uniteCode(), null, apres, null));
    }

    // ── Saisie a posteriori ──────────────────────────────────────────────────

    private static String justificationDe(Object v) {
        String j = v instanceof String s ? s.strip() : "";
        if (j.length() < PresenceEffectifs.JUSTIFICATION_MIN) {
            throw new EffectifsInvalideException("« justification » : dites en quelques mots pourquoi le quart n'a pas été pointé");
        }
        return j.substring(0, Math.min(j.length(), PresenceEffectifs.TEXTE_MAX));
    }

    private Ressource personne(Contexte ctx, Object ressourceId) {
        return ressourceId instanceof String id && !id.isEmpty() ? lire.ressources().exiger(ctx, id) : lire.ressources().exigerDeLActeur(ctx);
    }

    /** Un quart non pointé, déclaré en segments. Par soi : en attente. Par l'encadrement de l'unité : approuvé, à son nom. */
    public List<Map<String, Object>> saisirAPosteriori(Contexte ctx, Map<String, Object> entree) {
        Ressource r = personne(ctx, entree.get("ressourceId"));
        boolean pourSoi = Objects.equals(r.utilisateurId(), ctx.acteurId());
        if (!pourSoi) {
            lire.perimetres().exiger(ctx, r.uniteCode(), Perimetres.Niveau.GESTION);
        }
        List<PresenceEffectifs.SegmentSaisi> segments = PresenceEffectifs.segmentsSaisis(entree.get("segments"));
        List<PresenceEffectifs.PointageCourt> pointages = PresenceEffectifs.pointagesDeSegments(segments);
        String justification = justificationDe(entree.get("justification"));
        if (segments.getLast().fin().compareTo(ctx.maintenant()) > 0) {
            throw new EffectifsInvalideException("on ne déclare après coup que du temps passé");
        }
        lire.qualification().exigerAPosteriori(ctx, r, segments);
        PresenceEffectifs.QuartPointe q = lire.quarts().autourDe(ctx.tenantId(), r.id(), segments.getFirst().debut()).orElse(null);
        // P-GP-4 : plusieurs saisies se cumulent ; seul le chevauchement est refusé, qui compterait deux fois le même temps —
        // sur un quart comme hors de tout quart.
        RepartitionPresence.verifierCumul(segments, RepartitionPresence.plagesCouvertes(dejaDeclare(ctx, r, q, segments)));
        String saisieId = ecrire.sequence().suivant("esai");
        PointageEffectifs.Lieu lieu = q == null ? new PointageEffectifs.Lieu(null, null, r.uniteCode()) : new PointageEffectifs.Lieu(q.placeId(), q.milieuId(), q.uniteCode());
        Saisie saisie = new Saisie(saisieId, r, lieu, pourSoi, justification);
        exigerSaisiePermise(ctx, saisie, q == null ? segments.getFirst().debut().substring(0, DATE) : q.date(), segments.getLast().fin());
        inscrireLaSaisie(ctx, saisie, pointages);
        return lire.presence().vues(ctx.tenantId(), lire.presence().deLaSaisie(ctx.tenantId(), saisieId));
    }

    /**
     * Le temps déjà déclaré que la saisie ne doit pas recouvrir : celui de la place du quart, ou, hors quart, celui de la
     * personne autour des segments. La fenêtre s'élargit d'un jour de part et d'autre pour lire des paires entières —
     * une entrée seule ne couvre rien.
     */
    private List<PresenceEffectifs.PointageCourt> dejaDeclare(Contexte ctx, Ressource r, PresenceEffectifs.QuartPointe q,
                                                              List<PresenceEffectifs.SegmentSaisi> segments) {
        if (q != null) {
            return lire.presence().actifsDeLaPlace(ctx.tenantId(), r.id(), q.placeId());
        }
        return lire.presence().actifsHorsQuart(ctx.tenantId(), r.id(), Iso.de(Instant.parse(segments.getFirst().debut()).minus(MARGE_HORS_QUART)),
                Iso.de(Instant.parse(segments.getLast().fin()).plus(MARGE_HORS_QUART)));
    }

    /** Une saisie après coup, prête à s'inscrire. */
    private record Saisie(String id, Ressource ressource, PointageEffectifs.Lieu lieu, boolean pourSoi, String justification) { }

    /** E-GP-3 : une saisie après coup est une modification des heures ; les règles de l'organisme en décident. */
    private void exigerSaisiePermise(Contexte ctx, Saisie s, String date, String finDuTemps) {
        approbation.restriction().exigerPermise(ctx, new RestrictionModificationHeures.Demande(ModificationHeures.OperationModification.SAISIE_A_POSTERIORI,
                s.ressource(), s.lieu().uniteCode(), date, finDuTemps, !s.pourSoi(), TempsEffectifs.Source.SAISIE, s.id()));
    }

    private void inscrireLaSaisie(Contexte ctx, Saisie s, List<PresenceEffectifs.PointageCourt> pointages) {
        PresenceEffectifs.StatutPointage statut = s.pourSoi() ? PresenceEffectifs.StatutPointage.A_APPROUVER : PresenceEffectifs.StatutPointage.APPROUVE;
        PointageEffectifs.Constat constat = new PointageEffectifs.Constat(PresenceEffectifs.Resultat.NON_VERIFIE, null, null, ActionRegle.ACCEPTER, List.of());
        for (PresenceEffectifs.PointageCourt p : pointages) {
            inserer(ctx, new EcriturePointagesEffectifsPort.NouveauPointage(ecrire.sequence().suivant("epoi"), s.ressource().id(), s.lieu(), p,
                    PresenceEffectifs.OriginePointage.SAISIE, s.id(), constat, statut, s.justification()));
        }
        if (s.pourSoi()) {
            ouvrirFlux(ctx, s.id(), new DemandesFlux.PointageEnAttente(s.lieu().uniteCode(), 0, true, false,
                    lire.presence().corrobore(ctx.tenantId(), s.ressource().id(), pointages.getFirst())));
        }
    }
}
