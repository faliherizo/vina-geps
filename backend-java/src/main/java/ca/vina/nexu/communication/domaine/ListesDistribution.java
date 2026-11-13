package ca.vina.nexu.communication.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Les listes de distribution (BF-033) : nommées, partagées ou privées, recalculées à l'envoi. */
public final class ListesDistribution {

    public static final int NOM_MAX = 120;

    private ListesDistribution() {
    }

    /** Ce qu'un membre désigne : une personne, ou un groupe déplié à chaque envoi. */
    public enum Nature {
        PERSONNE("personne"), GROUPE("groupe");

        private final String code;

        Nature(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Optional<Nature> de(String code) {
            return Arrays.stream(values()).filter(n -> n.code.equals(code)).findFirst();
        }

        static String attendues() {
            return Arrays.stream(values()).map(Nature::code).collect(Collectors.joining(", "));
        }
    }

    public record Membre(Nature nature, String cible) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nature", nature.code());
            m.put("cible", cible);
            return m;
        }
    }

    /** Un membre tel que reçu, avant validation : ses champs ne sont pas encore sûrs. */
    public record MembreDemande(String nature, String cible) { }

    /** Une liste ; {@code membres} est vide quand on n'a lu que la ligne. */
    public record Liste(String id, String nom, boolean partagee, String creeePar, String creeeLe, List<Membre> membres) {

        public Liste avecMembres(List<Membre> m) {
            return new Liste(id, nom, partagee, creeePar, creeeLe, List.copyOf(m));
        }

        /** Une liste privée n'est lisible que de son auteur ; une partagée, de tous. */
        public boolean visiblePar(String personId) {
            return partagee || creeePar.equals(personId);
        }

        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("nom", nom);
            m.put("partagee", partagee);
            m.put("creeePar", creeePar);
            m.put("creeeLe", creeeLe);
            m.put("membres", membres.stream().map(Membre::enCarte).toList());
            return m;
        }
    }

    /** Un nom, et des membres dont on sait s'ils désignent une personne ou un groupe ; une liste vide est licite. */
    public static List<String> valider(String nom, List<MembreDemande> membres) {
        List<String> erreurs = new ArrayList<>();
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty()) {
            erreurs.add("nom requis");
        }
        if (n.length() > NOM_MAX) {
            erreurs.add("nom trop long (120 caractères au maximum)");
        }
        for (int i = 0; i < membres.size(); i++) {
            MembreDemande m = membres.get(i);
            if (Nature.de(m.nature()).isEmpty()) {
                erreurs.add("membre " + (i + 1) + " : nature attendue parmi " + Nature.attendues());
            }
            if (m.cible() == null || m.cible().isBlank()) {
                erreurs.add("membre " + (i + 1) + " : cible requise");
            }
        }
        return erreurs;
    }

    /** Les membres validés, cibles rognées. */
    public static List<Membre> membres(List<MembreDemande> demandes) {
        return demandes.stream().map(d -> new Membre(Nature.de(d.nature()).orElseThrow(), d.cible().trim())).toList();
    }

    /** Les destinataires, dépliés maintenant : union dédoublonnée, en ordre stable pour le journal. */
    public static List<String> destinataires(List<Membre> membres, Function<String, List<String>> membresDuGroupe) {
        TreeSet<String> vus = new TreeSet<>();
        for (Membre m : membres) {
            List<String> cibles = m.nature() == Nature.PERSONNE ? List.of(m.cible()) : membresDuGroupe.apply(m.cible());
            cibles.stream().filter(c -> c != null && !c.isEmpty()).forEach(vus::add);
        }
        return List.copyOf(vus);
    }
}
