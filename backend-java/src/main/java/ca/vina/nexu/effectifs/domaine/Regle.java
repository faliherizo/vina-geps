package ca.vina.nexu.effectifs.domaine;

/**
 * L'identité stable d'une règle : un contexte, une clé, une portée. Ce qui change — condition, action,
 * message, priorité — vit dans ses {@link VersionRegle versions datées}.
 *
 * @param porteeCode nul pour une règle d'organisme
 */
public record Regle(String id, ContexteRegle contexte, String cle, PorteeRegle porteeType, String porteeCode) {

    public Regle avecId(String nouvelId) {
        return new Regle(nouvelId, contexte, cle, porteeType, porteeCode);
    }
}
