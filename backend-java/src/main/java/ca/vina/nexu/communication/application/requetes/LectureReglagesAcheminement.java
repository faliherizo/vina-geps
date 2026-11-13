package ca.vina.nexu.communication.application.requetes;

import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.AcheminementUnites;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.CanalReception;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.ModeReception;
import ca.vina.nexu.communication.domaine.ReglagesAcheminementPort;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Le tableau des réglages : chaque unité en vigueur, ses canaux résolus et leur origine (réglage de
 * l'unité ou défaut), avec les nomenclatures que l'écran propose — l'écran n'en écrit aucune en dur.
 */
public final class LectureReglagesAcheminement {

    private final GroupesDeTravailPort groupes;
    private final ReglagesAcheminementPort.Lecture reglages;

    public LectureReglagesAcheminement(GroupesDeTravailPort groupes, ReglagesAcheminementPort.Lecture reglages) {
        this.groupes = groupes;
        this.reglages = reglages;
    }

    public Map<String, Object> lister(Appelant a) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unites", groupes.groupes(a.tenantId()).stream().map(g -> unite(a.tenantId(), g)).toList());
        r.put("canaux", Arrays.stream(CanalReception.values()).map(c -> entree(c.code(), c.libelle())).toList());
        r.put("modes", Arrays.stream(ModeReception.values()).map(m -> entree(m.code(), m.libelle())).toList());
        return r;
    }

    private Map<String, Object> unite(String tenantId, GroupesDeTravailPort.Groupe g) {
        Optional<AcheminementUnites.Reglage> reglage = reglages.reglage(tenantId, g.id());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uniteId", g.id());
        m.put("nom", g.nom());
        m.put("parDefaut", reglage.isEmpty());
        m.put("canaux", AcheminementUnites.canaux(reglage).stream().map(LectureReglagesAcheminement::canalLisible).toList());
        m.put("modifieLe", reglage.map(AcheminementUnites.Reglage::modifieLe).orElse(null));
        m.put("modifiePar", reglage.map(AcheminementUnites.Reglage::modifiePar).orElse(null));
        return m;
    }

    /** Le canal tel que l'écran l'affiche : ses codes, et son libellé « Texto (SMS) — Prioritaire ». */
    private static Map<String, Object> canalLisible(AcheminementPort.Canal c) {
        Map<String, Object> m = new LinkedHashMap<>(c.enCarte());
        m.put("libelle", CanalReception.de(c.canal()).libelle() + " — " + ModeReception.de(c.mode()).libelle());
        return m;
    }

    private static Map<String, Object> entree(String code, String libelle) {
        return Map.of("code", code, "libelle", libelle);
    }

}
