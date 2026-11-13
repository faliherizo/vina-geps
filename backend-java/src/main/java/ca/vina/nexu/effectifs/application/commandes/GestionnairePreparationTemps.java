package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.FeuilleDetailleeTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La <strong>préparation</strong> des feuilles (E-RF-1) — jumeau de {@code preparer} et {@code regenerer} : une feuille par
 * personne et par période de paie, générée depuis l'horaire publié et les pointages. Tout se calcule avant la première
 * écriture : une génération refusée (type d'heure absent) ne laisse aucune feuille à moitié préparée.
 */
public class GestionnairePreparationTemps {

    public static final String MECANISME = "feuille-de-temps";
    public static final int PERIODE_MAX_JOURS = 31;

    /** Les lectures de la préparation. */
    public record Lectures(LectureFeuillesTemps feuilles, CalculFeuillesTemps calcul, GenerationSegmentsTemps generation, LecturePerimetres perimetres) { }

    /** Les écritures de la préparation. */
    public record Ecritures(EcritureFeuillesTempsPort depot, JournalEffectifsPort journal, Sequence sequence) { }

    /** Le bilan d'une préparation : les feuilles créées et celles qui existaient déjà. */
    public record Bilan(List<String> creees, List<String> existantes) { }

    private final Lectures lire;
    private final Ecritures ecrire;

    public GestionnairePreparationTemps(Lectures lire, Ecritures ecrire) {
        this.lire = lire;
        this.ecrire = ecrire;
    }

    /** Prépare les feuilles d'une période de paie pour une personne, ou pour toutes celles d'une unité. */
    public Bilan preparer(Contexte ctx, Map<String, Object> entree) {
        String debut = Planification.dateDe(entree.get("debut"), "debut");
        String fin = Planification.dateDe(entree.get("fin"), "fin");
        Planification.verifierBornes(debut, fin);
        if (ChronoUnit.DAYS.between(LocalDate.parse(debut), LocalDate.parse(fin)) + 1 > PERIODE_MAX_JOURS) {
            throw new EffectifsInvalideException("une période de paie couvre " + PERIODE_MAX_JOURS + " jours au plus");
        }
        List<Ressource> ressources = ressourcesVisees(ctx, entree);
        int paliers = lire.feuilles().parametres(ctx.tenantId()).paliersApprobation();
        Bilan bilan = new Bilan(new ArrayList<>(), new ArrayList<>());
        Map<Ressource, List<EcritureFeuillesTempsPort.NouveauSegment>> aCreer = new LinkedHashMap<>();
        for (Ressource r : ressources) {
            lire.feuilles().depot().feuilleDe(ctx.tenantId(), r.id(), debut).ifPresentOrElse(bilan.existantes()::add,
                    () -> aCreer.put(r, lire.generation().generer(ctx, new GenerationSegmentsTemps.Periode(r.id(), debut, fin))));
        }
        aCreer.forEach((r, segments) -> bilan.creees().add(creer(ctx, new EcritureFeuillesTempsPort.NouvelleFeuille(ecrire.sequence().suivant("efeu"), r.id(),
                r.uniteCode(), debut, fin, paliers), segments)));
        journaliser(ctx, debut + ":" + fin, bilan);
        return new Bilan(List.copyOf(bilan.creees()), List.copyOf(bilan.existantes()));
    }

    private void journaliser(Contexte ctx, String periode, Bilan bilan) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("creees", bilan.creees().size());
        apres.put("existantes", bilan.existantes().size());
        ecrire.journal().ecrire(ctx, new JournalEffectifs.Consignation("temps.preparation", "feuilles", periode, null, null, apres, MECANISME));
    }

    private String creer(Contexte ctx, EcritureFeuillesTempsPort.NouvelleFeuille f, List<EcritureFeuillesTempsPort.NouveauSegment> segments) {
        ecrire.depot().insererFeuille(ctx, f);
        segments.forEach(s -> inserer(ctx, f.id(), s));
        return f.id();
    }

    private void inserer(Contexte ctx, String feuilleId, EcritureFeuillesTempsPort.NouveauSegment s) {
        ecrire.depot().insererSegment(ctx, feuilleId, s, EcritureFeuillesTempsPort.Filiation.origine(ecrire.sequence().suivant("eseg")));
    }

    private List<Ressource> ressourcesVisees(Contexte ctx, Map<String, Object> entree) {
        if (entree.get("ressourceId") instanceof String id && !id.isEmpty()) {
            Ressource r = lire.feuilles().ressources().exiger(ctx, id);
            lire.feuilles().ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.PLANIFICATION);
            return List.of(r);
        }
        if (!(entree.get("uniteCode") instanceof String unite) || unite.isEmpty()) {
            // Ni personne ni unité : la demande est « la mienne », et le message doit parler de moi.
            // Il posait la question d'un planificateur à quelqu'un qui n'avait désigné personne
            // justement parce qu'il demandait sa propre feuille (mesuré le 2 novembre 2026).
            return List.of(lire.feuilles().ressources().exigerDeLActeur(ctx));
        }
        lire.perimetres().exiger(ctx, unite, Perimetres.Niveau.PLANIFICATION);
        return lire.feuilles().ressources().lister(ctx, unite, null).stream().filter(Ressource::actif).sorted(Comparator.comparing(Ressource::matricule)).toList();
    }

    /** Régénère une feuille ouverte : les segments générés et pointés jamais corrigés sont repris de l'horaire et des pointages du moment. */
    public FeuilleDetailleeTemps regenerer(Contexte ctx, String id) {
        FeuilleTemps f = lire.feuilles().exigerLigne(ctx, id).feuille();
        lire.feuilles().exigerEcriture(ctx, f);
        TempsEffectifs.verifierModifiable(f.statut());
        List<EcritureFeuillesTempsPort.NouveauSegment> generes = lire.generation().generer(ctx, new GenerationSegmentsTemps.Periode(f.ressourceId(), f.debut(), f.fin()));
        int retires = ecrire.depot().desactiverGeneres(ctx.tenantId(), id);
        Set<String> gardes = lire.feuilles().depot().placesActives(ctx.tenantId(), id);
        List<EcritureFeuillesTempsPort.NouveauSegment> nouveaux = generes.stream().filter(s -> s.placeId() == null || !gardes.contains(s.placeId())).toList();
        nouveaux.forEach(s -> inserer(ctx, id, s));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("retires", retires);
        apres.put("generes", nouveaux.size());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.regeneration", "feuille", id, f.uniteCode(), null, apres));
        return lire.calcul().lire(ctx, id);
    }
}
