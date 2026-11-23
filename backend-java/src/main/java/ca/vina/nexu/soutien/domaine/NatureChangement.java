package ca.vina.nexu.soutien.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * <strong>La nature d'une maintenance annoncée</strong>, et le préavis qu'elle exige (ST-H02, ADR 0085).
 *
 * <ul>
 *   <li><em>entretien</em> : 72 h, la règle d'origine de la plateforme ;</li>
 *   <li><em>correctif</em> : correctif de sécurité ou correction mineure sans changement fonctionnel, 3 jours ouvrables
 *       (FX-AO-20260810, grille 99.1, addenda 1) ;</li>
 *   <li><em>rehaussement</em> : 21 jours ouvrables (grille 99, ramenée de 30 par l'addenda 1).</li>
 * </ul>
 */
public enum NatureChangement {
    ENTRETIEN("entretien", "Entretien planifié", Duration.ofHours(Soutien.PREAVIS_MAINTENANCE_HEURES), 0),
    CORRECTIF("correctif", "Correctif de sécurité ou correction mineure, sans changement fonctionnel", Duration.ZERO, 3),
    REHAUSSEMENT("rehaussement", "Rehaussement : nouvelle version qui change les fonctionnalités", Duration.ZERO, 21);

    private final String code;
    private final String libelle;
    private final Duration preavisHoraire;
    private final int joursOuvrables;

    NatureChangement(String code, String libelle, Duration preavisHoraire, int joursOuvrables) {
        this.code = code;
        this.libelle = libelle;
        this.preavisHoraire = preavisHoraire;
        this.joursOuvrables = joursOuvrables;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public int joursOuvrables() {
        return joursOuvrables;
    }

    public long heures() {
        return preavisHoraire.toHours();
    }

    /** Sans nature déclarée, une maintenance reste un entretien : les annonces d'avant ST-H02 gardent leur règle. */
    public static NatureChangement de(String code) {
        if (code == null || code.isBlank()) {
            return ENTRETIEN;
        }
        return Arrays.stream(values()).filter(n -> n.code.equals(code.trim())).findFirst()
                .orElseThrow(() -> new NatureInconnueException(code));
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(NatureChangement::code).toList();
    }

    /** Le premier instant où peut commencer un changement de cette nature, annoncé à {@code annonce}. */
    public Instant premierDebutAdmis(Instant annonce) {
        return joursOuvrables == 0 ? annonce.plus(preavisHoraire) : JoursOuvrablesDuQuebec.premierDebutAdmis(annonce, joursOuvrables);
    }

    /** Une nature hors de l'ensemble fermé : refusée à la frontière, en nommant les natures admises. */
    public static final class NatureInconnueException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public NatureInconnueException(String code) {
            super("nature : « " + code + " » inconnue (attendu : " + String.join(", ", codes()) + ")");
        }
    }
}
