package ca.vina.nexu.paie.domaine;

/**
 * Ce que la paie relève sur une feuille avant de la valider (ADR 0084). Une anomalie <em>bloquante</em> interdit la
 * validation ; les autres s'affichent pour qu'on les lise, et certaines empêchent de toute façon la personne d'être
 * payée (sans dossier, sans taux).
 */
public enum AnomalieTemps {
    AUCUNE_FEUILLE("aucune-feuille", false),
    NON_APPROUVEE("non-approuvee", true),
    INCALCULABLE("incalculable", true),
    SANS_DOSSIER("sans-dossier", false),
    SANS_TAUX("sans-taux", false),
    HEURES_ELEVEES("heures-elevees", false),
    VALIDATION_PERIMEE("validation-perimee", false);

    private final String code;
    private final boolean bloquante;

    AnomalieTemps(String code, boolean bloquante) {
        this.code = code;
        this.bloquante = bloquante;
    }

    public String code() {
        return code;
    }

    public boolean bloquante() {
        return bloquante;
    }
}
