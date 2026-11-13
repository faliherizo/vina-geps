package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <strong>Liaison des unités avec l'organigramme</strong> — AO 26-015-DO, lot R12 (décision D-3) ; le pur de
 * {@code backend/src/effectifs/liaisons/organisation.ts}.
 *
 * <p>L'organigramme fait foi pour la structure ; les unités des effectifs le suivent. Le plan dit ce qui est à créer,
 * à modifier, identique, ignoré avec son motif. Rien n'est fermé d'office : une unité d'effectifs absente de
 * l'organigramme est signalée, pas supprimée — des quarts et des périmètres y tiennent.
 */
public final class AlignementOrganigramme {

    private static final Pattern CODE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private static final String PARENT = "parent";

    private AlignementOrganigramme() {
    }

    public record UniteCible(String code, String libelle, String parent) { }

    public record Avant(String libelle, String parent) { }

    public record Modification(String code, String libelle, String parent, Avant avant) { }

    public record Ignoree(String code, String motif) { }

    public record Plan(List<UniteCible> creer, List<Modification> modifier, List<String> identiques, List<Ignoree> ignorees, List<String> horsOrganigramme) { }

    /** Le plan, pur : l'organigramme en vigueur d'un côté, les unités d'effectifs de l'autre. */
    public static Plan planAlignement(List<OrganigrammePourEffectifsPort.UniteDeLOrganigramme> org, List<ValeurReferentiel> unites) {
        Map<String, String> codeDeVersion = new HashMap<>();
        org.forEach(v -> codeDeVersion.put(v.id(), v.code()));
        Map<String, ValeurReferentiel> existantes = new HashMap<>();
        unites.forEach(u -> existantes.put(u.code(), u));
        Plan plan = new Plan(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Set<String> retenues = new HashSet<>();
        org.stream().filter(OrganigrammePourEffectifsPort.UniteDeLOrganigramme::enVigueur)
                .sorted(Comparator.comparingInt(OrganigrammePourEffectifsPort.UniteDeLOrganigramme::profondeur)
                        .thenComparing(OrganigrammePourEffectifsPort.UniteDeLOrganigramme::code))
                .forEach(v -> classer(v, codeDeVersion, existantes, retenues, plan));
        Set<String> ignorees = new HashSet<>();
        plan.ignorees().forEach(i -> ignorees.add(i.code()));
        unites.stream().map(ValeurReferentiel::code).filter(c -> !retenues.contains(c) && !ignorees.contains(c)).sorted().forEach(plan.horsOrganigramme()::add);
        return plan;
    }

    private static void classer(OrganigrammePourEffectifsPort.UniteDeLOrganigramme v, Map<String, String> codeDeVersion,
                                Map<String, ValeurReferentiel> existantes, Set<String> retenues, Plan plan) {
        if (!CODE.matcher(v.code()).matches()) {
            plan.ignorees().add(new Ignoree(v.code(), "code hors du format des référentiels (lettres, chiffres, tirets, soulignés ; 40 au plus)"));
            return;
        }
        String parent = v.parentVersionId() == null ? null : codeDeVersion.get(v.parentVersionId());
        if (parent != null && !retenues.contains(parent) && !existantes.containsKey(parent)) {
            plan.ignorees().add(new Ignoree(v.code(), "son unité parente « " + parent + " » n'est pas reprise"));
            return;
        }
        retenues.add(v.code());
        ValeurReferentiel e = existantes.get(v.code());
        if (e == null) {
            plan.creer().add(new UniteCible(v.code(), v.libelle(), parent));
            return;
        }
        String parentActuel = e.attributs().get(PARENT) instanceof String s && !s.isEmpty() ? s : null;
        if (e.libelleFr().equals(v.libelle()) && java.util.Objects.equals(parentActuel, parent)) {
            plan.identiques().add(v.code());
        } else {
            plan.modifier().add(new Modification(v.code(), v.libelle(), parent, new Avant(e.libelleFr(), parentActuel)));
        }
    }
}
