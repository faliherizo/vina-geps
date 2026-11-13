package ca.vina.nexu.effectifs.domaine;

/** Port d'écriture des profils de pointage : réservé à l'encadrement, séparé de la lecture que tout pointage emprunte. */
public interface EcritureProfilsPointagePort {

    /** Pose le profil entier : il remplace le précédent du même code, portée comprise. */
    void poser(Contexte ctx, ProfilsPointage.ProfilPointage p);

    void retirer(String tenantId, String code);
}
