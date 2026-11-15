package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import ca.vina.nexu.effectifs.domaine.FluxApprobation.Approbateur;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.TypeApprobateur;

/**
 * <strong>Les demandes d'approbation des heures</strong> (E-GP-6) — le pur de {@code effectifs/approbations/service.ts} et des
 * contextes de routage ({@code approbations/domaine.ts}) : la vue d'un flux dans les mots des effectifs, qui porte une étape,
 * et les champs qu'une feuille ou un pointage exposent à leur soumission.
 */
public final class DemandesFlux {

    private static final double MINUTES_HEURE = 60.0;
    private static final double CENTIEMES = 100.0;
    private static final int UN = 1;
    private static final int ZERO = 0;

    private DemandesFlux() {
    }

    /** Un statut du moteur générique, avec ce qu'il devient pour une étape et pour le flux. */
    public enum StatutApprobation {
        EN_ATTENTE("pending", StatutEtape.EN_ATTENTE, StatutFlux.EN_COURS), APPROUVE("approved", StatutEtape.APPROUVEE, StatutFlux.APPROUVE),
        REJETE("rejected", StatutEtape.REJETEE, StatutFlux.REJETE),
        /** Rappelée par son auteur avant la fin du circuit : plus personne n'a à la décider. */
        RETIRE("withdrawn", StatutEtape.RETIREE, StatutFlux.RETIRE);

        private final String code;
        private final StatutEtape etape;
        private final StatutFlux flux;

        StatutApprobation(String code, StatutEtape etape, StatutFlux flux) {
            this.code = code;
            this.etape = etape;
            this.flux = flux;
        }

        public String code() {
            return code;
        }

        /** Le code du moteur ; un statut inconnu compte comme en attente, comme au Node. */
        public static StatutApprobation de(String code) {
            return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst().orElse(EN_ATTENTE);
        }
    }

    public enum StatutEtape { EN_ATTENTE, APPROUVEE, REJETEE, RETIREE }

    public enum StatutFlux { EN_COURS, APPROUVE, REJETE, RETIRE }

    /** Pourquoi le moteur a refusé une décision. */
    public enum RefusEtape { NON_PORTEE, DEMANDEUR, AUTRE }

    /** Un refus du moteur, traduit par l'application dans les mots des effectifs. */
    public static final class EtapeRefuseeException extends RuntimeException {
        private final RefusEtape refus;

        public EtapeRefuseeException(RefusEtape refus, String message) {
            super(message);
            this.refus = refus;
        }

        public RefusEtape refus() {
            return refus;
        }
    }

    /** Une étape d'une demande du moteur générique. */
    public record EtapeApprobation(String id, int level, String approverRole, String approverId, StatutApprobation statut, String decidePar,
                                   String decideLe, String pourLeCompteDe) { }

    public record DemandeApprobation(String id, String documentId, StatutApprobation statut, List<EtapeApprobation> etapes) { }

    public record EtapeFlux(String id, int palier, int niveau, Approbateur approbateur, String libelle, StatutEtape statut, String decidePar,
                            String decideLe, String pourLeCompteDe) { }

    /** Le flux d'un document : sa demande, son statut, ses étapes, et le palier qui attend (nul s'il n'en attend aucun). */
    public record Flux(String demandeId, StatutFlux statut, List<EtapeFlux> etapes, Integer palierCourant) {
        public Optional<EtapeFlux> etapeCourante() {
            return etapes.stream().filter(e -> palierCourant != null && e.palier() == palierCourant).findFirst();
        }
    }

    /**
     * Qui agit : l'administrateur porte toutes les étapes ; sinon le niveau de périmètre sur l'unité, le rôle ou la personne.
     * {@code suppleePour} : les personnes désignées dont l'acteur tient la place pendant leur absence (délégation en vigueur).
     */
    public record Acteur(String id, String role, boolean administrateur, Predicate<Perimetres.Niveau> niveauSur, Set<String> suppleePour) {

        public Acteur {
            suppleePour = Set.copyOf(suppleePour);
        }

        /** Un acteur qui ne supplée personne. */
        public Acteur(String id, String role, boolean administrateur, Predicate<Perimetres.Niveau> niveauSur) {
            this(id, role, administrateur, niveauSur, Set.of());
        }
    }

    /** Une absence : qui approuve à la place de la personne désignée, et jusqu'à quand ({@code null} : sans date de fin). */
    public record Suppleance(String suppleantId, String jusquAu) { }

    /** Le libellé d'un approbateur, pour l'écran et les refus. */
    public static String libelle(Approbateur a, Function<String, String> nomDe) {
        return switch (a.type()) {
            case PERIMETRE -> "le niveau " + a.valeur() + " de l'unité";
            case ROLE -> "le rôle « " + a.valeur() + " »";
            case PERSONNE -> nomDe.apply(a.valeur()) + " (personne désignée)";
            case SUPERIEUR -> "le supérieur hiérarchique de la personne";
        };
    }

    /**
     * <strong>Qui porte une étape.</strong> Une personne désignée la porte, et son suppléant aussi pendant son absence : la
     * feuille doit apparaître chez qui peut la décider. La décision elle-même reste jugée par le moteur générique.
     */
    public static boolean porte(Approbateur a, Acteur acteur) {
        if (acteur.administrateur()) {
            return true;
        }
        return switch (a.type()) {
            case PERIMETRE -> Arrays.stream(Perimetres.Niveau.values()).anyMatch(n -> n.name().equals(a.valeur()) && acteur.niveauSur().test(n));
            case ROLE -> acteur.role().equals(a.valeur());
            case PERSONNE -> acteur.id().equals(a.valeur()) || acteur.suppleePour().contains(a.valeur());
            // Une étape vivante ne porte jamais le marqueur : il est remplacé par la personne à l'ouverture du flux.
            case SUPERIEUR -> false;
        };
    }

    /**
     * Le flux, avec la suppléance <strong>dite</strong> : à l'étape qui attend une personne absente, le libellé nomme qui la
     * remplace et jusqu'à quand. La personne qui soumet sait ainsi vers qui sa feuille est partie.
     */
    public static Flux avecSuppleance(Flux f, Function<String, Optional<Suppleance>> suppleanceDe, Function<String, String> nomDe) {
        List<EtapeFlux> etapes = f.etapes().stream().map(e -> e.statut() != StatutEtape.EN_ATTENTE || e.approbateur().type() != TypeApprobateur.PERSONNE
                ? e : suppleanceDe.apply(e.approbateur().valeur()).map(s -> avecLibelle(e, e.libelle() + " — en son absence : " + nomDe.apply(s.suppleantId())
                        + (s.jusquAu() == null ? "" : " (jusqu'au " + s.jusquAu() + ")"))).orElse(e)).toList();
        return new Flux(f.demandeId(), f.statut(), etapes, f.palierCourant());
    }

    private static EtapeFlux avecLibelle(EtapeFlux e, String libelle) {
        return new EtapeFlux(e.id(), e.palier(), e.niveau(), e.approbateur(), libelle, e.statut(), e.decidePar(), e.decideLe(), e.pourLeCompteDe());
    }

    /** La demande du moteur, dans les mots des effectifs : les paliers se numérotent dans l'ordre des étapes. */
    public static Flux vue(DemandeApprobation d, Function<String, String> nomDe) {
        List<EtapeFlux> etapes = IntStream.range(0, d.etapes().size()).mapToObj(i -> {
            EtapeApprobation s = d.etapes().get(i);
            Approbateur a = FluxApprobation.approbateurDe(s.approverRole(), s.approverId());
            return new EtapeFlux(s.id(), i + 1, s.level(), a, libelle(a, nomDe), s.statut().etape, s.decidePar(), s.decideLe(), s.pourLeCompteDe());
        }).toList();
        StatutFlux statut = d.statut().flux;
        Integer courant = statut != StatutFlux.EN_COURS ? null
                : etapes.stream().filter(e -> e.statut() == StatutEtape.EN_ATTENTE).map(EtapeFlux::palier).findFirst().orElse(null);
        return new Flux(d.id(), statut, etapes, courant);
    }

    // ── Les champs de routage ────────────────────────────────────────────────

    /** Un segment, compté à la soumission : sa source et sa version. */
    public record SegmentCompte(TempsEffectifs.Source source, int version) { }

    /** Ce qu'une feuille montre au moment de sa soumission. */
    public record FeuilleSoumise(String uniteCode, long minutesPayees, long minutesSupplementaires, List<TempsEffectifs.CodeAnomalie> anomalies,
                                 List<SegmentCompte> segments, List<Long> ecarts) { }

    private static Number heures(long minutes) {
        return FluxApprobation.nombre(Math.round(minutes / MINUTES_HEURE * CENTIEMES) / CENTIEMES);
    }

    /** Les champs d'une feuille au moment de sa soumission, dans l'ordre du catalogue. */
    public static Map<String, Object> contexteDeFeuille(FeuilleSoumise f) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(FluxApprobation.TOUJOURS, UN);
        c.put("heuresDeclarees", heures(f.minutesPayees()));
        c.put("heuresSupplementaires", heures(f.minutesSupplementaires()));
        c.put("anomalies", f.anomalies().size());
        c.put("anomaliesNonCorroborees", (int) f.anomalies().stream().filter(a -> a == TempsEffectifs.CodeAnomalie.NON_CORROBORE).count());
        c.put("segmentsExternes", (int) f.segments().stream().filter(s -> s.source() == TempsEffectifs.Source.EXTERNE).count());
        c.put("segmentsCorriges", (int) f.segments().stream().filter(s -> s.version() > 1).count());
        c.put("ecartPlanifieMaxMinutes", f.ecarts().stream().mapToLong(Math::abs).max().orElse(0));
        c.put("uniteCode", f.uniteCode());
        return c;
    }

    /** Un pointage (ou une saisie après coup) quand il passe en attente. */
    public record PointageEnAttente(String uniteCode, long ecartDebutMinutes, boolean saisie, boolean horsMilieu, boolean corrobore) { }

    public static Map<String, Object> contexteDePointage(PointageEnAttente p) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(FluxApprobation.TOUJOURS, UN);
        c.put("ecartDebutMinutes", Math.abs(p.ecartDebutMinutes()));
        c.put("saisieAPosteriori", p.saisie() ? UN : ZERO);
        c.put("horsMilieu", p.horsMilieu() ? UN : ZERO);
        c.put("corrobore", p.corrobore() ? UN : ZERO);
        c.put("uniteCode", p.uniteCode());
        return c;
    }
}
