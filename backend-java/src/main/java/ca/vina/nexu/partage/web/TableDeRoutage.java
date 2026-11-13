package ca.vina.nexu.partage.web;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Les routes que Spring sert réellement, lues dans ses correspondances : chemin OpenAPI ({@code {id}}) et verbe, triées. */
public final class TableDeRoutage {

    private TableDeRoutage() {
    }

    public record Route(String methode, String chemin) {
    }

    /** Toutes les routes montées, sans doublon, par chemin puis verbe ; une méthode sans verbe répond à tous ({@code *}). */
    public static List<Route> routes(RequestMappingHandlerMapping mappings) {
        TreeSet<String> cles = new TreeSet<>();
        List<Route> liste = new ArrayList<>();
        mappings.getHandlerMethods().forEach((info, methode) -> {
            for (String chemin : cheminsDe(info)) {
                for (String verbe : verbesDe(info)) {
                    if (cles.add(chemin + " " + verbe)) {
                        liste.add(new Route(verbe, chemin));
                    }
                }
            }
        });
        liste.sort((a, b) -> {
            int c = a.chemin().compareTo(b.chemin());
            return c != 0 ? c : a.methode().compareTo(b.methode());
        });
        return liste;
    }

    private static TreeSet<String> cheminsDe(RequestMappingInfo info) {
        TreeSet<String> chemins = new TreeSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns().forEach(p -> chemins.add(p.getPatternString()));
        }
        return chemins;
    }

    private static TreeSet<String> verbesDe(RequestMappingInfo info) {
        TreeSet<String> verbes = new TreeSet<>();
        info.getMethodsCondition().getMethods().forEach(m -> verbes.add(m.name()));
        if (verbes.isEmpty()) {
            verbes.add("*");
        }
        return verbes;
    }
}
