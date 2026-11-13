package ca.vina.nexu.demonstration.domaine;

/**
 * Pages HTML des tiers de démonstration, servies hors de l'application React : elles portent
 * elles-mêmes {@code lang}, un seul {@code h1}, un contraste calculé, des cibles de 44 px et un
 * focus visible — le même gabarit que {@code integrations/mocks/page.ts}.
 */
public final class PageHtml {

    private PageHtml() {
    }

    private static final String STYLE = """
              :root { color-scheme: light; }
              body { margin: 0; padding: 24px 16px; font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; color: #1f2937; background: #ffffff; max-width: 720px; margin-inline: auto; }
              h1 { font-size: 1.5rem; margin: 0 0 12px; }
              code { background: #f3f4f6; padding: 0 4px; border-radius: 3px; }
              small { color: #374151; }
              ul.comptes { list-style: none; padding: 0; margin: 16px 0; display: grid; gap: 12px; }
              ul.comptes button { width: 100%; min-height: 44px; text-align: left; padding: 12px 16px; font: inherit; color: #1f2937; background: #f9fafb; border: 2px solid #4b5563; border-radius: 8px; cursor: pointer; }
              ul.comptes button:hover { background: #eef2ff; }
              a, button { outline-offset: 2px; }
              a:focus-visible, button:focus-visible { outline: 3px solid #1d4ed8; }
              a { color: #1d4ed8; display: inline-block; min-height: 44px; line-height: 44px; }
              table { border-collapse: collapse; width: 100%; }
              th, td { border: 1px solid #6b7280; padding: 8px; text-align: left; vertical-align: top; }
              .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
              .table-wrap { overflow-x: auto; }
            """;

    public static String echapper(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    public static String page(String titre, String corps) {
        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>"
                + echapper(titre) + "</title><style>" + STYLE + "</style></head><body><main>" + corps + "</main></body></html>";
    }

    /** La liste de boutons « choisir un compte », commune aux deux IdP de démonstration. */
    public static String boutonsComptes(String prefixeTestId) {
        StringBuilder b = new StringBuilder();
        for (DonneesDemo.CompteEntra c : DonneesDemo.COMPTES_ENTRA) {
            b.append("<li><button type=\"submit\" name=\"compte\" value=\"").append(echapper(c.courriel())).append("\" data-testid=\"")
                    .append(prefixeTestId).append('-').append(c.cle()).append("\">").append(echapper(c.nom())).append("<br><small>")
                    .append(echapper(c.courriel())).append(" — ").append(echapper(String.join(", ", c.roles()))).append("</small></button></li>");
        }
        return b.toString();
    }
}
