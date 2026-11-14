package ca.vina.nexu.personnel.application.commandes;

import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import ca.vina.nexu.personnel.domaine.ComptesPersonnelPort;
import ca.vina.nexu.personnel.domaine.Hierarchie;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <strong>Mon équipe</strong> — ce qu'un gestionnaire voit et gère de sa ligne hiérarchique, comme dans Nexu VINA : ceux qui
 * relèvent de lui, directement ou non, et les personnes encore sans supérieur qu'il peut rattacher. Le cloisonnement est ici,
 * au serveur : un gestionnaire ne reçoit jamais une personne hors de sa chaîne.
 */
public final class GestionnaireEquipe {

    /** Une personne de l'équipe ; {@code niveau} 1 : relève directement du gestionnaire. */
    public record Membre(String id, String nom, String titre, String courriel, String superieurId, String superieurNom, int niveau) { }

    /** L'équipe vue par le gestionnaire ; {@code moiId} nul : son compte n'est relié à aucune fiche du personnel. */
    public record Equipe(String moiId, String moiNom, List<Membre> membres, List<Membre> sansSuperieur) { }

    /** Les ports que l'équipe emploie. */
    public record Ports(ComptesPersonnelPort comptes, AnnuaireEmployes annuaire, RepertoirePersonnes repertoire, Hierarchie.Ecriture ecriture) { }

    private static final String SANS_FICHE = "votre compte n'est relié à aucune fiche du personnel : demandez à l'administration de le rattacher";

    private final Ports ports;

    public GestionnaireEquipe(Ports ports) {
        this.ports = ports;
    }

    private Optional<String> maFiche(String tenantId, String utilisateurId) {
        return ports.comptes().compte(utilisateurId).filter(c -> tenantId.equals(c.tenantId())).map(ComptesPersonnelPort.CompteLie::ficheId);
    }

    public Equipe equipe(String tenantId, String utilisateurId) {
        List<AnnuaireEmployes.Fiche> bassin = ports.annuaire().tous(tenantId);
        Map<String, AnnuaireEmployes.Fiche> parId = bassin.stream().collect(Collectors.toMap(AnnuaireEmployes.Fiche::id, Function.identity()));
        // Une fiche rattachée puis retirée du répertoire ne vaut pas mieux que pas de fiche.
        Optional<String> moi = maFiche(tenantId, utilisateurId).filter(parId::containsKey);
        if (moi.isEmpty()) {
            return new Equipe(null, null, List.of(), List.of());
        }
        Map<String, RepertoirePersonnes.Personne> details = ports.repertoire().tous(tenantId).stream()
                .collect(Collectors.toMap(RepertoirePersonnes.Personne::id, Function.identity(), (a, b) -> a));
        Map<String, Integer> niveaux = Hierarchie.niveaux(moi.get(), bassin);
        Function<AnnuaireEmployes.Fiche, Membre> membre = f -> {
            Optional<RepertoirePersonnes.Personne> p = Optional.ofNullable(details.get(f.id()));
            String chef = Optional.ofNullable(f.gestionnaireId()).map(parId::get).map(AnnuaireEmployes.Fiche::nomComplet).orElse(null);
            return new Membre(f.id(), f.nomComplet(), p.map(RepertoirePersonnes.Personne::titre).orElse(null),
                    p.map(RepertoirePersonnes.Personne::courriel).orElse(null), f.gestionnaireId(), chef, niveaux.getOrDefault(f.id(), 0));
        };
        // L'ordre alphabétique français : « Léa » avant « Luc », ce que l'ordre des codes Unicode inverse.
        Comparator<Membre> ordre = Comparator.comparingInt(Membre::niveau).thenComparing(Membre::nom, Collator.getInstance(Locale.CANADA_FRENCH));
        List<Membre> membres = bassin.stream().filter(f -> niveaux.containsKey(f.id())).map(membre).sorted(ordre).toList();
        List<Membre> libres = bassin.stream().filter(f -> f.gestionnaireId() == null && !f.id().equals(moi.get())).map(membre).sorted(ordre).toList();
        return new Equipe(moi.get(), parId.get(moi.get()).nomComplet(), membres, libres);
    }

    /** Rattacher, réaffecter ou détacher ({@code superieurId} nul) une personne, dans les bornes de l'équipe. */
    public Equipe designer(String tenantId, String utilisateurId, String cible, String superieurId) {
        String moi = maFiche(tenantId, utilisateurId).orElseThrow(() -> new HorsPerimetreException(cible, SANS_FICHE));
        String s = superieurId == null || superieurId.isBlank() ? null : superieurId.strip();
        Hierarchie.verifierDansEquipe(moi, cible, s, ports.annuaire().tous(tenantId));
        ports.ecriture().designerSuperieur(tenantId, cible, s);
        return equipe(tenantId, utilisateurId);
    }
}
