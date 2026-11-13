package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphRemplacement;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ModulesPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Le comblement d'une absence, vu et poursuivi par l'encadrement</strong> (lot U5) — jumeau de
 * {@code lireComblement} et {@code ouvrirRemplacement}, {@code backend/src/effectifs/absences/serviceComblement.ts} :
 * ce que le mécanisme a fait de chaque place, les avis envoyés, et le remplacement long terme d'une absence longue,
 * ouvert par le service de l'AGPH et <strong>lié à l'absence</strong> (P-CH-8, E-CH-7).
 */
public class SuiviComblementAbsence {

    public static final String MODULE_REMPLACEMENTS = "effectifs_agph";
    private static final String OBJET = "absence";

    /** Ce que le suivi lit. */
    public record Lectures(DepotComblementsAbsencesPort comblements, LecturePerimetres perimetres, LectureRessources ressources, LecturePostes postes) { }

    /** Le remplacement long terme et ce qui l'entoure. */
    public record Remplacements(DepotRemplacementsAgphPort depot, GestionnaireRemplacementsAgph gestionnaire, ModulesPort modules, JournalEffectifsPort journal) { }

    /** Un poste qui peut porter le remplacement ; celui dont la personne est titulaire en tête. */
    public record PostePropose(String id, String code, String libelle, boolean titulaire) { }

    private final Lectures lire;
    private final Remplacements remplacements;
    private final GestionnaireComblementAbsences comblement;

    public SuiviComblementAbsence(Lectures lire, Remplacements remplacements, GestionnaireComblementAbsences comblement) {
        this.lire = lire;
        this.remplacements = remplacements;
        this.comblement = comblement;
    }

    private boolean moduleOuvert(Contexte ctx) {
        return remplacements.modules().estActive(ctx.tenantId(), MODULE_REMPLACEMENTS);
    }

    /** Pourquoi on ne peut pas ouvrir de remplacement long terme pour cette absence ; vide si on le peut. */
    private Optional<String> empechement(Contexte ctx, AbsenceEffectifs a) {
        if (a.statut() != AbsencesEffectifs.Statut.ACCEPTEE) {
            return Optional.of("seule une absence acceptée se remplace");
        }
        if (!comblement.dureeLongue(ctx, a.codeAbsence(), a.du())) {
            return Optional.of("le code « " + a.codeAbsence() + " » est ponctuel : ses quarts se comblent un à un");
        }
        if (!moduleOuvert(ctx)) {
            return Optional.of("le module « " + MODULE_REMPLACEMENTS + " » (remplacements long terme) est fermé pour cet organisme");
        }
        return Optional.empty();
    }

    private Optional<AgphRemplacement> remplacementDe(Contexte ctx, String absenceId) {
        return remplacements.depot().lister(ctx.tenantId()).stream()
                .filter(x -> x.origine() == Agph.OrigineRemplacement.ABSENCE && absenceId.equals(x.referenceId()))
                .max(Comparator.comparing(AgphRemplacement::creeLe)).map(x -> remplacements.gestionnaire().lire(ctx, x.id()));
    }

    /** Le comblement d'une absence, pour l'encadrement : place par place, les avis envoyés, et le remplacement long terme. */
    public Map<String, Object> lire(Contexte ctx, AbsenceEffectifs a) {
        lire.perimetres().exiger(ctx, a.uniteCode(), Perimetres.Niveau.LECTURE);
        List<DepotComblementsAbsencesPort.LigneAvis> avis = lire.comblements().avisDeLAbsence(ctx.tenantId(), a.id());
        List<Map<String, Object>> places = lire.comblements().deLAbsence(ctx.tenantId(), a.id()).stream().map(l -> vue(l, avis)).toList();
        Optional<String> empechement = empechement(ctx, a);
        AgphRemplacement remplacement = moduleOuvert(ctx) ? remplacementDe(ctx, a.id()).orElse(null) : null;
        boolean possible = empechement.isEmpty() && remplacement == null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("places", places);
        m.put("remplacement", remplacement);
        m.put("remplacementPossible", possible);
        m.put("raisonRemplacement", remplacement == null ? empechement.orElse(null) : null);
        m.put("postesProposes", possible ? postesProposes(ctx, a) : List.of());
        return m;
    }

    private static Map<String, Object> vue(DepotComblementsAbsencesPort.LigneComblement l, List<DepotComblementsAbsencesPort.LigneAvis> avis) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.id());
        m.put("placeId", l.placeId());
        m.put("date", l.date());
        m.put("debut", l.debut());
        m.put("fin", l.fin());
        m.put("mode", l.mode());
        m.put("resultat", l.resultat());
        m.put("attribueeA", l.attribueeA());
        m.put("attribueeANom", l.attribueeANom());
        m.put("occupeeParId", l.occupeeParId());
        m.put("occupeeParNom", l.occupeeParNom());
        m.put("creeLe", l.creeLe());
        m.put("avis", avis.stream().filter(v -> v.comblementId().equals(l.id())).map(SuiviComblementAbsence::vue).toList());
        return m;
    }

    private static Map<String, Object> vue(DepotComblementsAbsencesPort.LigneAvis v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ressourceId", v.ressourceId());
        m.put("nom", v.nom());
        m.put("canal", v.canal());
        m.put("statut", v.statut());
        m.put("erreur", v.erreur());
        m.put("envoyeLe", v.envoyeLe());
        return m;
    }

    /** Les postes publiés de l'unité, du corps de la personne ; celui dont elle est titulaire en tête. */
    private List<PostePropose> postesProposes(Contexte ctx, AbsenceEffectifs a) {
        Ressource r = lire.ressources().exiger(ctx, a.ressourceId());
        return lire.postes().lister(ctx, null, a.uniteCode()).stream()
                .filter(p -> p.etat() != Planification.EtatPoste.BROUILLON && p.corpsEmploiCode().equals(r.corpsEmploiCode()))
                .map(p -> new PostePropose(p.id(), p.code(), p.libelle(), r.id().equals(p.titulaireId())))
                .sorted(Comparator.comparing(PostePropose::titulaire).reversed().thenComparing(PostePropose::code)).toList();
    }

    /**
     * Ouvre le remplacement long terme d'une absence longue par le service de l'AGPH : origine ABSENCE, liée à
     * l'absence, du premier jour restant à la fin. Un seul remplacement par absence.
     */
    public AgphRemplacement ouvrirRemplacement(Contexte ctx, AbsenceEffectifs a, Map<String, Object> entree) {
        lire.perimetres().exiger(ctx, a.uniteCode(), Perimetres.Niveau.GESTION);
        empechement(ctx, a).ifPresent(raison -> {
            throw new EffectifsInvalideException(raison);
        });
        remplacementDe(ctx, a.id()).ifPresent(x -> {
            throw new ConflitException("un remplacement long terme est déjà ouvert pour cette absence (" + x.id() + ")");
        });
        Poste poste = lire.postes().exiger(ctx, entree.get("posteId") instanceof String s ? s : "");
        if (!poste.uniteCode().equals(a.uniteCode())) {
            throw new EffectifsInvalideException("le poste « " + poste.code() + " » n'est pas de l'unité de l'absence (" + a.uniteCode() + ")");
        }
        Map<String, Object> demande = new HashMap<>();
        demande.put("posteId", poste.id());
        demande.put("origine", Agph.OrigineRemplacement.ABSENCE.name());
        demande.put("referenceId", a.id());
        demande.put("debut", a.du().compareTo(ctx.aujourdhui()) > 0 ? a.du() : ctx.aujourdhui());
        demande.put("fin", a.au());
        demande.put("finManifestation", entree.get("finManifestation"));
        AgphRemplacement x = remplacements.gestionnaire().ouvrir(ctx, demande);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("remplacementId", x.id());
        apres.put("posteId", poste.id());
        remplacements.journal().ecrire(ctx, JournalEffectifs.Consignation.de("absence.remplacement", OBJET, a.id(), a.uniteCode(), null, apres));
        return x;
    }
}
