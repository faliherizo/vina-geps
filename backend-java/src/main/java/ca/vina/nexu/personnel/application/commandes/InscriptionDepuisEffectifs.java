package ca.vina.nexu.personnel.application.commandes;

import ca.vina.nexu.partage.personnel.FichesPersonnelPort;
import ca.vina.nexu.personnel.domaine.ComptesPersonnelPort;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;

/**
 * La fiche d'une personne, écrite depuis sa ressource d'effectifs : un compte, une ressource, une
 * fiche. Le compte est rattaché à la fiche créée, ce qui permet de l'aviser par son nom. Rien ne se
 * passe si le compte a déjà sa fiche, s'il n'existe pas, ou s'il appartient à un autre établissement
 * — une ressource ne crée jamais de fiche hors de son organisme.
 */
public final class InscriptionDepuisEffectifs implements FichesPersonnelPort {

    private final ComptesPersonnelPort comptes;
    private final GestionnairePersonnes gestionnaire;

    public InscriptionDepuisEffectifs(ComptesPersonnelPort comptes, GestionnairePersonnes gestionnaire) {
        this.comptes = comptes;
        this.gestionnaire = gestionnaire;
    }

    @Override
    public void inscrire(String tenantId, Inscription inscription) {
        comptes.compte(inscription.utilisateurId())
                .filter(c -> tenantId.equals(c.tenantId()) && c.ficheId() == null)
                .ifPresent(c -> {
                    String ficheId = gestionnaire.creer(tenantId, FichesPersonnes.depuisNomComplet(inscription.nomComplet(), c.courriel())).id();
                    comptes.rattacher(inscription.utilisateurId(), ficheId);
                });
    }
}
