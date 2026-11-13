package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReglesPort;
import ca.vina.nexu.effectifs.domaine.EvaluationRegle;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PorteesDuDossier;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.VersionRegle;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <strong>La porte d'évaluation du moteur de règles</strong>, celle que les lots R3 à R9 empruntent (jumeau
 * de {@code evaluerDossier} et {@code jugerSansTrace}, {@code backend/src/effectifs/regles/service.ts}). Le
 * moteur ne lit aucune table métier : l'appelant fournit le dossier de faits et les portées de la personne.
 */
public class EvaluateurRegles {

    /** Le nom sous lequel les décisions automatiques sont journalisées — jamais l'utilisateur qui les a déclenchées. */
    public static final String MECANISME = "moteur-de-regles";
    private static final String SIMULATION = "simulation";

    private final DepotReglesPort depot;
    private final LectureReferentiels referentiels;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public EvaluateurRegles(DepotReglesPort depot, LectureReferentiels referentiels, JournalEffectifsPort journal, Sequence sequence) {
        this.depot = depot;
        this.referentiels = referentiels;
        this.journal = journal;
        this.sequence = sequence;
    }

    /**
     * Évalue un dossier, conserve la décision avec les versions appliquées et les faits, et la journalise
     * sous le <strong>mécanisme</strong>, en nommant qui l'a déclenchée.
     */
    public Regles.Verdict evaluerDossier(Contexte ctx, Regles.Dossier d) {
        Regles.Verdict verdict = jugerSansTrace(ctx, d);
        depot.enregistrer(ctx.tenantId(), new EvaluationRegle(sequence.suivant("erev"), d.contexte().code(), d.objetType(), d.objetId(),
                verdict.action().code(), verdict.motifs(), verdict.versions(), d.faits(), d.date(), ctx.maintenant(), ctx.acteurId()));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("action", verdict.action().code());
        apres.put("motifs", verdict.motifs().stream().map(Regles.Motif::cle).toList());
        apres.put("declenchePar", ctx.acteurId());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("regles." + d.contexte().code().toLowerCase(Locale.ROOT), d.objetType(), d.objetId(),
                d.unite(), null, apres, MECANISME));
        return verdict;
    }

    /**
     * Le même jugement, <strong>sans trace</strong> : pour ce qui se calcule à la lecture (les anomalies d'une
     * feuille ouverte, E-RF-11). Une décision qui s'appuie sur un verdict passe par {@link #evaluerDossier}.
     * {@code objetType} et {@code objetId} du dossier sont ignorés.
     */
    public Regles.Verdict jugerSansTrace(Contexte ctx, Regles.Dossier d) {
        PorteesDuDossier portees = PorteesDuDossier.de(unitesEtAncetres(ctx.tenantId(), d.unite(), d.date()), d.portees());
        List<VersionRegle> applicables = Regles.selectionner(depot.versionsDuContexte(ctx.tenantId(), d.contexte()), d.contexte(), portees, d.date());
        return Regles.evaluer(applicables, d.faits() == null ? Map.of() : d.faits());
    }

    /**
     * Les règles qui <b>jugeront</b> un dossier, avant qu'il existe — sans rien évaluer ni tracer.
     *
     * <p>L'employée ne connaissait les règles de son dépôt qu'au refus : elle déposait, était
     * refusée, corrigeait. L'exigence E-GN-16 demande qu'on l'informe des motifs d'inadmissibilité,
     * et la planche s02 porte un panneau « Règles applicables » : les deux veulent savoir avant.</p>
     *
     * <p>La sélection est <b>celle du jugement</b> : annoncer d'autres règles que celles qui
     * décideront serait pire que se taire. {@code faits}, {@code objetType} et {@code objetId} du
     * dossier sont ignorés.</p>
     */
    public List<Regles.RegleApplicable> reglesApplicables(Contexte ctx, Regles.Dossier d) {
        PorteesDuDossier portees = PorteesDuDossier.de(unitesEtAncetres(ctx.tenantId(), d.unite(), d.date()), d.portees());
        return Regles.selectionner(depot.versionsDuContexte(ctx.tenantId(), d.contexte()), d.contexte(), portees, d.date())
                .stream().map(Regles.RegleApplicable::de).toList();
    }

    /** L'unité et tous ses ancêtres en vigueur à la date : une règle d'unité vise aussi les sous-unités. */
    public List<String> unitesEtAncetres(String tenantId, String unite, String date) {
        if (unite == null || unite.isEmpty()) {
            return List.of();
        }
        Map<String, String> parents = new HashMap<>();
        referentiels.unitesEnVigueur(tenantId, date).forEach(u -> parents.put(u.code(), u.parent()));
        List<String> chaine = new ArrayList<>();
        String courant = unite;
        while (courant != null && !chaine.contains(courant)) {
            chaine.add(courant);
            courant = parents.get(courant);
        }
        return List.copyOf(chaine);
    }

    /**
     * Le simulateur : un dossier saisi à la main, pour éprouver une règle avant de s'y fier. La décision est
     * tracée comme les autres — une simulation n'est pas un angle mort du journal.
     */
    public Regles.Verdict simuler(Contexte ctx, Map<String, Object> entree) {
        ContexteRegle contexte = ContexteRegle.de(entree.get("contexte"));
        Object objetId = entree.get("objetId");
        Object brutDate = entree.get("date");
        String date = brutDate == null || "".equals(brutDate) ? ctx.aujourdhui() : Referentiels.dateValide(brutDate, "date");
        return evaluerDossier(ctx, new Regles.Dossier(contexte, SIMULATION, objetId == null ? SIMULATION : String.valueOf(objetId), date,
                entree.get("unite") instanceof String u ? u : null, carte(entree.get("faits")), portees(entree.get("portees"))));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Object brut) {
        return brut instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** Seuls les textes comptent : une portée non textuelle ne peut égaler aucun code de référentiel. */
    private static Map<String, String> portees(Object brut) {
        Map<String, String> p = new HashMap<>();
        if (brut instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (v instanceof String s) {
                    p.put(String.valueOf(k), s);
                }
            });
        }
        return p;
    }
}
