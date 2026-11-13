package ca.vina.nexu.organisation.domaine;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <strong>La structure organisationnelle datée</strong> — AI-2026-03, exigences 10, 11, 70.
 *
 * <p>Jumeau de {@code backend/src/organisation/domaine.ts} : mêmes refus, mêmes messages.
 *
 * <p>Une unité administrative n'est pas une ligne : c'est une <strong>suite de versions
 * datées</strong>. La différence ne se voit pas le premier jour, et elle devient irréparable le
 * second — rejouer le budget 2024 exige l'organigramme de 2024, et une fusion qui aurait écrasé
 * la ligne rendrait la reddition de l'année passée invérifiable, au sens propre.
 *
 * <p>Une réorganisation ne remplace rien : elle <strong>termine</strong> des versions et en
 * <strong>ouvre</strong> d'autres, avec un lien de filiation.
 */
public final class Organisation {

    private Organisation() {
    }

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int MOTIF_MINIMUM = 10;

    public static class OrganisationInvalideException extends RuntimeException {
        public OrganisationInvalideException(String message) {
            super(message);
        }
    }

    /**
     * Les quatre opérations de réorganisation (exigence 11). Un ensemble fermé : un
     * {@code enum}, jamais une chaîne comparée (NORMES-JAVA § enums).
     */
    public enum Operation {
        CREATION("creation", "Création", "une"),
        SUPPRESSION("suppression", "Suppression", "une"),
        FUSION("fusion", "Fusion", "une"),
        FRACTIONNEMENT("fractionnement", "Fractionnement", "un");

        private final String code;
        private final String libelle;
        private final String article;

        Operation(String code, String libelle, String article) {
            this.code = code;
            this.libelle = libelle;
            this.article = article;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        /**
         * « une fractionnement » se lit mal, et un message d'erreur mal accordé fait douter du
         * reste : l'article appartient au nom de l'opération, pas à la phrase qui l'emploie.
         */
        public String article() {
            return article;
        }

        /** La conversion se fait <strong>une fois, à la frontière</strong> ; l'inconnu est nommé. */
        public static Operation de(String code) {
            return Arrays.stream(values()).filter(o -> o.code.equals(code)).findFirst()
                    .orElseThrow(() -> new OrganisationInvalideException(
                            "opération de réorganisation inconnue : « " + code + " » (attendu : "
                                    + String.join(", ", Arrays.stream(values()).map(Operation::code).toList()) + ")"));
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Operation::code).toList();
        }
    }

    private static String date(String v, String sujet) {
        if (v == null || !DATE.matcher(v).matches()) {
            throw new OrganisationInvalideException(sujet + " s'écrit AAAA-MM-JJ");
        }
        return v;
    }

    private static String texte(Object v, String sujet) {
        String t = v == null ? "" : v.toString().trim();
        if (t.isEmpty()) {
            throw new OrganisationInvalideException("« " + sujet + " » est obligatoire");
        }
        return t;
    }

    /**
     * Une version d'unité, telle qu'elle est écrite et relue.
     *
     * <p>{@code motif} accompagne la version : c'est lui qui expliquera la réorganisation trois ans
     * plus tard. Le Node l'omet de la réponse de création et le rend à la relecture ; ici il est
     * porté dès la création, parce que c'est ce que la base contiendra de toute façon — deux
     * lectures du même objet qui ne disent pas la même chose valent moins qu'une.
     */
    public record VersionUnite(String id, String uniteId, String code, String libelle,
                               String parentVersionId, String valideDu, String valideAu,
                               Operation operation, List<String> issueDe, String motif) { }

    public record VersionDecrite(String id, String uniteId, String code, String libelle,
                                 String parentVersionId, String valideDu, String valideAu,
                                 Operation operation, List<String> issueDe, String motif,
                                 String chemin, int profondeur, boolean enVigueur) { }

    /** Une version est-elle en vigueur à cette date ? C'est la question que pose chaque rapport. */
    public static boolean enVigueurLe(VersionUnite v, String le) {
        String jour = date(le, "la date d'observation");
        boolean ouverte = v.valideAu() == null || v.valideAu().isBlank() || v.valideAu().compareTo(jour) >= 0;
        return v.valideDu().compareTo(jour) <= 0 && ouverte;
    }

    /**
     * <strong>L'organigramme à une date donnée</strong> (exigence 10).
     *
     * <p>Le chemin hiérarchique est résolu <strong>avec les parents de cette date</strong>, et non
     * avec ceux d'aujourd'hui : un rapport de 2024 doit montrer la Direction des finances sous la
     * Direction générale de l'époque, même si elle a changé de rattachement depuis.
     */
    public static List<VersionDecrite> organigrammeLe(List<VersionUnite> versions, String le) {
        String jour = date(le, "la date d'observation");
        List<VersionUnite> actives = versions.stream().filter(v -> enVigueurLe(v, jour)).toList();
        Map<String, VersionUnite> parId = new HashMap<>();
        for (VersionUnite v : actives) {
            parId.put(v.id(), v);
        }

        List<VersionDecrite> decrites = new ArrayList<>();
        for (VersionUnite v : actives) {
            List<String> noms = new ArrayList<>();
            Set<String> vus = new HashSet<>();
            VersionUnite courant = v;
            while (courant != null) {
                if (!vus.add(courant.id())) {
                    throw new OrganisationInvalideException("la hiérarchie boucle sur « " + courant.code()
                            + " » au " + jour + " : une unité ne peut pas se contenir elle-même");
                }
                noms.addFirst(courant.libelle() == null || courant.libelle().isBlank()
                        ? courant.code() : courant.libelle());
                String parent = courant.parentVersionId();
                if (parent == null || parent.isBlank()) {
                    break;
                }
                VersionUnite suivant = parId.get(parent);
                if (suivant == null) {
                    // Le parent existe peut-être, mais **pas à cette date** : c'est une incohérence
                    // de structure, et la taire produirait un organigramme amputé sans le dire.
                    throw new OrganisationInvalideException("« " + courant.code() + " » est rattachée au "
                            + jour + " à un parent qui n'est pas en vigueur à cette date");
                }
                courant = suivant;
            }
            decrites.add(new VersionDecrite(v.id(), v.uniteId(), v.code(), v.libelle(), v.parentVersionId(),
                    v.valideDu(), v.valideAu(), v.operation(), v.issueDe(), v.motif(),
                    String.join(" › ", noms), noms.size() - 1, true));
        }
        decrites.sort(Comparator.comparing(VersionDecrite::chemin));
        return decrites;
    }

    /**
     * <strong>Les versions successives d'une unité</strong>, de la plus ancienne à la plus récente.
     *
     * <p>C'est ce qu'un vérificateur demande : « qu'est devenue la Direction des finances ? ». La
     * réponse n'est pas une ligne, c'est une histoire.
     */
    public static List<VersionUnite> historique(List<VersionUnite> versions, String uniteId) {
        String cherche = texte(uniteId, "uniteId");
        return versions.stream().filter(v -> v.uniteId().equals(cherche))
                .sorted(Comparator.comparing(VersionUnite::valideDu)).toList();
    }

    public record Cible(String uniteId, String code, String libelle, String parentVersionId) { }

    public record Reorganisation(Operation operation, String effetLe, List<String> sources,
                                 List<Cible> cibles, String motif) { }

    public record VersionTerminee(String id, String valideAu) { }

    public record ResultatReorganisation(List<VersionTerminee> terminees, List<VersionUnite> creees,
                                         List<String> motifs) { }

    /** La veille d'une date : une version se termine la veille du jour où sa remplaçante commence. */
    private static String veille(String jour) {
        return LocalDate.parse(jour).minusDays(1).toString();
    }

    /**
     * <strong>Appliquer une réorganisation</strong> (exigence 11).
     *
     * <p>Aucune version n'est modifiée ni effacée : les sources reçoivent une date de fin, les
     * cibles naissent le jour d'effet. Le <strong>jour de bascule est sans trou et sans
     * chevauchement</strong> — la source se termine la veille, la cible commence le jour même —
     * sans quoi un rapport daté du jour de la fusion verrait soit deux unités, soit aucune.
     *
     * <p>Le <strong>motif est obligatoire</strong> : c'est lui qui explique, trois ans plus tard,
     * pourquoi deux directions n'en font plus qu'une.
     */
    public static ResultatReorganisation reorganiser(List<VersionUnite> versions, Reorganisation r,
                                                     java.util.function.IntFunction<String> nouvelId) {
        String effet = date(r.effetLe(), "la date d'effet");
        String motif = texte(r.motif(), "motif");
        if (motif.length() < MOTIF_MINIMUM) {
            throw new OrganisationInvalideException("une réorganisation exige un motif écrit d'au moins dix "
                    + "caractères : c'est lui qui expliquera, trois ans plus tard, pourquoi deux directions "
                    + "n'en font plus qu'une");
        }

        Map<String, VersionUnite> parId = new HashMap<>();
        for (VersionUnite v : versions) {
            parId.put(v.id(), v);
        }
        List<VersionUnite> sources = new ArrayList<>();
        for (String id : r.sources()) {
            VersionUnite v = parId.get(id);
            if (v == null) {
                throw new OrganisationInvalideException("version introuvable : " + id);
            }
            if (v.valideAu() != null && !v.valideAu().isBlank() && v.valideAu().compareTo(effet) < 0) {
                throw new OrganisationInvalideException("« " + v.code() + " » est déjà terminée le "
                        + v.valideAu() + " : elle ne peut pas être réorganisée le " + effet);
            }
            if (v.valideDu().compareTo(effet) > 0) {
                throw new OrganisationInvalideException("« " + v.code() + " » ne commence que le "
                        + v.valideDu() + " : elle ne peut pas être réorganisée le " + effet);
            }
            sources.add(v);
        }

        exigerFormeAttendue(r.operation(), sources.size(), r.cibles().size());

        String finVeille = veille(effet);
        List<String> filiation = sources.stream().map(VersionUnite::id).toList();
        List<VersionUnite> creees = new ArrayList<>();
        for (int i = 0; i < r.cibles().size(); i++) {
            Cible c = r.cibles().get(i);
            String code = texte(c.code(), "code");
            creees.add(new VersionUnite(nouvelId.apply(i), texte(c.uniteId(), "uniteId"), code,
                    c.libelle() == null || c.libelle().isBlank() ? code : c.libelle().trim(),
                    c.parentVersionId(), effet, null, r.operation(), filiation, motif));
        }

        return new ResultatReorganisation(
                sources.stream().map(s -> new VersionTerminee(s.id(), finVeille)).toList(),
                creees,
                List.of(r.operation().libelle() + " au " + effet + " : " + sources.size()
                        + " version(s) terminée(s) le " + finVeille + ", " + creees.size() + " créée(s)", motif));
    }

    /**
     * <strong>Chaque opération a sa forme</strong>, et l'écart révèle une erreur de saisie plutôt
     * qu'une intention.
     *
     * <p>Une « fusion » d'une seule source est un déplacement ; un « fractionnement » vers une
     * seule cible est un renommage. Les accepter sous le mauvais nom rendrait l'historique
     * illisible : on chercherait une fusion là où il n'y en a jamais eu.
     */
    /**
     * <strong>Chaque opération a sa forme</strong>, et l'écart révèle une erreur de saisie plutôt
     * qu'une intention.
     *
     * <p>Une « fusion » d'une seule source est un déplacement ; un « fractionnement » vers une
     * seule cible est un renommage. Les accepter sous le mauvais nom rendrait l'historique
     * illisible : on chercherait une fusion là où il n'y en a jamais eu.
     */
    private record Forme(String sources, String cibles, boolean conforme) { }

    private static void exigerFormeAttendue(Operation operation, int sources, int cibles) {
        // Une expression `switch` exhaustive, **sans `default`** : ajouter une cinquième opération
        // sans décrire sa forme ne compilera pas, au lieu de la laisser passer sans contrôle.
        Forme forme = switch (operation) {
            case CREATION -> new Forme("aucune source", "une cible", sources == 0 && cibles == 1);
            case SUPPRESSION -> new Forme("une source", "aucune cible", sources == 1 && cibles == 0);
            case FUSION -> new Forme("au moins deux sources", "une cible", sources >= 2 && cibles == 1);
            case FRACTIONNEMENT -> new Forme("une source", "au moins deux cibles", sources == 1 && cibles >= 2);
        };
        if (!forme.conforme()) {
            throw new OrganisationInvalideException(operation.article() + " "
                    + operation.libelle().toLowerCase(java.util.Locale.ROOT)
                    + " attend " + forme.sources() + " et " + forme.cibles() + " ; reçu " + sources
                    + " source(s) et " + cibles + " cible(s)");
        }
    }

    public record Ascendant(String id, String code, Operation operation, String effetLe) { }

    public record Filiation(String versionId, String code, List<Ascendant> ascendants) { }

    /**
     * <strong>Remonter la filiation d'une version</strong> : d'où vient cette unité ?
     *
     * <p>C'est la question qu'un écart budgétaire inexpliqué finit toujours par poser. Sans
     * filiation, on compare le budget d'une unité née cette année à celui d'une unité qui n'existe
     * plus, et l'écart paraît énorme alors qu'il n'y en a aucun.
     */
    public static Filiation filiation(List<VersionUnite> versions, String versionId) {
        Map<String, VersionUnite> parId = new HashMap<>();
        for (VersionUnite v : versions) {
            parId.put(v.id(), v);
        }
        VersionUnite depart = parId.get(texte(versionId, "versionId"));
        if (depart == null) {
            throw new OrganisationInvalideException("version introuvable : " + versionId);
        }

        List<Ascendant> ascendants = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        Deque<String> file = new ArrayDeque<>(depart.issueDe());
        while (!file.isEmpty()) {
            String id = file.removeFirst();
            if (!vus.add(id)) {
                continue;
            }
            VersionUnite v = parId.get(id);
            if (v == null) {
                continue;
            }
            ascendants.add(new Ascendant(v.id(), v.code(), v.operation(), v.valideDu()));
            file.addAll(v.issueDe());
        }
        return new Filiation(depart.id(), depart.code(), ascendants);
    }
}
