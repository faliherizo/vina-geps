package ca.vina.nexu.communication.domaine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Messagerie sécurisée (L6, exigences 13.x) — port de {@code horaires/messagerie.ts} : la
 * portée de lecture (une personne ne lit que ce qui a été écrit pendant qu'elle participait), qui
 * peut écrire, qui gère les participants, la validation des conversations, messages et pièces
 * jointes, et la recherche dans la seule portée de l'appelant.
 */
public final class Messagerie {

    private Messagerie() {
    }

    public record Participation(String personId, String role, String depuis, String jusqua) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("personId", personId);
            m.put("role", role);
            m.put("depuis", depuis);
            m.put("jusqua", jusqua);
            return m;
        }
    }

    public record Conversation(String id, String sujet, String type, String groupeId, String creePar, String createdAt, String archiveeLe, List<Participation> participants) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("sujet", sujet);
            m.put("type", type);
            m.put("groupeId", groupeId);
            m.put("creePar", creePar);
            m.put("createdAt", createdAt);
            m.put("archiveeLe", archiveeLe);
            m.put("participants", participants.stream().map(Participation::enCarte).toList());
            return m;
        }
    }

    public record PieceJointe(String id, String nom, String typeMime, long taille) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("nom", nom);
            m.put("typeMime", typeMime);
            m.put("taille", taille);
            return m;
        }
    }

    /**
     * Un message, et <b>si le lecteur courant l'a lu</b>.
     *
     * {@code luParMoi} est faux par défaut : il n'a de sens que dans une lecture qui sait qui lit,
     * et les autres chemins (recherche, comptes) n'ont pas à s'en soucier.
     */
    public record Message(String id, String conversationId, String auteurId, String corps, String envoyeLe, String editeLe,
                          List<PieceJointe> piecesJointes, boolean luParMoi) {
        public Message(String id, String conversationId, String auteurId, String corps, String envoyeLe, String editeLe, List<PieceJointe> piecesJointes) {
            this(id, conversationId, auteurId, corps, envoyeLe, editeLe, piecesJointes, false);
        }

        public Message avecLecture(boolean lu) {
            return new Message(id, conversationId, auteurId, corps, envoyeLe, editeLe, piecesJointes, lu);
        }

        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("conversationId", conversationId);
            m.put("auteurId", auteurId);
            m.put("corps", corps);
            m.put("envoyeLe", envoyeLe);
            m.put("editeLe", editeLe);
            m.put("piecesJointes", piecesJointes.stream().map(PieceJointe::enCarte).toList());
            m.put("luParMoi", luParMoi);
            return m;
        }
    }

    public record Fenetre(String conversationId, String depuis, String jusqua) {
        boolean contient(String conversation, String instant) {
            return conversationId.equals(conversation) && instant.compareTo(depuis) >= 0 && (jusqua == null || instant.compareTo(jusqua) <= 0);
        }
    }

    public record PorteeLecture(String personId, List<Fenetre> fenetres) {
        public boolean couvre(String conversationId, String instant) {
            return fenetres.stream().anyMatch(f -> f.contient(conversationId, instant));
        }

        public boolean connait(String conversationId) {
            return fenetres.stream().anyMatch(f -> f.conversationId().equals(conversationId));
        }
    }

    public static PorteeLecture porteeDeLecture(List<Conversation> conversations, String personId) {
        List<Fenetre> fenetres = new ArrayList<>();
        for (Conversation c : conversations) {
            c.participants().stream().filter(p -> p.personId().equals(personId)).forEach(p -> fenetres.add(new Fenetre(c.id(), p.depuis(), p.jusqua())));
        }
        return new PorteeLecture(personId, fenetres);
    }

    /** Ce qu'une conversation doit à son participant : combien de messages il n'a pas encore lus. */
    public record ConversationNonLue(String conversationId, String sujet, int nonLus,
                                     String dernierMessageLe, String dernierAuteurId, String dernierExtrait) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("conversationId", conversationId);
            m.put("sujet", sujet);
            m.put("nonLus", nonLus);
            m.put("dernierMessageLe", dernierMessageLe);
            m.put("dernierAuteurId", dernierAuteurId);
            m.put("dernierExtrait", dernierExtrait);
            return m;
        }
    }

    /** Longueur de l'aperçu d'une conversation : de quoi reconnaître le fil, jamais de quoi le lire. */
    private static final int LONGUEUR_APERCU = 90;
    /** En deçà, couper au dernier espace rognerait plus qu'il n'aiderait : on coupe net. */
    private static final int COUPURE_MOT_MINIMALE = 40;

    /**
     * Le compte d'une conversation, et son aperçu.
     *
     * Deux règles, et chacune a sa raison : un message **que j'ai écrit** n'est jamais non lu —
     * sinon toute conversation où l'on parle sonnerait pour son propre auteur ; et la portée passe
     * par {@link #dansLaPortee}, comme toute autre lecture, pour que la barre ne sonne jamais pour
     * un message que l'écran refusera ensuite d'ouvrir.
     */
    public static ConversationNonLue compterNonLus(PorteeLecture portee, Conversation conversation,
                                                   List<Message> messages, Set<String> lus) {
        List<Message> visibles = messages.stream().filter(m -> dansLaPortee(portee, m)).toList();
        Message dernier = visibles.isEmpty() ? null : visibles.get(visibles.size() - 1);
        int compte = (int) visibles.stream()
                .filter(m -> !m.auteurId().equals(portee.personId()) && !lus.contains(m.id()))
                .count();
        return new ConversationNonLue(conversation.id(), conversation.sujet(), compte,
                dernier == null ? null : dernier.envoyeLe(),
                dernier == null ? null : dernier.auteurId(),
                dernier == null ? null : apercu(dernier.corps()));
    }

    /** Une première ligne, coupée sur un mot et suivie d'une ellipse — jamais au milieu d'un mot. */
    static String apercu(String corps) {
        String plat = corps.replaceAll("\\s+", " ").trim();
        if (plat.length() <= LONGUEUR_APERCU) {
            return plat;
        }
        String coupe = plat.substring(0, LONGUEUR_APERCU);
        int espace = coupe.lastIndexOf(' ');
        return (espace > COUPURE_MOT_MINIMALE ? coupe.substring(0, espace) : coupe).stripTrailing() + "…";
    }

    public static boolean dansLaPortee(PorteeLecture portee, Message m) {
        return portee.couvre(m.conversationId(), m.envoyeLe());
    }

    private static final int LONGUEUR_EXTRAIT = 120;

    public static String normaliser(String texte) {
        return Normalizer.normalize(texte, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "").toLowerCase(Locale.ROOT);
    }

    static String extraire(String corps, String terme) {
        int position = normaliser(corps).indexOf(terme);
        int debut = Math.max(0, position - LONGUEUR_EXTRAIT / 3);
        String extrait = corps.substring(Math.min(debut, corps.length()), Math.min(corps.length(), debut + LONGUEUR_EXTRAIT));
        return (debut > 0 ? "…" : "") + extrait + (debut + LONGUEUR_EXTRAIT < corps.length() ? "…" : "");
    }

    public static List<Map<String, Object>> rechercher(PorteeLecture portee, List<Message> messages, String requete) {
        String terme = normaliser(requete);
        if (terme.length() < 2) {
            throw new IllegalArgumentException("la recherche demande au moins deux caractères");
        }
        List<Message> trouves = new ArrayList<>(messages.stream().filter(m -> dansLaPortee(portee, m) && normaliser(m.corps()).contains(terme)).toList());
        trouves.sort(Comparator.comparing(Message::envoyeLe).reversed());
        List<Map<String, Object>> resultats = new ArrayList<>();
        for (Message m : trouves) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("messageId", m.id());
            r.put("conversationId", m.conversationId());
            r.put("auteurId", m.auteurId());
            r.put("envoyeLe", m.envoyeLe());
            r.put("extrait", extraire(m.corps(), terme));
            resultats.add(r);
        }
        return resultats;
    }

    private static boolean actif(Participation p, String instant) {
        return p.depuis().compareTo(instant) <= 0 && (p.jusqua() == null || instant.compareTo(p.jusqua()) <= 0);
    }

    public static boolean participantActif(Conversation c, String personId, String instant) {
        return c.participants().stream().anyMatch(p -> p.personId().equals(personId) && actif(p, instant));
    }

    public static boolean peutEcrire(Conversation c, String personId, String instant) {
        return c.archiveeLe() == null && participantActif(c, personId, instant);
    }

    public static boolean peutGererParticipants(Conversation c, String personId, String instant) {
        if (TypeConversation.INDIVIDUELLE.est(c.type()) || c.archiveeLe() != null) {
            return false;
        }
        return c.participants().stream().anyMatch(p -> p.personId().equals(personId) && RoleParticipant.MODERATEUR.est(p.role()) && actif(p, instant));
    }

    public static List<String> validerConversation(String sujet, String type, List<String> participants) {
        List<String> erreurs = new ArrayList<>();
        if (sujet == null || sujet.isBlank()) {
            erreurs.add("sujet requis");
        }
        if (TypeConversation.de(type).isEmpty()) {
            erreurs.add("type doit être individuelle ou groupe");
        }
        if (participants.size() < 2) {
            erreurs.add("une conversation compte au moins deux participants");
        }
        if (new HashSet<>(participants).size() != participants.size()) {
            erreurs.add("un participant est répété");
        }
        if (TypeConversation.INDIVIDUELLE.est(type) && participants.size() != 2) {
            erreurs.add("une conversation individuelle compte exactement deux participants");
        }
        return erreurs;
    }

    public static List<String> validerMessage(String corps) {
        List<String> erreurs = new ArrayList<>();
        String c = corps == null ? "" : corps.trim();
        if (c.isEmpty()) {
            erreurs.add("corps requis");
        }
        if (c.length() > 10_000) {
            erreurs.add("message trop long (10 000 caractères au maximum)");
        }
        return erreurs;
    }

    public static final List<String> TYPES_PIECE_JOINTE = List.of("application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp", "text/plain", "text/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static final long TAILLE_MAX_PIECE = 25L * 1024 * 1024;

    public static List<String> validerPieceJointe(String nom, String typeMime, Object taille) {
        List<String> erreurs = new ArrayList<>();
        if (nom == null || nom.isBlank()) {
            erreurs.add("nom requis");
        }
        if (typeMime == null || !TYPES_PIECE_JOINTE.contains(typeMime)) {
            erreurs.add("typeMime doit être l'un de " + String.join(", ", TYPES_PIECE_JOINTE));
        }
        boolean entier = taille instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue());
        if (!entier || ((Number) taille).longValue() <= 0) {
            erreurs.add("taille doit être un entier positif");
        } else if (((Number) taille).longValue() > TAILLE_MAX_PIECE) {
            erreurs.add("taille supérieure au maximum de " + (TAILLE_MAX_PIECE / (1024 * 1024)) + " Mo");
        }
        if (nom != null && (nom.contains("/") || nom.contains("\\") || nom.contains(".."))) {
            erreurs.add("nom de fichier invalide");
        }
        return erreurs;
    }

    public static String typeServi(String typeMime) {
        return TYPES_PIECE_JOINTE.contains(typeMime) ? typeMime : "application/octet-stream";
    }

    /** Les participants uniques, l'auteur en tête : c'est lui le modérateur de sa conversation. */
    public static List<String> participantsAvec(String acteurId, List<String> demandes) {
        Set<String> vus = new java.util.LinkedHashSet<>();
        vus.add(acteurId);
        vus.addAll(demandes);
        return new ArrayList<>(vus);
    }
}
