package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcriturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * <strong>L'import d'horodatages externes</strong> (E-RF-13) — jumeau de {@code importerHorodatages}
 * ({@code presence/service.ts}). Les horodatages d'une borne ou d'un système tiers corroborent les pointages, sans les
 * remplacer ; une clé par source rend l'import rejouable sans doublon, et chaque ligne fautive est nommée.
 */
public class GestionnaireHorodatagesExternes {

    public static final int IMPORT_MAX = 5_000;
    private static final int SOURCE_MAX = 60;

    /** Le bilan d'un import : lignes nouvelles, doublons ignorés, rejets motivés (numérotés à partir de 1). */
    public record Bilan(int importes, int doublons, List<Rejet> rejets) { }

    public record Rejet(int ligne, String motif) { }

    /** Une ligne valide, prête à inscrire. */
    private record Ligne(String cle, Ressource ressource, PresenceEffectifs.PointageCourt pointage) { }

    /** Les lectures que l'import emprunte. */
    public record Lectures(LecturePointagesEffectifsPort pointages, LectureRessources ressources, LecturePerimetres perimetres) { }

    private final Lectures lire;
    private final EcriturePointagesEffectifsPort depot;
    private final JournalEffectifsPort journal;
    private final Sequence sequence;

    public GestionnaireHorodatagesExternes(Lectures lire, EcriturePointagesEffectifsPort depot, JournalEffectifsPort journal, Sequence sequence) {
        this.lire = lire;
        this.depot = depot;
        this.journal = journal;
        this.sequence = sequence;
    }

    public Bilan importer(Contexte ctx, Map<String, Object> entree) {
        String source = entree.get("source") instanceof String s ? s.strip() : "";
        source = source.substring(0, Math.min(source.length(), SOURCE_MAX));
        if (source.isEmpty()) {
            throw new EffectifsInvalideException("« source » : le nom du système qui a produit ces horodatages");
        }
        if (!(entree.get("lignes") instanceof List<?> lignes) || lignes.isEmpty() || lignes.size() > IMPORT_MAX) {
            throw new EffectifsInvalideException("« lignes » : de 1 à " + IMPORT_MAX + " horodatages");
        }
        Set<String> gerees = ctx.estAdministrateur() ? null : lire.perimetres().unitesVisibles(ctx, Perimetres.Niveau.GESTION);
        int importes = 0;
        List<Rejet> rejets = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            try {
                Ligne l = ligne(ctx, lignes.get(i), gerees);
                importes += depot.importer(ctx, new EcriturePointagesEffectifsPort.HorodatageExterne(sequence.suivant("ehor"), source, l.cle(),
                        l.ressource().id(), l.pointage())) ? 1 : 0;
            } catch (EffectifsInvalideException e) {
                rejets.add(new Rejet(i + 1, e.getMessage()));
            }
        }
        Bilan bilan = new Bilan(importes, lignes.size() - importes - rejets.size(), List.copyOf(rejets));
        consigner(ctx, source, bilan);
        return bilan;
    }

    private void consigner(Contexte ctx, String source, Bilan bilan) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("importes", bilan.importes());
        apres.put("doublons", bilan.doublons());
        apres.put("rejets", bilan.rejets().size());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("presence.import", "horodatages", source, null, null, apres));
    }

    private static EffectifsInvalideException rejet(String motif) {
        return new EffectifsInvalideException(motif);
    }

    /** Une ligne d'import valide ; sinon le motif de son rejet, levé. */
    private Ligne ligne(Contexte ctx, Object brut, Set<String> gerees) {
        Map<?, ?> m = brut instanceof Map<?, ?> carte ? carte : Map.of();
        String cle = m.get("cle") instanceof String s ? s.strip() : "";
        if (cle.isEmpty()) {
            throw rejet("clé absente");
        }
        String matricule = String.valueOf(Optional.ofNullable(m.get("matricule")).orElse(""));
        Ressource r = lire.ressources().exiger(ctx, lire.pointages().ressourceParMatricule(ctx.tenantId(), matricule)
                .orElseThrow(() -> rejet("matricule « " + matricule + " » inconnu")));
        if (gerees != null && !gerees.contains(r.uniteCode())) {
            throw rejet("hors de votre périmètre");
        }
        String horodatage = m.get("horodatage") instanceof String h ? Planification.instantDe(h).map(Iso::de).orElse(null) : null;
        if (horodatage == null) {
            throw rejet("horodatage invalide (ISO 8601)");
        }
        return new Ligne(cle, r, new PresenceEffectifs.PointageCourt(PresenceEffectifs.sensDe(m.get("sens")), horodatage));
    }
}
