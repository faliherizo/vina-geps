package ca.vina.nexu.demonstration.infrastructure.web;

/** L'origine de ce serveur ({@code MOCK_URL_BASE}, sinon {@code http://localhost:PORT}), sans barre finale. */
public record UrlServeur(String base) {
    public UrlServeur {
        base = base.replaceFirst("/+$", "");
    }
}
