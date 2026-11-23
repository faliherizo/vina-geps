package ca.vina.nexu.soutien.domaine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Les <strong>engagements de service</strong> d'un organisme : délais par niveau de priorité,
 * heures de soutien, objectif de disponibilité. Trois politiques, au catalogue fermé :
 * <ul>
 *   <li><em>standard</em> — la plateforme : un seul délai, de résolution, depuis l'ouverture ;
 *       99,5 % de disponibilité ;</li>
 *   <li><em>service essentiel</em> — AO 26-015-DO, clauses 5.02 et 6.01 : un délai de réponse
 *       qui dépend de l'heure d'ouverture (heures de soutien de 5 h à 23 h, 7 j/7), puis un délai
 *       de résolution qui court depuis le retour d'appel ; 99,7 %, manquement majeur sous 99,0 %.</li>
 *   <li><em>communications cliniques</em> — AO FX-AO-20260810, grille 98 et contrat 10.19.07 : une réponse en 4 h ou
 *       moins, en tout temps (activité 24/7, addenda 2) ; la résolution de la politique standard ; 95 %.</li>
 * </ul>
 * Les délais sont des durées civiles : le contrat ne les suspend pas hors des heures de soutien.
 */
public final class EngagementsDeService {

    private static final ZoneId FUSEAU_DU_QUEBEC = ZoneId.of("America/Montreal");
    private static final LocalTime DEBUT_DU_SOUTIEN = LocalTime.of(5, 0);
    private static final LocalTime FIN_DU_SOUTIEN = LocalTime.of(23, 0);
    private static final double OBJECTIF_STANDARD = 99.5;
    private static final double OBJECTIF_SERVICE_ESSENTIEL = 99.7;
    /** FX-AO-20260810, contrat 10.19.07 : taux d'efficacité de 95 % du temps prévu, entretien préventif exclu. */
    private static final double OBJECTIF_COMMUNICATIONS_CLINIQUES = 95.0;
    /** Grille 98 : « un temps de réponse inférieur ou égal à quatre (4) heures pour les demandes de soutien ». */
    private static final java.time.Duration REPONSE_COMMUNICATIONS_CLINIQUES = java.time.Duration.ofHours(4);
    private static final double SEUIL_MAJEUR_SERVICE_ESSENTIEL = 99.0;
    private static final long SECONDES_PAR_MINUTE = 60;

    private EngagementsDeService() {
    }

    /** Le niveau de priorité d'un billet, attribué selon la gravité et l'impact. */
    public enum Priorite {
        N1, N2, N3, N4;

        public String code() {
            return name();
        }

        public static Optional<Priorite> de(String code) {
            return Arrays.stream(values()).filter(p -> p.name().equals(code)).findFirst();
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Priorite::code).toList();
        }
    }

    /** La politique d'un organisme ; son code est ce que la base et l'API portent. */
    public enum Politique {
        STANDARD("standard", "Plateforme : délai de résolution compté depuis l'ouverture"),
        SERVICE_ESSENTIEL("service-essentiel", "Service essentiel : réponse puis résolution, soutien de 5 h à 23 h, 7 j/7"),
        COMMUNICATIONS_CLINIQUES("communications-cliniques", "Communications cliniques : réponse en 4 h ou moins en tout temps, 95 % d'efficacité");

        private final String code;
        private final String libelle;

        Politique(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        public static Politique de(String code) {
            return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst().orElseThrow(() -> new PolitiqueInconnueException(code));
        }
    }

    /** Un code de politique hors catalogue : refusé à la frontière (400). */
    public static final class PolitiqueInconnueException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public PolitiqueInconnueException(String code) {
            super("politique de soutien inconnue : « " + code + " » (attendu : "
                    + String.join(", ", Arrays.stream(Politique.values()).map(Politique::code).toList()) + ")");
        }
    }

    /** Les heures de soutien : un billet ouvert dans cette plage reçoit le délai de réponse court. */
    public record HeuresDeSoutien(LocalTime debut, LocalTime fin, ZoneId fuseau) {
        public boolean couvre(Instant instant) {
            LocalTime heure = instant.atZone(fuseau).toLocalTime();
            return !heure.isBefore(debut) && heure.isBefore(fin);
        }
    }

    public record Reponse(Duration enHeures, Duration horsHeures) {
    }

    /** Ce que la politique promet pour un niveau : sa définition, le délai de réponse s'il y en a un, le délai de résolution. */
    public record Clause(String description, Optional<Reponse> reponse, Duration resolution) {
    }

    public record Engagement(Politique politique, Map<Priorite, Clause> clauses, Optional<HeuresDeSoutien> heures,
                             double objectifPourcent, OptionalDouble seuilManquementMajeurPourcent) {
        public Clause clause(Priorite priorite) {
            return clauses.get(priorite);
        }
    }

    public static Engagement de(Politique politique) {
        return switch (politique) {
            case STANDARD -> standard();
            case SERVICE_ESSENTIEL -> serviceEssentiel();
            case COMMUNICATIONS_CLINIQUES -> communicationsCliniques();
        };
    }

    private static Engagement standard() {
        Map<Priorite, Clause> c = new EnumMap<>(Priorite.class);
        c.put(Priorite.N1, new Clause("Service interrompu pour l'ensemble des utilisateurs, ou perte de données", Optional.empty(), Duration.ofHours(1)));
        c.put(Priorite.N2, new Clause("Fonction essentielle indisponible pour un établissement ou un groupe", Optional.empty(), Duration.ofHours(2)));
        c.put(Priorite.N3, new Clause("Anomalie avec solution de contournement", Optional.empty(), Duration.ofHours(4)));
        c.put(Priorite.N4, new Clause("Question, demande d'information ou d'amélioration", Optional.empty(), Duration.ofHours(24)));
        return new Engagement(Politique.STANDARD, Map.copyOf(c), Optional.empty(), OBJECTIF_STANDARD, OptionalDouble.empty());
    }

    /**
     * FX-AO-20260810 (ST-H05, ADR 0085) : la réponse en 4 h de la grille 98 s'ajoute aux résolutions de la politique standard ;
     * aucun seuil de manquement majeur, le contrat n'en définit pas — sous 95 %, une pénalité par jour (10.19.07 b).
     */
    private static Engagement communicationsCliniques() {
        Reponse quatreHeures = new Reponse(REPONSE_COMMUNICATIONS_CLINIQUES, REPONSE_COMMUNICATIONS_CLINIQUES);
        Map<Priorite, Clause> c = new EnumMap<>(Priorite.class);
        standard().clauses().forEach((p, clause) -> c.put(p, new Clause(clause.description(), Optional.of(quatreHeures), clause.resolution())));
        return new Engagement(Politique.COMMUNICATIONS_CLINIQUES, Map.copyOf(c), Optional.empty(), OBJECTIF_COMMUNICATIONS_CLINIQUES, OptionalDouble.empty());
    }

    /** Clause 5.02.01 (définitions), 5.02.02 (délais), 6.01 (disponibilité) du contrat 26-015-DO. */
    private static Engagement serviceEssentiel() {
        Reponse rapide = new Reponse(Duration.ofMinutes(30), Duration.ofMinutes(60));
        Reponse normale = new Reponse(Duration.ofMinutes(60), Duration.ofMinutes(120));
        Map<Priorite, Clause> c = new EnumMap<>(Priorite.class);
        c.put(Priorite.N1, new Clause("La solution est inopérante, ou l'intégrité des données d'exploitation est compromise", Optional.of(rapide), Duration.ofHours(2)));
        c.put(Priorite.N2, new Clause("Une fonction ou un module essentiel est inopérant, ou la sauvegarde et la récupération des données sont touchées",
                Optional.of(rapide), Duration.ofHours(6)));
        c.put(Priorite.N3, new Clause("Une fonction non essentielle est inopérante ou non conforme, avec une solution de contournement acceptable",
                Optional.of(normale), Duration.ofHours(48)));
        c.put(Priorite.N4, new Clause("Tout autre problème, ou une demande d'information", Optional.of(normale), Duration.ofDays(7)));
        return new Engagement(Politique.SERVICE_ESSENTIEL, Map.copyOf(c), Optional.of(new HeuresDeSoutien(DEBUT_DU_SOUTIEN, FIN_DU_SOUTIEN, FUSEAU_DU_QUEBEC)),
                OBJECTIF_SERVICE_ESSENTIEL, OptionalDouble.of(SEUIL_MAJEUR_SERVICE_ESSENTIEL));
    }

    // ── Les délais d'un billet ──────────────────────────────────────────────

    public enum EtatDelai {
        DANS_LES_DELAIS("dans_les_delais"), EN_RETARD("en_retard"), TENU("tenu"), MANQUE("manque");

        private final String code;

        EtatDelai(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Un délai jugé : son échéance, l'instant où il a été tenu s'il l'a été, et l'écart en minutes (négatif = dépassé). */
    public record Delai(Instant echeance, Optional<Instant> atteintLe, EtatDelai etat, long ecartMinutes) {
        public boolean enRetard() {
            return etat == EtatDelai.EN_RETARD || etat == EtatDelai.MANQUE;
        }
    }

    /** Les instants qui comptent : l'envoi, le retour d'appel du soutien (prise en charge), la résolution. */
    public record Jalons(Instant ouvert, Optional<Instant> retourDAppel, Optional<Instant> resolu) {
    }

    /**
     * Le suivi d'un billet : le délai de réponse, s'il est promis, et l'échéance de résolution.
     * Tant que le soutien n'a pas rappelé, l'échéance de résolution est comptée depuis l'échéance
     * de réponse : c'est la plus tardive que le contrat admette.
     */
    public record Suivi(Politique politique, Optional<Delai> reponse, Instant echeanceResolution) {
        public boolean enRetard(Instant maintenant, Optional<Instant> resolu) {
            return reponse.map(Delai::enRetard).orElse(false) || (resolu.isEmpty() && maintenant.isAfter(echeanceResolution));
        }
    }

    public static Suivi suivre(Engagement e, Priorite priorite, Jalons jalons, Instant maintenant) {
        Clause clause = e.clause(priorite);
        if (clause.reponse().isEmpty()) {
            return new Suivi(e.politique(), Optional.empty(), jalons.ouvert().plus(clause.resolution()));
        }
        Reponse r = clause.reponse().get();
        boolean enHeures = e.heures().map(h -> h.couvre(jalons.ouvert())).orElse(true);
        Instant echeanceReponse = jalons.ouvert().plus(enHeures ? r.enHeures() : r.horsHeures());
        Delai reponse = juger(echeanceReponse, jalons.retourDAppel(), maintenant);
        Instant depart = jalons.retourDAppel().orElse(echeanceReponse);
        return new Suivi(e.politique(), Optional.of(reponse), depart.plus(clause.resolution()));
    }

    static Delai juger(Instant echeance, Optional<Instant> atteint, Instant maintenant) {
        Instant reference = atteint.orElse(maintenant);
        long ecart = Math.floorDiv(Duration.between(reference, echeance).getSeconds(), SECONDES_PAR_MINUTE);
        boolean aTemps = !reference.isAfter(echeance);
        EtatDelai etat = atteint.isPresent() ? (aTemps ? EtatDelai.TENU : EtatDelai.MANQUE) : (aTemps ? EtatDelai.DANS_LES_DELAIS : EtatDelai.EN_RETARD);
        return new Delai(echeance, atteint, etat, ecart);
    }

    // ── La disponibilité du mois, jugée contre l'engagement ──────────────────

    public enum Manquement {
        AUCUN("aucun"), MANQUEMENT("manquement"), MANQUEMENT_MAJEUR("manquement_majeur");

        private final String code;

        Manquement(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Sous l'objectif : manquement (6.01.04 b) ; sous le seuil majeur, s'il y en a un : manquement majeur (6.01.05 c). */
    public static Manquement manquementDe(Engagement e, double pourcentage) {
        if (pourcentage >= e.objectifPourcent()) {
            return Manquement.AUCUN;
        }
        boolean majeur = e.seuilManquementMajeurPourcent().isPresent() && pourcentage < e.seuilManquementMajeurPourcent().getAsDouble();
        return majeur ? Manquement.MANQUEMENT_MAJEUR : Manquement.MANQUEMENT;
    }

    // ── Ports ────────────────────────────────────────────────────────────────

    /** La politique d'un organisme ; sans choix enregistré, la politique standard. */
    @FunctionalInterface
    public interface Registre {
        Politique de(String tenantId);
    }

    /** L'organisme, son nom, sa politique, et qui l'a fixée. */
    public record Affectation(String tenantId, String nom, Politique politique, Optional<String> modifieLe, Optional<String> modifiePar) {
    }

    public interface Repertoire {
        List<Affectation> affectations();
    }

    public interface Ecriture {
        /** @throws ca.vina.nexu.partage.erreurs.domaine.IntrouvableException si l'organisme n'existe pas */
        Affectation fixer(String tenantId, Politique politique, String acteurId, Instant le);
    }
}
