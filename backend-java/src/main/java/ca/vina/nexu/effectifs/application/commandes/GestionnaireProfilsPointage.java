package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureProfilsPointagePort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ProfilsPointage;

import java.util.Map;

/**
 * <strong>Poser et retirer un profil de pointage</strong> — jumeau de {@code poserProfil} et {@code retirerProfil}
 * ({@code presence/serviceProfils.ts}). Chaque geste est journalisé au nom du mécanisme {@value #MECANISME}.
 */
public class GestionnaireProfilsPointage {

    static final String MECANISME = "presence-profils";
    private static final String OBJET = "profil_pointage";
    private static final String ACTION_POSE = "presence.profil.pose";
    private static final String ACTION_RETRAIT = "presence.profil.retire";

    private final LectureProfilsPointage lire;
    private final EcritureProfilsPointagePort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireProfilsPointage(LectureProfilsPointage lire, EcritureProfilsPointagePort depot, JournalEffectifsPort journal) {
        this.lire = lire;
        this.depot = depot;
        this.journal = journal;
    }

    /** Un profil arrive <b>entier</b> et remplace le précédent du même code, portée comprise. */
    public ProfilsPointage.ProfilPointage poser(Contexte ctx, Map<String, Object> entree) {
        ProfilsPointage.ProfilPointage p = ProfilsPointage.valider(entree);
        ProfilsPointage.ProfilPointage avant = lire.trouver(ctx.tenantId(), p.code()).orElse(null);
        depot.poser(ctx, p);
        journal.ecrire(ctx, new JournalEffectifs.Consignation(ACTION_POSE, OBJET, p.code(), null, avant, p, MECANISME));
        return lire.exiger(ctx.tenantId(), p.code());
    }

    /**
     * Retirer un profil <b>remet les personnes visées au profil par défaut</b> : le journal le dit, pour qu'on ne croie pas
     * avoir seulement masqué une ligne d'écran.
     */
    public Map<String, Object> retirer(Contexte ctx, String code) {
        ProfilsPointage.ProfilPointage avant = lire.exiger(ctx.tenantId(), code);
        depot.retirer(ctx.tenantId(), code);
        journal.ecrire(ctx, new JournalEffectifs.Consignation(ACTION_RETRAIT, OBJET, code, null, avant,
                Map.of("profilApplique", ProfilsPointage.CODE_PAR_DEFAUT), MECANISME));
        return Map.of("retire", code);
    }
}
