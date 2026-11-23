package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.partage.securite.Role;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ce que les effectifs donnent à la paie (ADR 0076, 0084) : les feuilles d'une période, codées comme le lot de paie
 * (E-RF-12), avec leurs segments payés en heure de Montréal, les jours fériés, le verrou PAYEE et le geste « retourner ».
 * La paie couvre tout l'organisme, sans périmètre : elle lit au nom de l'administration, et sa propre garde
 * ({@code paie.gerer}) a déjà jugé de l'acteur.
 */
public class FeuillesPourPaie implements HeuresApprouveesPort, PersonnesPourPaiePort {

    /** Les primes de quart se jugent à l'heure de l'établissement, et non à celle du serveur ni en UTC. */
    private static final ZoneId FUSEAU = ZoneId.of("America/Montreal");

    public record Dependances(LectureFeuillesTemps lecture, ExportPaieTemps export, EcritureFeuillesTempsPort ecriture,
                              JournalEffectifsPort journal, DepotRessourcesPort ressources) { }

    private final Dependances d;
    private final GestionnaireApprobationTemps approbation;
    private final Supplier<String> maintenant;

    public FeuillesPourPaie(Dependances dependances, GestionnaireApprobationTemps approbation, Supplier<String> maintenant) {
        this.d = dependances;
        this.approbation = approbation;
        this.maintenant = maintenant;
    }

    private Contexte administration(String tenantId) {
        return new Contexte(tenantId, null, Role.ADMIN.code(), maintenant.get());
    }

    @Override
    public List<FeuilleAPayer> approuvees(String tenantId, String debut, String fin) {
        Contexte ctx = administration(tenantId);
        return dansLaPeriode(tenantId, TempsEffectifs.StatutFeuille.APPROUVEE.name(), debut, fin).stream().map(f -> aPayer(ctx, f)).toList();
    }

    @Override
    public List<FeuilleDeLaPeriode> feuilles(String tenantId, String debut, String fin) {
        Contexte ctx = administration(tenantId);
        return dansLaPeriode(tenantId, null, debut, fin).stream().map(f -> deLaPeriode(ctx, f)).toList();
    }

    @Override
    public Optional<FeuilleDeLaPeriode> feuille(String tenantId, String feuilleId) {
        Contexte ctx = administration(tenantId);
        return d.lecture().depot().feuille(tenantId, feuilleId).map(l -> deLaPeriode(ctx, l.feuille()));
    }

    @Override
    public Set<LocalDate> joursFeries(String tenantId, LocalDate debut, LocalDate fin) {
        Contexte ctx = administration(tenantId);
        Set<LocalDate> feries = new LinkedHashSet<>();
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            if (d.lecture().estFerie(ctx, jour.toString())) {
                feries.add(jour);
            }
        }
        return feries;
    }

    private List<FeuilleTemps> dansLaPeriode(String tenantId, String statut, String debut, String fin) {
        return d.lecture().depot().lister(tenantId, statut, null).stream()
                .filter(f -> f.debut().compareTo(debut) >= 0 && f.debut().compareTo(fin) <= 0).toList();
    }

    /** Une feuille et son état ; ses heures si les effectifs savent les coder, sinon la raison. */
    private FeuilleDeLaPeriode deLaPeriode(Contexte ctx, FeuilleTemps f) {
        EtatFeuille etat = EtatFeuille.valueOf(f.statut().name());
        try {
            return new FeuilleDeLaPeriode(f.id(), f.ressourceId(), f.matricule(), f.nom(), f.debut(), etat, Optional.of(aPayer(ctx, f)), null);
        } catch (HeuresIncalculables e) {
            return new FeuilleDeLaPeriode(f.id(), f.ressourceId(), f.matricule(), f.nom(), f.debut(), etat, Optional.empty(), e.getMessage());
        }
    }

    private FeuilleAPayer aPayer(Contexte ctx, FeuilleTemps f) {
        ExportPaieTemps.LignesFeuille l;
        try {
            l = d.export().lignesDe(ctx, f);
        } catch (EffectifsInvalideException e) {
            throw new HeuresIncalculables(e.getMessage(), e);
        }
        List<LigneHeures> lignes = l.lignes().stream().map(lp -> new LigneHeures(lp.date(), lp.codePaie(),
                l.codesSupplementaires().contains(lp.codePaie()) ? NatureHeures.SUPPLEMENTAIRE : NatureHeures.REGULIERE,
                new BigDecimal(lp.heures().toString()))).toList();
        List<Plage> plages = l.segments().stream().map(s -> new Plage(local(s.debut()), local(s.fin()), s.codePaie())).toList();
        return new FeuilleAPayer(f.id(), f.ressourceId(), f.matricule(), f.nom(), f.debut(), lignes, plages);
    }

    private static LocalDateTime local(String iso) {
        return LocalDateTime.ofInstant(Planification.instantDe(iso).orElseThrow(), FUSEAU);
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

    /**
     * Rouvrir au nom de la paie : le geste du gestionnaire, consigné au journal des feuilles avec l'acteur de la paie. Les
     * effectifs refusent une feuille qui n'est plus approuvée (payée, déjà rouverte) ; le refus passe dans le vocabulaire du port.
     */
    @Override
    public void retourner(Verrou verrou, String feuilleId, String motif) {
        Contexte ctx = new Contexte(verrou.tenantId(), verrou.acteurId(), Role.ADMIN.code(), verrou.instant());
        try {
            approbation.rouvrir(ctx, feuilleId, Map.of("motif", motif));
        } catch (EffectifsInvalideException e) {
            throw new FeuilleNonApprouvee(e.getMessage(), e);
        }
    }

    @Override
    public List<Personne> personnes(String tenantId) {
        return d.ressources().lister(tenantId).stream().filter(Ressource::actif)
                .sorted(Comparator.comparing(Ressource::nom))
                .map(r -> new Personne(r.id(), r.matricule(), r.nom(), r.titreEmploiCode(), r.conventionCode())).toList();
    }

    @Override
    public Optional<String> ressourceDe(String tenantId, String utilisateurId) {
        return d.ressources().deLUtilisateur(tenantId, utilisateurId).map(Ressource::id);
    }
}
