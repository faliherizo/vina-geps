package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port de lecture des échanges de quarts ({@code eff_echanges}) et de ce qu'ils lisent des places publiées. Une
 * place s'y lit avec son quart et l'état de sa période, ce que la lecture des équipages (R3) ne donne pas d'un coup.
 */
public interface SourceEchangesEffectifsPort {

    Optional<EchangeEffectifs.PlaceDetaillee> place(String tenantId, String placeId);

    Optional<EchangeEffectifs.Fiche> lire(String tenantId, String id);

    /** Du plus récent au plus ancien. */
    List<EchangeEffectifs.Fiche> lister(String tenantId);

    /** L'échange ouvert qui vise déjà l'une de ces places, s'il y en a un. */
    Optional<String> ouvertSur(String tenantId, String placeA, String placeB);

    /** Toutes les ressources reliées à ce compte. */
    Set<String> ressourcesDuCompte(String tenantId, String utilisateurId);

    /** Les quarts publiés d'une personne, à compter de {@code depuis}, par date puis heure. */
    List<EchangeEffectifs.QuartEchangeable> quartsDe(String tenantId, String ressourceId, String depuis);

    /**
     * Les mêmes quarts dans une <b>fenêtre fermée</b>, passé compris : {@code quartsDe} ne rend que
     * l'à-venir parce que c'est ce qu'un échange peut porter — on ne cède pas un quart déjà
     * travaillé. Lire son horaire est une autre question, et la borne figée à « aujourd'hui » la
     * laissait sans réponse au moment même où la feuille de temps demande de vérifier le planifié
     * de la semaine écoulée.
     */
    List<EchangeEffectifs.QuartEchangeable> quartsEntre(String tenantId, String ressourceId, FenetreHoraire fenetre);

    /**
     * Les quarts publiés à venir de la même unité et du même corps d'emploi que {@code cedee}, tenus par
     * quelqu'un d'autre et sans échange ouvert.
     */
    List<EchangeEffectifs.QuartEchangeable> demandables(String tenantId, EchangeEffectifs.PlaceDetaillee cedee, String depuis);
}
