package ca.vina.nexu.effectifs.domaine;

/**
 * Une source externe (RAO, BD Corpo) injoignable, en erreur ou qui ne rend pas ce qui est convenu : 502. Jumeau de
 * {@code SourceIndisponibleError} du Node — ce n'est pas la demande qui est fautive, c'est le système tiers.
 */
public class SourceIndisponibleException extends RuntimeException {
    public SourceIndisponibleException(String message) {
        super(message);
    }
}
