package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/**
 * Port du comblement des places libérées par une absence ({@code eff_comblements}, {@code eff_comblement_avis}) :
 * ce que le mécanisme a fait de chaque place, et les avis envoyés. Que la place soit pourvue aujourd'hui se lit sur
 * la place, jamais ici.
 */
public interface DepotComblementsAbsencesPort {

    /** Ce que le mécanisme a fait d'une place. */
    record Comblement(String id, String absenceId, String placeId, Planification.ModeAttribution mode, AbsencesEffectifs.ResultatComblement resultat,
                      String attribueeA, String creeLe) { }

    /** Un avis envoyé pour une place, par canal, avec ce que le canal a rendu. */
    record Avis(String comblementId, String ressourceId, String canal, String statut, String erreur, String envoyeLe) { }

    /** Une place comblée, lue avec son quart et qui l'occupe aujourd'hui (forme JSON du Node). */
    record LigneComblement(String id, String placeId, String date, int debut, int fin, String mode, String resultat, String attribueeA,
                           String attribueeANom, String occupeeParId, String occupeeParNom, String creeLe) { }

    /** Un avis lu avec le nom de la personne. */
    record LigneAvis(String comblementId, String ressourceId, String nom, String canal, String statut, String erreur, String envoyeLe) { }

    void inserer(String tenantId, Comblement c, String mecanisme);

    void aviser(String tenantId, Avis a);

    /** Par date, début, puis création. */
    List<LigneComblement> deLAbsence(String tenantId, String absenceId);

    /** Par nom, puis canal. */
    List<LigneAvis> avisDeLAbsence(String tenantId, String absenceId);

    /** L'identifiant d'employé d'un compte : c'est lui que les préférences et l'annuaire des avis connaissent. */
    Optional<String> employeDuCompte(String utilisateurId);
}
