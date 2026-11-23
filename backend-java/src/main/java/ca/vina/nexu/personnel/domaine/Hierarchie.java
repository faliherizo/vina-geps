package ca.vina.nexu.personnel.domaine;

import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** L'équipe d'une personne : tous ceux qui relèvent d'elle, directement ou non. */
    public static Set<String> equipe(String moi, List<AnnuaireEmployes.Fiche> bassin) {
        return AnnuaireEmployes.subordonnes(moi, bassin).stream().map(AnnuaireEmployes.Fiche::id).collect(Collectors.toSet());
    }

    /**
     * <strong>Ce qu'un gestionnaire peut changer dans son équipe</strong>, et rien au-delà : rattacher une personne sans
     * supérieur, réaffecter un membre de son équipe à lui-même ou à un autre membre, détacher l'un de ses subordonnés
     * directs. Le reste de l'organigramme appartient à l'administration.
     */
    public static void verifierDansEquipe(String moi, String cible, String superieurId, List<AnnuaireEmployes.Fiche> bassin) {
        Set<String> equipe = equipe(moi, bassin);
        AnnuaireEmployes.Fiche fiche = bassin.stream().filter(f -> f.id().equals(cible)).findFirst()
                .orElseThrow(() -> new IntrouvableException("employé introuvable : " + cible));
        if (!equipe.contains(cible) && fiche.gestionnaireId() != null) {
            throw new HorsPerimetreException(cible, "« " + fiche.nomComplet() + " » ne relève pas de votre équipe");
        }
        if (superieurId == null && !moi.equals(fiche.gestionnaireId())) {
            throw new HorsPerimetreException(cible, "seul le supérieur direct de « " + fiche.nomComplet() + " » peut le détacher");
        }
        if (superieurId != null && !superieurId.equals(moi) && !equipe.contains(superieurId)) {
            throw new HorsPerimetreException(cible, "le nouveau supérieur doit être vous ou un membre de votre équipe");
        }
        verifier(cible, superieurId, bassin);
    }

    /** La profondeur de chaque membre sous la personne, en largeur d'abord : 1 pour un subordonné direct. */
    public static Map<String, Integer> niveaux(String moi, List<AnnuaireEmployes.Fiche> bassin) {
        Map<String, Integer> niveaux = new LinkedHashMap<>();
        Deque<String> file = new ArrayDeque<>(List.of(moi));
        while (!file.isEmpty()) {
            String chef = file.poll();
            int niveau = niveaux.getOrDefault(chef, 0) + 1;
            bassin.stream().filter(f -> chef.equals(f.gestionnaireId()) && !niveaux.containsKey(f.id()) && !f.id().equals(moi)).forEach(f -> {
                niveaux.put(f.id(), niveau);
                file.add(f.id());
            });
        }
        return niveaux;
    }
}
