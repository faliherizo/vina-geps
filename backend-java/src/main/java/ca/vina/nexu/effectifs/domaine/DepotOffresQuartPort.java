package ca.vina.nexu.effectifs.domaine;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port des offres de quart (AD-07 ; {@code eff_offres}, {@code eff_offre_rangs}) : l'ordre figé à l'ouverture, ce que
 * chaque rang en a fait, et le compte d'heures qui fait tourner le rôle (16.02, 16.03).
 */
public interface DepotOffresQuartPort {

    /** Une proposition faite à un rang, à un instant, jusqu'à une échéance. */
    record Proposition(String offreId, String ressourceId, String offertLe, String echeance) { }

    /** Ce qu'un rang a fait de l'offre, à quel instant, et pourquoi s'il a été écarté. */
    record Reponse(String offreId, String ressourceId, OffresQuart.EtatRang etat, String le, String motif) { }

    /** La fin d'une offre : les rangs encore offerts ou en attente sont clos, sans être comptés. */
    record Fermeture(String offreId, OffresQuart.EtatOffre etat, String attribueeA, String le) { }

    void ouvrir(String tenantId, OffreQuart offre);

    Optional<OffreQuart> lire(String tenantId, String offreId);

    void proposer(String tenantId, Proposition p);

    void repondre(String tenantId, Reponse r);

    void fermer(String tenantId, Fermeture f);

    /** Les heures comptées de chaque personne depuis un instant, dans les états donnés ; une personne sans heures n'y figure pas. */
    Map<String, Double> heuresComptees(String tenantId, Collection<String> ressourceIds, String depuis, Collection<OffresQuart.EtatRang> etats);

    /** Les offres ouvertes dont une proposition est échue à cet instant. */
    List<String> ouvertesEchues(String tenantId, String maintenant);

    /** Les offres où cette personne a un rang offert, la plus proche échéance d'abord. */
    List<OffreQuart> offertesA(String tenantId, String ressourceId);

    /** Les offres nées du comblement d'une absence, par date du quart. */
    List<OffreQuart> deLAbsence(String tenantId, String absenceId);
}
