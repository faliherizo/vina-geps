package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.partage.securite.Role;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Ce que les effectifs donnent à la paie (ADR 0076) : les feuilles <strong>approuvées</strong> d'une période, codées
 * comme le lot de paie (E-RF-12), et le verrou PAYEE. La paie couvre tout l'organisme, sans périmètre : elle lit au
 * nom de l'administration, et sa propre garde ({@code paie.gerer}) a déjà jugé de l'acteur.
 */
public class FeuillesPourPaie implements HeuresApprouveesPort, PersonnesPourPaiePort {

    public record Dependances(LectureFeuillesTemps lecture, ExportPaieTemps export, EcritureFeuillesTempsPort ecriture,
                              JournalEffectifsPort journal, DepotRessourcesPort ressources) { }

    private final Dependances d;
    private final Supplier<String> maintenant;

    public FeuillesPourPaie(Dependances dependances, Supplier<String> maintenant) {
        this.d = dependances;
        this.maintenant = maintenant;
    }

    @Override
    public List<FeuilleAPayer> approuvees(String tenantId, String debut, String fin) {
        Contexte ctx = new Contexte(tenantId, null, Role.ADMIN.code(), maintenant.get());
        return d.lecture().depot().lister(tenantId, TempsEffectifs.StatutFeuille.APPROUVEE.name(), null).stream()
                .filter(f -> f.debut().compareTo(debut) >= 0 && f.debut().compareTo(fin) <= 0)
                .map(f -> aPayer(ctx, f)).toList();
    }

    private FeuilleAPayer aPayer(Contexte ctx, FeuilleTemps f) {
        ExportPaieTemps.LignesFeuille l = d.export().lignesDe(ctx, f);
        List<LigneHeures> lignes = l.lignes().stream().map(lp -> new LigneHeures(lp.date(), lp.codePaie(),
                l.codesSupplementaires().contains(lp.codePaie()) ? NatureHeures.SUPPLEMENTAIRE : NatureHeures.REGULIERE,
                new BigDecimal(lp.heures().toString()))).toList();
        return new FeuilleAPayer(f.id(), f.ressourceId(), f.matricule(), f.nom(), f.debut(), lignes);
    }

    @Override
    public void marquerPayees(Verrou verrou, List<String> feuilleIds) {
        Contexte ctx = new Contexte(verrou.tenantId(), verrou.acteurId(), Role.ADMIN.code(), verrou.instant());
        for (String id : feuilleIds) {
            try {
                d.ecriture().marquerPayee(ctx, id, verrou.cycleId());
            } catch (ConflitException e) {
                throw new FeuilleNonApprouvee(e.getMessage(), e);
            }
            d.journal().ecrire(ctx, JournalEffectifs.Consignation.de("temps.payee", "feuille", id, null,
                    Map.of("statut", TempsEffectifs.StatutFeuille.APPROUVEE), Map.of("statut", TempsEffectifs.StatutFeuille.PAYEE, "paieId", verrou.cycleId())));
        }
    }

    @Override
    public List<Personne> personnes(String tenantId) {
        return d.ressources().lister(tenantId).stream().filter(Ressource::actif)
                .sorted(Comparator.comparing(Ressource::nom)).map(r -> new Personne(r.id(), r.matricule(), r.nom())).toList();
    }

    @Override
    public Optional<String> ressourceDe(String tenantId, String utilisateurId) {
        return d.ressources().deLUtilisateur(tenantId, utilisateurId).map(Ressource::id);
    }
}
