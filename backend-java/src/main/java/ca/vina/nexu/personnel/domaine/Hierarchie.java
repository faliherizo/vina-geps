package ca.vina.nexu.personnel.domaine;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;

import java.util.List;

/**
 * <strong>Qui relève de qui</strong> : le supérieur hiérarchique d'une personne, que le flux d'approbation des
 * feuilles de temps lit pour router une feuille à « son gestionnaire hiérarchique ». Rien ne l'écrivait : la
 * colonne {@code employees.managerId} n'était remplie que par une reprise de données.
 *
 * <p>Une personne ne relève ni d'elle-même ni de quelqu'un qui relève d'elle : un cycle laisserait une feuille
 * sans personne au-dessus pour l'approuver.
 */
public final class Hierarchie {

    private Hierarchie() {
    }

    /** Port d'écriture, étroit : poser (ou retirer, {@code null}) le supérieur d'une fiche. */
    public interface Ecriture {
        void designerSuperieur(String tenantId, String id, String superieurId);
    }

    /** Refuse une désignation impossible ; {@code superieurId} nul retire le supérieur. */
    public static void verifier(String id, String superieurId, List<AnnuaireEmployes.Fiche> bassin) {
        if (bassin.stream().noneMatch(f -> f.id().equals(id))) {
            throw new IntrouvableException("employé introuvable : " + id);
        }
        if (superieurId == null) {
            return;
        }
        if (superieurId.equals(id)) {
            throw new FichesPersonnes.FicheInvalideException(List.of("une personne ne relève pas d'elle-même"));
        }
        if (bassin.stream().noneMatch(f -> f.id().equals(superieurId))) {
            throw new IntrouvableException("supérieur introuvable : " + superieurId);
        }
        if (AnnuaireEmployes.subordonnes(id, bassin).stream().anyMatch(f -> f.id().equals(superieurId))) {
            throw new FichesPersonnes.FicheInvalideException(List.of("le supérieur choisi relève déjà de cette personne : la hiérarchie tournerait en rond"));
        }
    }
}
