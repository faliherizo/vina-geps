package ca.vina.nexu.rapports.domaine;

import java.util.Map;

/**
 * <b>Les rapports enregistrés</b> — les relevés que l'organisme définit une fois et reproduit
 * (AO 26-015-DO, P-RF-1) ; jumeau de {@code backend/src/rapports/enregistres.ts}.
 *
 * <p>Une définition n'est enregistrée <b>que valide</b> (l'appelant la planifie avant) ; ce qui est
 * stocké est la définition — jamais son résultat, qui se recalcule à chaque production.
 */
public final class RapportsEnregistres {

    private RapportsEnregistres() {
    }

    public static final int NOM_MAX = 120;
    public static final int DESCRIPTION_MAX = 1000;

    /** Un rapport enregistré introuvable dans l'organisme (404). */
    public static class RapportIntrouvableException extends RuntimeException {
        public RapportIntrouvableException(String id) {
            super("rapport enregistré « " + id + " » introuvable");
        }
    }

    /** Ce qu'on enregistre : un nom, une description facultative, la définition telle que reçue. */
    public record Saisie(String nom, String description, Map<String, Object> definition) {
    }

    /** Le nom, la description et la présence de la définition, validés dans cet ordre (les messages du Node). */
    @SuppressWarnings("unchecked")
    public static Saisie saisie(Map<String, Object> entree) {
        String nom = entree.get("nom") instanceof String s ? s.trim() : "";
        if (nom.isEmpty() || nom.length() > NOM_MAX) {
            throw new CatalogueRapports.RapportInvalideException("« nom » : de 1 à " + NOM_MAX + " caractères");
        }
        String description = entree.get("description") instanceof String s && !s.isBlank() ? s.trim() : null;
        if (description != null && description.length() > DESCRIPTION_MAX) {
            throw new CatalogueRapports.RapportInvalideException("« description » : " + DESCRIPTION_MAX + " caractères au plus");
        }
        if (!(entree.get("definition") instanceof Map<?, ?> definition)) {
            throw new CatalogueRapports.RapportInvalideException("« definition » : la définition du rapport est requise");
        }
        return new Saisie(nom, description, (Map<String, Object>) definition);
    }
}
