package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.DisponibilitesDeposees;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.securite.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les lectures des disponibilités (lot R4) — jumeau de la partie lecture de
 * {@code backend/src/effectifs/disponibilites/service.ts} : un dépôt, les dépôts d'une période, ce que l'employé
 * voit de lui-même, et le <strong>bassin</strong> d'une place avec le motif de chaque exclusion (E-GN-14, E-GN-16).
 */
public class LectureDisponibilites {

    /** Les états d'une période où l'employé voit encore ses dépôts. */
    private static final Set<Planification.EtatPeriode> PERIODES_VISIBLES = EnumSet.of(Planification.EtatPeriode.COLLECTE,
            Planification.EtatPeriode.CONFECTION, Planification.EtatPeriode.PUBLIEE);

    /** Les lectures de la planification (lot R3) dont les disponibilités ont besoin. */
    public record Lectures(LectureRessources ressources, LecturePeriodes periodes, LectureEquipages equipages, LecturePerimetres perimetres) { }

    /** Une ressource tenue hors du bassin, et pourquoi. */
    public record Exclusion(String ressourceId, String nom, String motif) implements Jugement { }

    /** Les candidats d'une place, classés, et les exclus avec leur motif. */
    public record Candidats(List<Disponibilites.Candidature> admissibles, List<Exclusion> exclus) { }

    /** Le jugement d'une ressource pour une place : admise, ou exclue avec son motif. */
    sealed interface Jugement permits Admission, Exclusion { }

    record Admission(Disponibilites.Candidature candidature) implements Jugement { }

    private final DepotDisponibilitesPort depot;
    private final Lectures lire;
    /** Pour **annoncer** les règles du dépôt, jamais pour juger : la lecture ne décide rien. */
    private final EvaluateurRegles regles;

    public LectureDisponibilites(DepotDisponibilitesPort depot, Lectures lire, EvaluateurRegles regles) {
        this.depot = depot;
        this.lire = lire;
        this.regles = regles;
    }

    /**
     * Le contexte d'une <strong>lecture interne</strong>, sans filtre de périmètre : retrouver la période de l'unité
     * d'un employé, le bassin d'une place dont le périmètre a déjà été vérifié. Son résultat ne sort jamais tel quel ;
     * toute écriture repasse par le vrai contexte.
     */
    static Contexte interne(Contexte ctx) {
        return new Contexte(ctx.tenantId(), ctx.acteurId(), Role.ADMIN.code(), ctx.maintenant());
    }

    public Optional<DisponibilitesDeposees> lire(Contexte ctx, String periodeId, String ressourceId) {
        return depot.lire(ctx.tenantId(), periodeId, ressourceId);
    }

    /** La période, lue sans exiger de périmètre (l'employé regarde sa propre unité), mais de l'unité de la ressource. */
    public Periode periodeDe(Contexte ctx, String periodeId, Ressource r) {
        Periode p = lire.periodes().lire(interne(ctx), periodeId);
        if (!p.uniteCode().equals(r.uniteCode())) {
            throw new EffectifsInvalideException("« " + r.nom() + " » n'appartient pas à l'unité de cette période (" + p.uniteCode() + ")");
        }
        return p;
    }

    public Optional<DisponibilitesDeposees> consulter(Contexte ctx, String periodeId, String ressourceId) {
        Ressource r = lire.ressources().exiger(ctx, ressourceId);
        lire.ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.LECTURE);
        periodeDe(ctx, periodeId, r);
        return lire(ctx, periodeId, ressourceId);
    }

    /** Les dépôts d'une période, pour le planificateur : qui a déposé, qui manque, qui attend. */
    public List<Map<String, Object>> listerDepots(Contexte ctx, String periodeId) {
        Periode p = lire.periodes().exiger(ctx, periodeId, Perimetres.Niveau.LECTURE);
        return lire.ressources().lister(ctx, p.uniteCode(), null).stream().filter(Ressource::actif).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ressourceId", r.id());
            m.put("nom", r.nom());
            m.put("matricule", r.matricule());
            m.put("depot", lire(ctx, periodeId, r.id()).map(LectureDisponibilites::resume).orElse(null));
            return m;
        }).toList();
    }

    private static Map<String, Object> resume(DisponibilitesDeposees d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("conformite", d.conformite());
        m.put("motifs", d.motifs());
        m.put("plages", d.plages().size());
        m.put("deposeLe", d.deposeLe());
        return m;
    }

    /** Ce que l'employé connecté voit : sa ressource, les périodes ouvertes de son unité, ses dépôts. */
    public Map<String, Object> mesDisponibilites(Contexte ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        Optional<Ressource> r = lire.ressources().deLActeur(ctx);
        m.put("ressource", r.orElse(null));
        m.put("periodes", r.map(x -> periodesDe(ctx, x)).orElse(List.of()));
        return m;
    }

    /** Les règles {@code DISPONIBILITE} qui jugeront le dépôt de cette personne pour cette période. */
    private List<Regles.RegleApplicable> reglesDuDepot(Contexte ctx, Ressource r, Periode p) {
        return regles.reglesApplicables(ctx, new Regles.Dossier(ContexteRegle.DISPONIBILITE, null, null, p.debut(), r.uniteCode(),
                Map.of(), r.porteesDeRegle()));
    }

    private List<Map<String, Object>> periodesDe(Contexte ctx, Ressource r) {
        return lire.periodes().lister(interne(ctx)).stream().filter(p -> p.uniteCode().equals(r.uniteCode()) && PERIODES_VISIBLES.contains(p.etat()))
                .sorted(Comparator.comparing(Periode::debut)).map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.id());
                    m.put("uniteCode", p.uniteCode());
                    m.put("nom", p.nom());
                    m.put("debut", p.debut());
                    m.put("fin", p.fin());
                    m.put("etat", p.etat());
                    m.put("finCollecte", p.finCollecte());
                    m.put("depot", lire(ctx, p.id(), r.id()).orElse(null));
                    // Les règles annoncées **avant** le dépôt (E-GN-16, planche s02) : elles ne se
                    // connaissaient qu'au refus. La date est celle du début de période, comme au dépôt.
                    m.put("regles", reglesDuDepot(ctx, r, p));
                    return m;
                }).toList();
    }

    /** Les candidats d'une place, et les exclus avec leur motif (E-GN-14, E-GN-16). */
    public Candidats candidats(Contexte ctx, String placeId) {
        LectureEquipages.PlaceEtEquipage pe = lire.equipages().lirePlace(ctx, placeId);
        Equipage e = pe.equipage();
        lire.perimetres().exiger(ctx, e.uniteCode(), Perimetres.Niveau.PLANIFICATION);
        List<Disponibilites.Candidature> admissibles = new ArrayList<>();
        List<Exclusion> exclus = new ArrayList<>();
        for (Ressource r : lire.ressources().lister(interne(ctx), e.uniteCode(), pe.place().corpsEmploiCode())) {
            switch (juger(ctx, r, e, placeId)) {
                case Admission a -> admissibles.add(a.candidature());
                case Exclusion x -> exclus.add(x);
            }
        }
        return new Candidats(Disponibilites.classer(admissibles), exclus);
    }

    private Jugement juger(Contexte ctx, Ressource r, Equipage e, String placeId) {
        if (!r.actif()) {
            return new Exclusion(r.id(), r.nom(), "ressource inactive");
        }
        if (depot.absenteLe(ctx.tenantId(), r.id(), e.date())) {
            return new Exclusion(r.id(), r.nom(), "absente ce jour-là");
        }
        List<Planification.Creneau> tenus = lire.equipages().creneauxDe(ctx, r.id(), placeId);
        if (Planification.chevaucheUnQuart(tenus, e.creneau())) {
            return new Exclusion(r.id(), r.nom(), "déjà affectée à un quart qui chevauche");
        }
        return lire(ctx, e.periodeId(), r.id()).map(d -> jugerDepot(d, r, e.creneau(), tenus))
                .orElseGet(() -> new Exclusion(r.id(), r.nom(), "aucune disponibilité déposée pour cette période"));
    }

    private static Jugement jugerDepot(DisponibilitesDeposees d, Ressource r, Planification.Creneau quart, List<Planification.Creneau> tenus) {
        if (d.attendApprobation()) {
            return new Exclusion(r.id(), r.nom(), "disponibilités non conformes, en attente d'approbation : "
                    + d.motifs().stream().map(Regles.Motif::message).collect(Collectors.joining(" ; ")));
        }
        if (Disponibilites.indisponible(d.plages(), quart)) {
            return new Exclusion(r.id(), r.nom(), "s'est déclarée indisponible sur ce quart");
        }
        return Disponibilites.couvre(d.plages(), quart)
                .<Jugement>map(p -> new Admission(new Disponibilites.Candidature(r.id(), r.nom(), p.type() == Disponibilites.TypeDisponibilite.PREFERENCE,
                        r.anciennete(), Planification.heuresSemaine(tenus, quart))))
                .orElseGet(() -> new Exclusion(r.id(), r.nom(), "aucune plage de disponibilité ne couvre ce quart"));
    }
}
