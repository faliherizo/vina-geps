package ca.vina.nexu.paie.application.requetes;

import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.Baremes;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.Cumuls;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.Declarations;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.LecturePaiePort;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Les lectures de la paie : dossiers, cycles et bulletins, remises et relevés, et les bulletins de chacun. */
public class LecturePaie {

    /** Une personne des effectifs et son dossier, s'il existe : l'écran des dossiers montre aussi ceux qui en manquent. */
    public record PersonneEtDossier(PersonnesPourPaiePort.Personne personne, DossierRemuneration dossier) { }

    private final LecturePaiePort depot;
    private final Baremes baremes;
    private final PersonnesPourPaiePort personnes;

    public LecturePaie(LecturePaiePort depot, Baremes baremes, PersonnesPourPaiePort personnes) {
        this.depot = depot;
        this.baremes = baremes;
        this.personnes = personnes;
    }

    public Bareme bareme(int annee) {
        return baremes.pour(annee).orElseThrow(() -> RefusPaie.invalide("aucun barème de paie publié pour " + annee
                + " : ajoutez paie/bareme-" + annee + ".json avant de payer cette année"));
    }

    public ReglagesPaie reglages(String tenantId, int annee) {
        return depot.reglages(tenantId).orElseGet(() -> ReglagesPaie.parDefaut(bareme(annee)));
    }

    public List<PersonneEtDossier> dossiers(String tenantId) {
        Map<String, DossierRemuneration> dossiers = dossiersParPersonne(tenantId);
        return personnes.personnes(tenantId).stream().map(p -> new PersonneEtDossier(p, dossiers.get(p.ressourceId()))).toList();
    }

    public Map<String, DossierRemuneration> dossiersParPersonne(String tenantId) {
        return depot.dossiers(tenantId).stream().collect(Collectors.toMap(DossierRemuneration::ressourceId, Function.identity()));
    }

    /** Le cumul de chacun avant un nouveau cycle : la somme de ses bulletins traités de l'année. */
    public Function<String, Cumuls> cumulsAvant(String tenantId, int annee) {
        Map<String, Cumuls> cumuls = new HashMap<>();
        depot.bulletinsTraites(tenantId, annee)
                .forEach(b -> cumuls.put(b.ressourceId(), cumuls.getOrDefault(b.ressourceId(), Cumuls.AUCUN).plus(b.retenues())));
        return ressourceId -> cumuls.getOrDefault(ressourceId, Cumuls.AUCUN);
    }

    public List<CyclePaie> cycles(String tenantId) {
        return depot.cycles(tenantId);
    }

    public CyclePaie exigerCycle(String tenantId, String id) {
        return depot.cycle(tenantId, id).orElseThrow(() -> RefusPaie.introuvable("cycle de paie « " + id + " » introuvable"));
    }

    public List<Bulletin> bulletins(String tenantId, String cycleId) {
        exigerCycle(tenantId, cycleId);
        return depot.bulletins(tenantId, cycleId);
    }

    /** Les bulletins d'un compte : ceux de la ressource d'effectifs qui lui est reliée ; aucun s'il n'en a pas. */
    public List<Bulletin> mesBulletins(String tenantId, String utilisateurId) {
        return personnes.ressourceDe(tenantId, utilisateurId).map(r -> depot.bulletinsTraitesDe(tenantId, r)).orElse(List.of());
    }

    public List<Declarations.Remise> remises(String tenantId, int annee) {
        return Declarations.remises(depot.bulletinsTraites(tenantId, annee));
    }

    public List<Declarations.Releve> releves(String tenantId, int annee) {
        return Declarations.releves(depot.bulletinsTraites(tenantId, annee));
    }
}
