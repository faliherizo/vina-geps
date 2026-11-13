package ca.vina.nexu.saas.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Les forfaits d'abonnement et leurs quotas par défaut : les modules qu'un organisme reçoit à sa
 * création. Le premier forfait ouvre la planification et ce qu'elle exige ; les suivants, tout le
 * module et les deux modules transversaux. Aucune E/S.
 */
public final class Plans {

    private Plans() {
    }

    public static final List<String> CODES = List.of("starter", "professional", "enterprise", "sovereign");
    public static final List<String> MODULES_TRANSVERSAUX = List.of("integrations", "confidentialite");
    private static final List<String> ENCADREMENT = List.of("approvals", "notifications");
    private static final List<String> OPTIONS_SOUVERAINES = List.of("byok", "dedicated");
    private static final String FAMILLE_EFFECTIFS = "effectifs";
    private static final String PLANIFICATION = "effectifs_planification";

    public record Quota(int maxTeachers, int maxContractsPerYear, List<String> modules) { }

    private static List<String> moduleComplet() {
        return CatalogueModules.MODULES.stream().filter(m -> FAMILLE_EFFECTIFS.equals(m.famille())).map(CatalogueModules.Definition::code).toList();
    }

    /** La planification et la chaîne de ses prérequis, lue au catalogue plutôt que recopiée. */
    private static List<String> planificationSeule() {
        return CatalogueModules.resoudreDependances(List.of(PLANIFICATION), null).modules();
    }

    @SafeVarargs
    private static List<String> concat(List<String> base, List<String>... suites) {
        List<String> tous = new ArrayList<>(base);
        for (List<String> s : suites) {
            tous.addAll(s);
        }
        return List.copyOf(tous);
    }

    public static final Map<String, Quota> PAR_DEFAUT = Map.of(
            "starter", new Quota(50, 200, concat(planificationSeule(), ENCADREMENT)),
            "professional", new Quota(500, 2000, concat(moduleComplet(), ENCADREMENT, MODULES_TRANSVERSAUX)),
            "enterprise", new Quota(-1, -1, concat(moduleComplet(), ENCADREMENT, MODULES_TRANSVERSAUX)),
            "sovereign", new Quota(-1, -1, concat(moduleComplet(), ENCADREMENT, MODULES_TRANSVERSAUX, OPTIONS_SOUVERAINES)));

    public static Quota defauts(String code) {
        return PAR_DEFAUT.get(code);
    }
}
