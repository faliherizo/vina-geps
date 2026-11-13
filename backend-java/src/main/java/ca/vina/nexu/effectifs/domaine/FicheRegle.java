package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Une règle et toutes ses versions, par numéro. */
public record FicheRegle(Regle regle, List<VersionRegle> versions) {

    public FicheRegle {
        versions = List.copyOf(versions);
    }

    /**
     * La version en vigueur à la date, sinon la version ouverte (à venir). Vide seulement quand la règle est
     * retirée : une règle pas encore en vigueur montre sa version à venir, pas un « rien » qui dirait retirée.
     */
    public Optional<VersionRegle> courante(String date) {
        return Referentiels.enVigueur(versions, date).or(() -> Referentiels.versionOuverte(versions));
    }
}
