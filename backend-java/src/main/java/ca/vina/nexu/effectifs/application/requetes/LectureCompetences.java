package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Competences;
import ca.vina.nexu.effectifs.domaine.CompetencesPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <strong>Les compétences, lues</strong> (addenda 8, M-04) : celles d'une personne, le relevé des échéances de ce que
 * l'acteur voit, et les faits d'une attribution — les compétences exigées du corps d'emploi au référentiel, en vigueur à
 * la date du quart, confrontées à celles que la personne détient.
 */
public class LectureCompetences implements Competences.Source {

    private final CompetencesPort depot;
    private final DepotReferentielsPort referentiels;
    private final LectureRessources ressources;

    public LectureCompetences(CompetencesPort depot, DepotReferentielsPort referentiels, LectureRessources ressources) {
        this.depot = depot;
        this.referentiels = referentiels;
        this.ressources = ressources;
    }

    /** Les codes exigés du corps d'emploi le jour {@code date}. */
    public List<String> exigees(String tenantId, String corpsEmploiCode, String date) {
        return referentiels.enVigueur(tenantId, TypeReferentiel.COMPETENCES, date).stream()
                .filter(v -> v.attributs().get(Competences.ATTRIBUT_CORPS) instanceof List<?> l && l.stream().map(String::valueOf).anyMatch(corpsEmploiCode::equals))
                .map(ValeurReferentiel::code).sorted().toList();
    }

    @Override
    public Competences.Faits faits(String tenantId, String ressourceId, String corpsEmploiCode, String date) {
        return Competences.Faits.de(exigees(tenantId, corpsEmploiCode, date), depot.deLaRessource(tenantId, ressourceId), date);
    }

    /** Les compétences d'une personne, chacune avec son état au jour, et celles que son corps exige sans qu'elle les ait. */
    public Map<String, Object> deLaRessource(Contexte ctx, String ressourceId) {
        Ressource r = ressources.exiger(ctx, ressourceId);
        ressources.exigerDroitSur(ctx, r, Perimetres.Niveau.LECTURE);
        String jour = ctx.aujourdhui();
        List<Competences.Detenue> detenues = depot.deLaRessource(ctx.tenantId(), r.id());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ressourceId", r.id());
        m.put("detenues", detenues.stream().map(d -> vue(d, jour)).toList());
        m.put("manquantes", Competences.manquantes(exigees(ctx.tenantId(), r.corpsEmploiCode(), jour), detenues, jour));
        return m;
    }

    /** Le relevé : les compétences échues ou qui échoient dans l'horizon, des personnes que l'acteur voit. */
    public List<Map<String, Object>> releve(Contexte ctx, String dans) {
        int horizon = Competences.horizon(dans);
        Map<String, Ressource> visibles = ressources.lister(ctx, null, null).stream().collect(Collectors.toMap(Ressource::id, Function.identity()));
        List<Competences.Detenue> siennes = depot.avecEcheance(ctx.tenantId()).stream().filter(d -> visibles.containsKey(d.ressourceId())).toList();
        return Competences.releve(siennes, ctx.aujourdhui(), horizon).stream().map(e -> {
            Ressource r = visibles.get(e.competence().ressourceId());
            Map<String, Object> m = new LinkedHashMap<>(vue(e.competence(), ctx.aujourdhui()));
            m.put("ressourceNom", r.nom());
            m.put("uniteCode", r.uniteCode());
            m.put("etat", e.etat().code());
            m.put("jours", e.jours());
            return m;
        }).toList();
    }

    private static Map<String, Object> vue(Competences.Detenue d, String jour) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ressourceId", d.ressourceId());
        m.put("code", d.code());
        m.put("obtenueLe", d.obtenueLe());
        m.put("echeance", d.echeance());
        m.put("valide", d.valideLe(jour));
        return m;
    }
}
