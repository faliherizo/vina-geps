package ca.vina.nexu.acces.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Table déclarative des droits — qui peut faire quoi.
 *
 * <p>Portage de {@code backend/src/auth/accessPolicy.ts}. C'est la couche que tout le reste
 * attend : aucun module protégé ne peut être migré avant elle, puisque chacun de ses contrôleurs
 * s'y adosse.
 *
 * <p>Les 25 capacités et leurs rôles sont <strong>extraits</strong> du Node vers
 * {@code politique-acces.json}, jamais retranscrits. Une ligne recopiée de travers donnerait un
 * droit à qui ne l'a pas, ou le retirerait à qui en a besoin — et cela ne se verrait qu'en
 * production, sous forme de 403 inexplicables ou, bien pire, d'un accès accordé à tort.
 */
public record PolitiqueAcces(Map<String, Regle> regles) {

    /**
     * Une règle : les rôles qui détiennent la capacité, son libellé affiché, et la portée qui la
     * borne — un gestionnaire n'agit que sur son équipe, et cette restriction-là ne se lit pas
     * dans la liste des rôles.
     */
    public record Regle(List<String> roles, String libelle, String portee) {
    }

    /** L'ordre des capacités est celui du fichier : il porte le classement de l'écran des rôles. */
    public List<String> capacites() {
        return List.copyOf(regles.keySet());
    }

    public Regle regle(String capacite) {
        return regles.get(capacite);
    }

    /** Capacités attachées par défaut à un rôle intégré. */
    public List<String> defautsPour(String role) {
        List<String> sortie = new java.util.ArrayList<>();
        for (Map.Entry<String, Regle> e : regles.entrySet()) {
            if (e.getValue().roles().contains(role)) {
                sortie.add(e.getKey());
            }
        }
        return sortie;
    }

    public static PolitiqueAcces de(Map<String, Regle> regles) {
        return new PolitiqueAcces(new LinkedHashMap<>(regles));
    }
}
