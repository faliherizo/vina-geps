package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Port des campagnes AGPH ({@code eff_agph_campagnes}, {@code eff_agph_rangs}, {@code eff_agph_postes}, {@code eff_agph_choix}). */
public interface DepotCampagnesAgphPort {

    /** Un choix à consigner ; {@code choisiPar} est le mécanisme pour un choix automatique. */
    record ChoixAConsigner(String ressourceId, String posteId, Agph.ModeChoix mode, String choisiLe, String choisiPar) { }

    /** Ce que l'ouverture fige : les rangs et les postes offerts. */
    record Ouverture(List<Agph.RangCalcule> rangs, List<String> postes, String instant) { }

    Optional<AgphCampagne> lire(String tenantId, String id);

    /** Par année décroissante, puis unité. */
    List<AgphCampagne> lister(String tenantId);

    boolean existe(String tenantId, String uniteCode, String corpsEmploiCode, int annee);

    void inserer(String tenantId, AgphCampagne c);

    /** Les ressources actives de l'unité et du corps : les candidats à ordonner. */
    List<Agph.Candidat> candidats(String tenantId, String uniteCode, String corpsEmploiCode);

    /** Les postes publiés (ou vacants) et sans titulaire de l'unité et du corps. */
    List<String> postesOffrables(String tenantId, String uniteCode, String corpsEmploiCode);

    void ouvrir(String tenantId, String id, Ouverture o);

    void poserEtat(String tenantId, String id, Agph.EtatCampagne etat);

    void clore(String tenantId, String id, String instant);

    /** Par rang, avec le nom et le matricule de chaque personne. */
    List<AgphRang> rangs(String tenantId, String campagneId);

    /** Les campagnes où figure cette ressource. */
    Set<String> campagnesDe(String tenantId, String ressourceId);

    void donnerTour(String tenantId, String campagneId, String ressourceId, String debut, String fin);

    void poserEtatRang(String tenantId, String campagneId, String ressourceId, Agph.EtatRang etat);

    /** Les postes offerts et encore libres, dans l'ordre de leur code. */
    List<String> postesLibres(String tenantId, String campagneId);

    void insererChoix(String tenantId, String campagneId, ChoixAConsigner c);

    /** Dans l'ordre où ils ont été faits. */
    List<AgphChoix> choix(String tenantId, String campagneId);

    /** Fixe (ou efface, {@code budget} nul) le budget d'heures hebdomadaires de la campagne. */
    void parametrer(String tenantId, String id, Double budget);

    /** Passe la campagne en révision, datée. */
    void reviser(String tenantId, String id, String instant);

    /** Retire à une personne le poste obtenu : son choix s'efface, son rang devient « passé ». */
    void retirerChoix(String tenantId, String campagneId, String ressourceId);

    /** Les postes offerts que personne n'a obtenus, dans l'ordre de leur code, avec leur état du moment. */
    List<AgphPosteNonPourvu> postesNonPourvus(String tenantId, String campagneId);
}
