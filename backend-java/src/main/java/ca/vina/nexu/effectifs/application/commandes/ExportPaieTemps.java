package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.FeuilleTemps;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Le <strong>lot de paie</strong> (E-RF-12) — jumeau de {@code exporterPaie} : les feuilles approuvées d'une période, codées
 * pour la paie, heures supplémentaires reclassées, avec leur empreinte ; l'export est journalisé. Le lot R10
 * (intégrations, connecteur PFT) l'emprunte tel quel.
 */
public class ExportPaieTemps {

    /** Le lot : la période, les feuilles qu'il couvre, ses lignes et leur empreinte SHA-256. */
    public record LotPaie(String debut, List<String> feuilles, List<TempsEffectifs.LignePaie> lignes, String empreinte) { }

    private final LectureFeuillesTemps lecture;
    private final JournalEffectifsPort journal;

    public ExportPaieTemps(LectureFeuillesTemps lecture, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.journal = journal;
    }

    /** {@code uniteCode} nul ou vide : toutes les unités visibles. */
    public LotPaie exporter(Contexte ctx, Object debutBrut, String uniteCode) {
        String debut = Planification.dateDe(debutBrut, "debut");
        List<FeuilleTemps> feuilles = lecture.lister(ctx, TempsEffectifs.StatutFeuille.APPROUVEE.name(), debut).stream()
                .filter(f -> uniteCode == null || uniteCode.isEmpty() || f.uniteCode().equals(uniteCode)).toList();
        List<TempsEffectifs.LignePaie> lignes = feuilles.stream().flatMap(f -> lignesDe(ctx, f).lignes().stream()).toList();
        LotPaie lot = new LotPaie(debut, feuilles.stream().map(FeuilleTemps::id).toList(), lignes, TempsEffectifs.empreinteDe(lignes));
        journaliser(ctx, lot);
        return lot;
    }

    /**
     * Les lignes de paie d'une feuille, et les codes qui y marquent des heures supplémentaires (la paie les majore) : tous
     * ceux des types de comportement SUPPLEMENTAIRE, pas seulement le premier — un organisme peut en avoir plusieurs. Les
     * segments payés suivent, bornés par leurs instants : les primes de quart se jugent à l'heure du jour (ADR 0084).
     */
    public record LignesFeuille(List<TempsEffectifs.LignePaie> lignes, Set<String> codesSupplementaires, List<TempsEffectifs.SegmentPaye> segments) { }

    /** Codées selon les types d'heure en vigueur au début de la feuille, heures supplémentaires reclassées. */
    public LignesFeuille lignesDe(Contexte ctx, FeuilleTemps f) {
        List<LectureFeuillesTemps.TypeHeureTemps> types = lecture.typesHeure(ctx, f.debut());
        TempsEffectifs.CodesPaie codes = new TempsEffectifs.CodesPaie(
                types.stream().collect(Collectors.toMap(LectureFeuillesTemps.TypeHeureTemps::code, LectureFeuillesTemps.TypeHeureTemps::codePaie, (a, b) -> a)),
                LectureFeuillesTemps.typeDuComportement(types, Comportements.TypeHeure.TRAVAILLEE).codePaie(),
                LectureFeuillesTemps.typeDuComportement(types, Comportements.TypeHeure.SUPPLEMENTAIRE).codePaie());
        List<TempsEffectifs.SegmentLu> segments = lecture.segmentsActifs(ctx, f.id(), types);
        Map<String, Long> supplementaires = TempsEffectifs.heuresSupplementaires(segments,
                lecture.parametresPour(ctx.tenantId(), lecture.ressources().exiger(ctx, f.ressourceId()).conventionCode())).parJour();
        Set<String> codesSupplementaires = types.stream().filter(t -> t.comportement() == Comportements.TypeHeure.SUPPLEMENTAIRE)
                .map(LectureFeuillesTemps.TypeHeureTemps::codePaie).collect(Collectors.toSet());
        return new LignesFeuille(TempsEffectifs.lignesDePaie(f.matricule(), segments, codes, supplementaires), codesSupplementaires,
                TempsEffectifs.segmentsPayes(segments, codes));
    }

    private void journaliser(Contexte ctx, LotPaie lot) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("feuilles", lot.feuilles().size());
        apres.put("lignes", lot.lignes().size());
        apres.put("empreinte", lot.empreinte());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("temps.export", "lot-paie", lot.debut(), null, null, apres, GestionnairePreparationTemps.MECANISME));
    }
}
