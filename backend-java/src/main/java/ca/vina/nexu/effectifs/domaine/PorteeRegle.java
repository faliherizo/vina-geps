package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/**
 * La portée d'une règle. <strong>L'ordre des constantes est la précision</strong>, du plus général au plus
 * particulier : à clé égale, la règle la plus particulière remplace la plus générale.
 */
public enum PorteeRegle {
    ORGANISME, UNITE, CONVENTION, STATUT_EMPLOI, CORPS_EMPLOI, TITRE_EMPLOI;

    public String code() {
        return name();
    }

    public static PorteeRegle de(Object v) {
        return Regles.enumDe(values(), PorteeRegle::code, v, "portée");
    }

    /** Le référentiel où le code de portée doit être en vigueur ; l'organisme n'en a pas. */
    public Optional<TypeReferentiel> referentiel() {
        return switch (this) {
            case ORGANISME -> Optional.empty();
            case UNITE -> Optional.of(TypeReferentiel.UNITES);
            case CONVENTION -> Optional.of(TypeReferentiel.CONVENTIONS);
            case STATUT_EMPLOI -> Optional.of(TypeReferentiel.STATUTS_EMPLOI);
            case CORPS_EMPLOI -> Optional.of(TypeReferentiel.CORPS_EMPLOI);
            case TITRE_EMPLOI -> Optional.of(TypeReferentiel.TITRES_EMPLOI);
        };
    }

    /** Une règle de cette portée et de ce code vise-t-elle le dossier ? Une règle d'unité vise aussi les sous-unités. */
    public boolean vise(String code, PorteesDuDossier p) {
        return switch (this) {
            case ORGANISME -> true;
            case UNITE -> p.unites().contains(code == null ? "" : code);
            case CONVENTION -> egal(code, p.convention());
            case STATUT_EMPLOI -> egal(code, p.statutEmploi());
            case CORPS_EMPLOI -> egal(code, p.corpsEmploi());
            case TITRE_EMPLOI -> egal(code, p.titreEmploi());
        };
    }

    private static boolean egal(String code, String valeur) {
        return code != null && code.equals(valeur);
    }
}
