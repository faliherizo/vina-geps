package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.LectureProfilsPointagePort;
import ca.vina.nexu.effectifs.domaine.NaturePause;
import ca.vina.nexu.effectifs.domaine.PorteesDuDossier;
import ca.vina.nexu.effectifs.domaine.ProfilsPointage;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Les profils de pointage</strong> (E-GP-1, E-GP-2, P-GP-1) — jumeau de la lecture de {@code presence/serviceProfils.ts}.
 * La décision vit dans {@link ProfilsPointage}, sans E/S ; ce service lit, et fait la seule chose que le domaine ne peut pas :
 * <b>résoudre les portées d'une ressource</b> (son unité et ses ancêtres à la date, ses appartenances d'emploi).
 */
public class LectureProfilsPointage {

    private static final int DATE = 10;

    private final LectureProfilsPointagePort depot;
    private final EvaluateurRegles regles;
    private final LectureRessources ressources;

    public LectureProfilsPointage(LectureProfilsPointagePort depot, EvaluateurRegles regles, LectureRessources ressources) {
        this.depot = depot;
        this.regles = regles;
        this.ressources = ressources;
    }

    public List<ProfilsPointage.ProfilPointage> lister(String tenantId) {
        return depot.lister(tenantId);
    }

    public Optional<ProfilsPointage.ProfilPointage> trouver(String tenantId, String code) {
        return lister(tenantId).stream().filter(p -> p.code().equals(code)).findFirst();
    }

    public ProfilsPointage.ProfilPointage exiger(String tenantId, String code) {
        return trouver(tenantId, code).orElseThrow(() -> new IntrouvableException("profil de pointage « " + code + " » introuvable"));
    }

    /**
     * Le profil qui s'applique à la personne. {@code date} lit la hiérarchie <b>telle qu'elle était</b> : juger un pointage
     * d'il y a six mois avec l'organigramme d'aujourd'hui appliquerait la réorganisation d'après.
     */
    public ProfilsPointage.ProfilPointage deLaRessource(String tenantId, Ressource r, String date) {
        String jour = date.substring(0, Math.min(date.length(), DATE));
        PorteesDuDossier portees = PorteesDuDossier.de(regles.unitesEtAncetres(tenantId, r.uniteCode(), jour), r.porteesDeRegle());
        return ProfilsPointage.profilDe(lister(tenantId), portees);
    }

    /** Le profil de la personne connectée : c'est lui qui dit à l'écran s'il doit offrir « Pointer ». Sans dossier : le défaut. */
    public Map<String, Object> monProfil(Contexte ctx) {
        Optional<Ressource> r = ressources.deLActeur(ctx);
        Map<String, Object> vue = new LinkedHashMap<>();
        vue.put("profil", r.map(x -> deLaRessource(ctx.tenantId(), x, ctx.maintenant())).orElse(ProfilsPointage.PROFIL_PAR_DEFAUT));
        vue.put("ressourceId", r.map(Ressource::id).orElse(null));
        return vue;
    }

    /** Les ensembles fermés, pour que l'écran ne les code pas. */
    public static Map<String, Object> referentiels() {
        Map<String, Object> vue = new LinkedHashMap<>();
        vue.put("modes", Arrays.stream(ProfilsPointage.ModeSaisie.values()).map(ProfilsPointage.ModeSaisie::code).toList());
        vue.put("naturesNonPayees", Arrays.stream(NaturePause.values()).map(NaturePause::code).toList());
        return vue;
    }
}
