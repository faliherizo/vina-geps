package ca.vina.nexu.organisation.infrastructure.web;

import ca.vina.nexu.organisation.application.commandes.GestionnaireOrganisation;
import ca.vina.nexu.organisation.domaine.Organisation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La mise en forme JSON du contexte {@code organisation}.
 *
 * <p>Elle existe pour une seule raison : {@code Operation} est un <strong>enum</strong> dans le
 * domaine, et le Node écrit {@code "creation"}, pas {@code "CREATION"}. Laisser le sérialiseur
 * décider produirait deux API qui ne se ressemblent plus — et l'écran, lui, ne lit qu'une forme.
 * La conversion se fait donc <strong>une fois, à la frontière</strong>, comme la conversion
 * inverse à l'entrée.
 */
final class VueOrganisation {

    private VueOrganisation() {
    }

    static Map<String, Object> version(Organisation.VersionUnite v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id());
        m.put("uniteId", v.uniteId());
        m.put("code", v.code());
        m.put("libelle", v.libelle());
        m.put("parentVersionId", v.parentVersionId());
        m.put("valideDu", v.valideDu());
        m.put("valideAu", v.valideAu());
        m.put("operation", v.operation().code());
        m.put("issueDe", v.issueDe());
        m.put("motif", v.motif());
        return m;
    }

    static List<Map<String, Object>> versions(List<Organisation.VersionUnite> versions) {
        return versions.stream().map(VueOrganisation::version).toList();
    }

    static List<Map<String, Object>> organigramme(List<Organisation.VersionDecrite> decrites) {
        return decrites.stream().map(d -> {
            Map<String, Object> m = version(new Organisation.VersionUnite(d.id(), d.uniteId(), d.code(),
                    d.libelle(), d.parentVersionId(), d.valideDu(), d.valideAu(), d.operation(),
                    d.issueDe(), d.motif()));
            m.put("chemin", d.chemin());
            m.put("profondeur", d.profondeur());
            m.put("enVigueur", d.enVigueur());
            return m;
        }).toList();
    }

    static Map<String, Object> filiation(Organisation.Filiation f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("versionId", f.versionId());
        m.put("code", f.code());
        m.put("ascendants", f.ascendants().stream().map(a -> Map.<String, Object>of(
                "id", a.id(), "code", a.code(), "operation", a.operation().code(), "effetLe", a.effetLe())).toList());
        return m;
    }

    static Map<String, Object> resultat(GestionnaireOrganisation.ResultatApplique r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("operation", r.operation());
        m.put("terminees", r.terminees());
        m.put("creees", versions(r.creees()));
        m.put("motifs", r.motifs());
        return m;
    }
}
