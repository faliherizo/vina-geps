package ca.vina.nexu.soutien.infrastructure.persistance;

import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.soutien.domaine.AvisSoutien;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import ca.vina.nexu.soutien.domaine.Soutien;
import ca.vina.nexu.soutien.domaine.Soutien.Acteur;
import ca.vina.nexu.soutien.domaine.Soutien.Contenu;
import ca.vina.nexu.soutien.domaine.Soutien.EtatNotification;
import ca.vina.nexu.soutien.domaine.Soutien.GroupeEtablissement;
import ca.vina.nexu.soutien.domaine.Soutien.Maintenance;
import ca.vina.nexu.soutien.domaine.Soutien.Message;
import ca.vina.nexu.soutien.domaine.Soutien.Piece;
import ca.vina.nexu.soutien.domaine.Soutien.Resume;
import ca.vina.nexu.soutien.domaine.Soutien.Ticket;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Soutien — persistance (migration Node 0067) et règles d'accès (exigences 27 à 31), port de
 * {@code soutien/service.ts}. Les billets appartiennent à leur auteur ; le soutien est le
 * superadmin ; l'horloge est injectée pour que les essais fixent l'instant.
 */
@Repository
public class DepotSoutien implements Soutien.Depot {

    private record Ligne(String id, Integer numero, String tenantId, String auteurId, String auteurNom, String sujet, String description, String niveau, String categorie, String impact,
                         String ecran, String navigateur, String statut, String langue, String ouvertLe, String echeanceLe, String prisEnChargeLe, String resoluLe, String fermeLe, String traitePar,
                         Integer satisfaction, String satisfactionCommentaire, String satisfactionLe) { }

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final AvisSoutien avis;
    private final Map<String, String> env;
    private final Antivirus.AnalyseurFichier antivirus;
    private final EngagementsDeService.Registre engagements;

    /** Sans adaptateur d'avis : rien ne part (essais qui ne regardent pas le courriel). */
    public DepotSoutien(JdbcClient jdbc, Clock horloge) {
        this(jdbc, horloge, courriels -> 0, Map.of());
    }

    @Autowired
    public DepotSoutien(JdbcClient jdbc, Clock horloge, AvisSoutien avis, Antivirus.AnalyseurFichier antivirus, EngagementsDeService.Registre engagements) {
        this(jdbc, horloge, avis, System.getenv(), antivirus, engagements);
    }

    public DepotSoutien(JdbcClient jdbc, Clock horloge, AvisSoutien avis, Map<String, String> env) {
        this(jdbc, horloge, avis, env, new Antivirus.AnalyseurSimule());
    }

    /** Tous les organismes sous la politique standard. */
    public DepotSoutien(JdbcClient jdbc, Clock horloge, AvisSoutien avis, Map<String, String> env, Antivirus.AnalyseurFichier antivirus) {
        this(jdbc, horloge, avis, env, antivirus, tenantId -> EngagementsDeService.Politique.STANDARD);
    }

    public DepotSoutien(JdbcClient jdbc, Clock horloge, AvisSoutien avis, Map<String, String> env, Antivirus.AnalyseurFichier antivirus, EngagementsDeService.Registre engagements) {
        this.engagements = engagements;
        this.avis = avis;
        this.env = env;
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.antivirus = antivirus;
    }

    private String maintenant() {
        return Iso.de(horloge.instant());
    }

    private static Integer entier(ResultSet rs, String colonne) throws SQLException {
        Object v = rs.getObject(colonne);
        return v == null ? null : ((Number) v).intValue();
    }

    private static Ligne versLigne(ResultSet rs, int i) throws SQLException {
        String categorie = rs.getString("categorie");
        String impact = rs.getString("impact");
        return new Ligne(rs.getString("id"), entier(rs, "numero"), rs.getString("tenantId"), rs.getString("auteurId"), rs.getString("auteurNom"), rs.getString("sujet"), rs.getString("description"),
                rs.getString("niveau"), categorie == null ? "incident" : categorie, impact == null ? "moi" : impact, rs.getString("ecran"), rs.getString("navigateur"),
                rs.getString("statut"), rs.getString("langue"), rs.getString("ouvertLe"), rs.getString("echeanceLe"), rs.getString("prisEnChargeLe"),
                rs.getString("resoluLe"), rs.getString("fermeLe"), rs.getString("traitePar"), entier(rs, "satisfaction"), rs.getString("satisfactionCommentaire"), rs.getString("satisfactionLe"));
    }

    private Ticket versTicket(Ligne l, String instant, boolean voitInterne) {
        List<Message> messages = jdbc.sql("SELECT * FROM soutien_messages WHERE ticketId = ? ORDER BY le").params(l.id())
                .query((rs, i) -> new Message(rs.getString("id"), rs.getString("auteurId"), rs.getString("auteurNom"), rs.getString("texte"), rs.getInt("interne") != 0, rs.getString("le"))).list()
                .stream().filter(m -> voitInterne || !m.interne()).toList();
        java.util.Set<String> visibles = messages.stream().map(Message::id).collect(java.util.stream.Collectors.toSet());
        // Les pièces d'une note interne restent invisibles de l'auteur, comme la note.
        List<Piece> pieces = jdbc.sql("SELECT id, messageId, nom, typeMime, taille, ajouteLe, ajouteParNom FROM soutien_pieces WHERE ticketId = ? ORDER BY ajouteLe, rowid").params(l.id())
                .query((rs, i) -> new Piece(rs.getString("id"), rs.getString("messageId"), rs.getString("nom"), rs.getString("typeMime"), rs.getLong("taille"), rs.getString("ajouteLe"), rs.getString("ajouteParNom"))).list()
                .stream().filter(p -> p.messageId() == null || visibles.contains(p.messageId())).toList();
        EngagementsDeService.Suivi suivi = suivre(l.tenantId(), l.niveau(), l.ouvertLe(), l.prisEnChargeLe(), l.resoluLe(), instant);
        String echeance = Iso.de(suivi.echeanceResolution());
        Soutien.SuiviReponse reponse = suivi.reponse().map(d -> new Soutien.SuiviReponse(Iso.de(d.echeance()), d.atteintLe().map(Iso::de).orElse(null), d.etat().code(), d.ecartMinutes()))
                .orElse(null);
        return new Ticket(l.id(), l.numero(), l.tenantId(), l.auteurId(), l.auteurNom(), l.sujet(), l.description(), l.niveau(), l.categorie(), l.impact(), l.ecran(), l.navigateur(),
                l.statut(), l.langue(), l.ouvertLe(), echeance, l.prisEnChargeLe(), l.resoluLe(), l.fermeLe(), l.traitePar(), l.satisfaction(), l.satisfactionCommentaire(), l.satisfactionLe(),
                Soutien.etatSla(echeance, l.resoluLe(), instant), messages, pieces, suivi.politique().code(), reponse);
    }

    private static Optional<Instant> instantOptionnel(String iso) {
        return Optional.ofNullable(iso).map(Instant::parse);
    }

    /**
     * Les délais d'un billet selon la politique de son organisme. Le retour d'appel est la prise en
     * charge : une première réponse du soutien la pose, ou le passage explicite « en cours ».
     */
    private EngagementsDeService.Suivi suivre(String tenantId, String niveau, String ouvertLe, String prisEnChargeLe, String resoluLe, String instant) {
        EngagementsDeService.Engagement e = EngagementsDeService.de(engagements.de(tenantId));
        EngagementsDeService.Priorite p = EngagementsDeService.Priorite.de(niveau).orElse(EngagementsDeService.Priorite.N4);
        EngagementsDeService.Jalons jalons = new EngagementsDeService.Jalons(Instant.parse(ouvertLe), instantOptionnel(prisEnChargeLe), instantOptionnel(resoluLe));
        return EngagementsDeService.suivre(e, p, jalons, Instant.parse(instant));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> piecesDe(Map<String, Object> corps) {
        Object v = corps.get("pieces");
        if (!(v instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().map(x -> x instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of()).toList();
    }

    private static byte[] decoder(Object contenu) {
        try {
            return contenu == null ? new byte[0] : java.util.Base64.getDecoder().decode(String.valueOf(contenu));
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static String vide(Object v) {
        String s = v == null ? "" : String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Enregistre les pièces d'un envoi, après l'antivirus : une pièce vient de l'extérieur (grille
     * 33). Le contrôle précède toute écriture, pour qu'un lot où une pièce est infectée n'en laisse aucune.
     */
    private void enregistrerPieces(Acteur acteur, String ticketId, String messageId, List<Map<String, Object>> pieces, String instant) {
        List<byte[]> contenus = pieces.stream().map(p -> decoder(p.get("contenu"))).toList();
        List<String> erreurs = Soutien.validerPieces(pieces, contenus.stream().map(c -> c.length).toList());
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        for (byte[] c : contenus) {
            Antivirus.exigerSain(antivirus, c);
        }
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> p = pieces.get(i);
            jdbc.sql("INSERT INTO soutien_pieces (id, ticketId, messageId, nom, typeMime, taille, contenu, ajouteLe, ajouteParId, ajouteParNom) VALUES (?,?,?,?,?,?,?,?,?,?)")
                    .params(UUID.randomUUID().toString(), ticketId, messageId, String.valueOf(p.get("nom")).trim(), String.valueOf(p.get("typeMime")), contenus.get(i).length, String.valueOf(p.get("contenu")), instant, acteur.id(), acteur.nom()).update();
        }
    }

    public Ticket ouvrirTicket(Acteur acteur, Map<String, Object> e) {
        List<Map<String, Object>> pieces = piecesDe(e);
        List<String> erreurs = new ArrayList<>(Soutien.validerTicket(e));
        erreurs.addAll(Soutien.validerPieces(pieces, pieces.stream().map(p -> decoder(p.get("contenu")).length).toList()));
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        String instant = maintenant();
        String id = UUID.randomUUID().toString();
        String niveau = String.valueOf(e.get("niveau"));
        int numero = jdbc.sql("SELECT COALESCE(MAX(numero), 0) FROM soutien_tickets").query(Integer.class).single() + 1;
        jdbc.sql("INSERT INTO soutien_tickets (id, numero, tenantId, auteurId, auteurNom, sujet, description, niveau, categorie, impact, ecran, navigateur, statut, langue, ouvertLe, echeanceLe) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'nouveau','fr',?,?)")
                .params(id, numero, acteur.tenantId(), acteur.id(), acteur.nom(), String.valueOf(e.get("sujet")).trim(), String.valueOf(e.get("description")).trim(), niveau,
                        e.get("categorie") == null ? "incident" : String.valueOf(e.get("categorie")), e.get("impact") == null ? "moi" : String.valueOf(e.get("impact")), vide(e.get("ecran")), vide(e.get("navigateur")),
                        instant, Soutien.echeanceDe(niveau, instant)).update();
        try {
            enregistrerPieces(acteur, id, null, pieces, instant);
        } catch (RuntimeException ex) {
            // Un billet sans ses pièces refusées serait une demande à moitié faite : on retire tout.
            jdbc.sql("DELETE FROM soutien_tickets WHERE id = ?").param(id).update();
            throw ex;
        }
        Ticket t = lireTicket(acteur, id).orElseThrow();
        // Le soutien est averti dès l'envoi : le délai court à partir de là, pas de l'ouverture de l'écran.
        aviser(t, true, null);
        return t;
    }

    /** Le contenu d'une pièce, pour qui peut lire le billet. */
    public Optional<Contenu> lirePiece(Acteur acteur, String ticketId, String pieceId) {
        Optional<Ticket> t = lireTicket(acteur, ticketId);
        if (t.isEmpty() || t.get().pieces().stream().noneMatch(p -> p.id().equals(pieceId))) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT nom, typeMime, contenu FROM soutien_pieces WHERE id = ? AND ticketId = ?").params(pieceId, ticketId)
                .query((rs, i) -> new Contenu(rs.getString("nom"), rs.getString("typeMime"), decoder(rs.getString("contenu")))).optional();
    }

    /** La satisfaction de l'auteur, une fois le billet résolu : une note, un mot ; la seconde remplace la première. */
    public Ticket noterSatisfaction(Acteur acteur, String ticketId, Object note, Object commentaire) {
        Ticket t = exigerTicket(acteur, ticketId);
        if (!t.auteurId().equals(acteur.id())) {
            throw new HorsPerimetreException(null, "réservé à l'auteur du billet");
        }
        List<String> erreurs = Soutien.validerSatisfaction(t.statut(), note, commentaire);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        jdbc.sql("UPDATE soutien_tickets SET satisfaction = ?, satisfactionCommentaire = ?, satisfactionLe = ? WHERE id = ?")
                .params(((Number) note).intValue(), vide(commentaire), maintenant(), ticketId).update();
        return exigerTicket(acteur, ticketId);
    }

    /** Billets ouverts, nouveaux (sans réponse du soutien), en retard — le badge du superadmin. */
    public Resume resume() {
        String instant = maintenant();
        java.util.Set<String> repondus = new java.util.HashSet<>(jdbc.sql("SELECT DISTINCT m.ticketId FROM soutien_messages m JOIN soutien_tickets t ON t.id = m.ticketId WHERE m.auteurId <> t.auteurId").query(String.class).list());
        int ouverts = 0;
        int nouveaux = 0;
        int enRetard = 0;
        String dernier = null;
        for (Map<String, Object> l : jdbc.sql("SELECT id, tenantId, niveau, statut, resoluLe, ouvertLe, prisEnChargeLe FROM soutien_tickets WHERE statut <> 'ferme'").query().listOfRows()) {
            ouverts++;
            String resoluLe = (String) l.get("resoluLe");
            if (suivre((String) l.get("tenantId"), (String) l.get("niveau"), (String) l.get("ouvertLe"), (String) l.get("prisEnChargeLe"), resoluLe, instant)
                    .enRetard(Instant.parse(instant), instantOptionnel(resoluLe))) {
                enRetard++;
            }
            if ("nouveau".equals(l.get("statut")) && !repondus.contains((String) l.get("id"))) {
                nouveaux++;
            }
            String ouvertLe = (String) l.get("ouvertLe");
            if (dernier == null || ouvertLe.compareTo(dernier) > 0) {
                dernier = ouvertLe;
            }
        }
        return new Resume(ouverts, nouveaux, enRetard, dernier);
    }

    /** Le dispositif d'avis, tel que l'écran du soutien le montre : qui reçoit, par quel transport. */
    public EtatNotification etatNotification(String userId) {
        String courriel = jdbc.sql("SELECT email FROM users WHERE id = ?").param(userId).query(String.class).optional().filter(c -> !c.isBlank()).orElse(null);
        String partagee = env.getOrDefault("SOUTIEN_COURRIEL", "").trim().toLowerCase(java.util.Locale.ROOT);
        return new EtatNotification(destinatairesSoutien(), courriel, partagee.isEmpty() ? null : partagee, avis.transport());
    }

    private static final java.util.regex.Pattern COURRIEL = java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Le courriel du compte superadmin qui reçoit les avis ; vide pour ne plus en recevoir. */
    public EtatNotification reglerCourriel(String userId, Object courrielBrut) {
        String courriel = courrielBrut == null ? "" : String.valueOf(courrielBrut).trim().toLowerCase(java.util.Locale.ROOT);
        if (!courriel.isEmpty() && !COURRIEL.matcher(courriel).matches()) {
            throw new IllegalArgumentException("courriel invalide");
        }
        if (jdbc.sql("UPDATE users SET email = ? WHERE id = ?").params(courriel.isEmpty() ? null : courriel, userId).update() == 0) {
            throw new IntrouvableException("user not found");
        }
        return etatNotification(userId);
    }

    /** L'adresse de l'auteur : son compte, sinon sa fiche d'employé. */
    String courrielAuteur(String auteurId) {
        Optional<Map<String, Object>> compte = jdbc.sql("SELECT email, employeeId FROM users WHERE id = ?").param(auteurId).query().listOfRows().stream().findFirst();
        if (compte.isEmpty()) {
            return null;
        }
        String email = (String) compte.get().get("email");
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        String employeeId = (String) compte.get().get("employeeId");
        if (employeeId == null) {
            return null;
        }
        return jdbc.sql("SELECT email FROM employees WHERE id = ?").param(employeeId).query(String.class).optional().filter(e -> !e.isBlank()).map(String::trim).orElse(null);
    }

    /** Une réponse du soutien ou la résolution sont annoncées à l'auteur, s'il a une adresse. */
    private void aviserAuteur(Ticket t, boolean reponse, String texte) {
        String to = courrielAuteur(t.auteurId());
        if (to == null) {
            return;
        }
        AvisSoutien.Billet billet = new AvisSoutien.Billet(t.id(), t.sujet(), t.description(), t.niveau(), t.auteurNom(), t.echeanceLe());
        avis.aviser(List.of(AvisSoutien.composerPourAuteur(to, billet, t.numero(), reponse, texte)));
    }

    /** Les adresses du soutien : comptes superadmin actifs avec courriel, puis la boîte partagée {@code SOUTIEN_COURRIEL}. */
    public List<String> destinatairesSoutien() {
        java.util.LinkedHashSet<String> adresses = new java.util.LinkedHashSet<>(
                jdbc.sql("SELECT email FROM users WHERE role = 'superadmin' AND email IS NOT NULL AND email <> '' AND active = 1")
                        .query((rs, i) -> rs.getString("email").trim().toLowerCase(java.util.Locale.ROOT)).list());
        String partagee = env.getOrDefault("SOUTIEN_COURRIEL", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (!partagee.isEmpty()) {
            adresses.add(partagee);
        }
        return List.copyOf(adresses);
    }

    /** Le nom de l'établissement d'un billet ; « compte système » sans établissement. */
    public String nomEtablissement(String tenantId) {
        if (tenantId == null) {
            return "compte système";
        }
        return jdbc.sql("SELECT name FROM tenants WHERE id = ?").params(tenantId).query(String.class).optional().orElse(tenantId);
    }

    private void aviser(Ticket t, boolean ouverture, String texte) {
        List<String> destinataires = destinatairesSoutien();
        if (destinataires.isEmpty()) {
            return;
        }
        AvisSoutien.Billet billet = new AvisSoutien.Billet(t.id(), t.sujet(), t.description(), t.niveau(), t.auteurNom(), t.echeanceLe());
        String etablissement = nomEtablissement(t.tenantId());
        avis.aviser(destinataires.stream().map(d -> AvisSoutien.composer(d, billet, etablissement, ouverture, texte)).toList());
    }


    public List<GroupeEtablissement> listerParEtablissement(Acteur acteur) {
        if (!acteur.superadmin()) {
            throw new HorsPerimetreException(null, "réservé au soutien");
        }
        Map<String, List<Ticket>> parTenant = new java.util.LinkedHashMap<>();
        for (Ticket t : listerTickets(acteur, null)) {
            parTenant.computeIfAbsent(t.tenantId() == null ? "" : t.tenantId(), k -> new ArrayList<>()).add(t);
        }
        java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CANADA_FRENCH);
        return parTenant.entrySet().stream().map(e -> {
            String tenantId = e.getKey().isEmpty() ? null : e.getKey();
            List<Ticket> tickets = e.getValue();
            int ouverts = (int) tickets.stream().filter(t -> !"ferme".equals(t.statut())).count();
            int enRetard = (int) tickets.stream().filter(DepotSoutien::enRetard).count();
            return new GroupeEtablissement(tenantId, nomEtablissement(tenantId), tickets.size(), ouverts, enRetard, tickets);
        }).sorted(java.util.Comparator.comparingInt(GroupeEtablissement::enRetard).reversed()
                .thenComparing(java.util.Comparator.comparingInt(GroupeEtablissement::ouverts).reversed())
                .thenComparing(GroupeEtablissement::nom, collator)).toList();
    }

    /** En retard : la résolution a dépassé son échéance, ou le soutien n'a pas rappelé à temps. */
    private static boolean enRetard(Ticket t) {
        return "en_retard".equals(t.sla().etat()) || (t.reponse() != null && EngagementsDeService.EtatDelai.EN_RETARD.code().equals(t.reponse().etat()));
    }

    /** Un billet se lit par son auteur ou par le soutien (superadmin) — jamais par un autre locataire. */
    public Optional<Ticket> lireTicket(Acteur acteur, String ticketId) {
        return jdbc.sql("SELECT * FROM soutien_tickets WHERE id = ?").params(ticketId).query(DepotSoutien::versLigne).optional()
                .filter(l -> acteur.superadmin() || l.auteurId().equals(acteur.id())).map(l -> versTicket(l, maintenant(), acteur.superadmin()));
    }

    private Ticket exigerTicket(Acteur acteur, String ticketId) {
        return lireTicket(acteur, ticketId).orElseThrow(() -> new IntrouvableException("billet introuvable"));
    }

    public List<Ticket> listerTickets(Acteur acteur, String statut) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (!acteur.superadmin()) {
            clauses.add("auteurId = ?");
            params.add(acteur.id());
        }
        if (statut != null && !statut.isEmpty()) {
            clauses.add("statut = ?");
            params.add(statut);
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        String instant = maintenant();
        return jdbc.sql("SELECT * FROM soutien_tickets " + where + " ORDER BY ouvertLe DESC").params(params).query(DepotSoutien::versLigne).list()
                .stream().map(l -> versTicket(l, instant, acteur.superadmin())).toList();
    }

    public Ticket repondre(Acteur acteur, String ticketId, String texte, boolean interne) {
        return repondre(acteur, ticketId, texte, interne, List.of());
    }

    public Ticket repondre(Acteur acteur, String ticketId, String texte, boolean interne, List<Map<String, Object>> pieces) {
        Ticket t = exigerTicket(acteur, ticketId);
        if ("ferme".equals(t.statut())) {
            throw new VerrouException("le billet est fermé : ouvrez-en un nouveau");
        }
        String message = texte == null ? "" : texte.trim();
        if (message.isEmpty() || message.length() > 5000) {
            throw new IllegalArgumentException("message requis (5000 caractères au plus)");
        }
        if (interne && !acteur.superadmin()) {
            throw new HorsPerimetreException(null, "une note interne est réservée au soutien");
        }
        List<String> erreursPieces = Soutien.validerPieces(pieces, pieces.stream().map(p -> decoder(p.get("contenu")).length).toList());
        if (!erreursPieces.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreursPieces));
        }
        String messageId = UUID.randomUUID().toString();
        String instant = maintenant();
        jdbc.sql("INSERT INTO soutien_messages (id, ticketId, auteurId, auteurNom, texte, interne, le) VALUES (?,?,?,?,?,?,?)")
                .params(messageId, ticketId, acteur.id(), acteur.nom(), message, interne ? 1 : 0, instant).update();
        try {
            enregistrerPieces(acteur, ticketId, messageId, pieces, instant);
        } catch (RuntimeException ex) {
            jdbc.sql("DELETE FROM soutien_messages WHERE id = ?").param(messageId).update();
            throw ex;
        }
        // Une réponse du soutien à un billet neuf vaut prise en charge : le délai court, on le dit.
        if (acteur.superadmin() && "nouveau".equals(t.statut()) && !interne) {
            changerStatut(acteur, ticketId, "en_cours");
        }
        // Une réponse de l'auteur est un nouvel envoi de l'établissement : le soutien en est averti aussi.
        if (!acteur.superadmin()) {
            aviser(t, false, message);
        }
        // Une réponse du soutien (pas une note interne) est annoncée à l'auteur : il n'a pas à revenir voir.
        if (acteur.superadmin() && !interne) {
            aviserAuteur(t, true, message);
        }
        return exigerTicket(acteur, ticketId);
    }

    public Ticket changerStatut(Acteur acteur, String ticketId, String vers) {
        Ticket t = exigerTicket(acteur, ticketId);
        // L'auteur peut fermer un billet résolu (il confirme) ; les autres transitions sont au soutien.
        if (!acteur.superadmin() && !("ferme".equals(vers) && "resolu".equals(t.statut()))) {
            throw new HorsPerimetreException(null, "réservé au soutien : seule la fermeture d'un billet résolu est à l'auteur");
        }
        if (!Soutien.transitionPermise(t.statut(), vers)) {
            throw new VerrouException("transition impossible : " + t.statut() + " → " + vers);
        }
        Map<String, String> colonnes = Map.of("en_cours", "prisEnChargeLe", "resolu", "resoluLe", "ferme", "fermeLe");
        List<String> sets = new ArrayList<>(List.of("statut = ?", "traitePar = COALESCE(traitePar, ?)"));
        List<Object> params = new ArrayList<>();
        params.add(vers);
        params.add(acteur.superadmin() ? acteur.id() : null);
        String colonne = colonnes.get(vers);
        if (colonne != null) {
            sets.add(colonne + " = COALESCE(" + colonne + ", ?)");
            params.add(maintenant());
        }
        // Rouvrir un billet résolu remet le compteur : la résolution n'était pas la bonne.
        if ("en_cours".equals(vers) && "resolu".equals(t.statut())) {
            sets.add("resoluLe = NULL");
        }
        params.add(ticketId);
        jdbc.sql("UPDATE soutien_tickets SET " + String.join(", ", sets) + " WHERE id = ?").params(params).update();
        Ticket apres = exigerTicket(acteur, ticketId);
        // Résolu par le soutien : l'auteur l'apprend, et sait qu'il peut confirmer ou rouvrir.
        if (acteur.superadmin() && "resolu".equals(vers)) {
            aviserAuteur(apres, false, null);
        }
        return apres;
    }

    // ── Maintenances planifiées (exigence 29) ───────────────────────────────

    public Maintenance planifierMaintenance(String acteurId, Map<String, Object> m) {
        String instant = maintenant();
        List<String> erreurs = Soutien.validerMaintenance(m, instant);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO soutien_maintenances (id, debut, fin, description, statut, annonceeLe, annonceePar) VALUES (?,?,?,?,'planifiee',?,?)")
                .params(id, Iso.de(instantDe(m.get("debut"))), Iso.de(instantDe(m.get("fin"))), String.valueOf(m.get("description")).trim(), instant, acteurId).update();
        return maintenance(id).orElseThrow();
    }

    /** Le validateur a déjà admis la forme : ici l'instant existe. */
    private static Instant instantDe(Object v) {
        String s = String.valueOf(v).trim();
        return Instant.parse(s.length() == 16 ? s + ":00Z" : s.endsWith("Z") || s.matches(".*[+-]\\d{2}:\\d{2}$") ? s : s + "Z");
    }

    public List<Maintenance> listerMaintenances() {
        return jdbc.sql("SELECT * FROM soutien_maintenances ORDER BY debut DESC")
                .query((rs, i) -> new Maintenance(rs.getString("id"), rs.getString("debut"), rs.getString("fin"), rs.getString("description"), rs.getString("statut"), rs.getString("annonceeLe"), rs.getString("annonceePar"))).list();
    }

    private Optional<Maintenance> maintenance(String id) {
        return listerMaintenances().stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public Maintenance annulerMaintenance(String id) {
        if (jdbc.sql("UPDATE soutien_maintenances SET statut = 'annulee', annuleeLe = ? WHERE id = ? AND statut = 'planifiee'").params(maintenant(), id).update() == 0) {
            throw new IntrouvableException("maintenance introuvable ou déjà annulée");
        }
        return maintenance(id).orElseThrow();
    }

    /** La fenêtre en cours, s'il y en a une — c'est elle qui allume la bannière. */
    public Optional<Maintenance> maintenanceEnCours() {
        String instant = maintenant();
        return listerMaintenances().stream().filter(m -> "planifiee".equals(m.statut()) && m.debut().compareTo(instant) <= 0 && instant.compareTo(m.fin()) < 0).findFirst();
    }

    /** La prochaine fenêtre annoncée : affichée à tous dès son annonce. */
    public Optional<Maintenance> prochaineMaintenance() {
        String instant = maintenant();
        return listerMaintenances().stream().filter(m -> "planifiee".equals(m.statut()) && m.debut().compareTo(instant) > 0).min((a, b) -> a.debut().compareTo(b.debut()));
    }

    // ── Disponibilité (exigence 31) ─────────────────────────────────────────

    /** Un battement par minute ; la même minute battue deux fois ne compte qu'une. */
    public void battre(double latenceMs) {
        String instant = maintenant();
        jdbc.sql("INSERT OR IGNORE INTO soutien_battements (minute, le, latenceMs) VALUES (?,?,?)").params(Soutien.cleMinute(instant), instant, Math.round(latenceMs)).update();
    }

    public Soutien.Disponibilite disponibilite(String mois) {
        String premier = jdbc.sql("SELECT MIN(le) FROM soutien_battements").query(String.class).optional().orElse(null);
        List<String> battements = jdbc.sql("SELECT le FROM soutien_battements WHERE minute >= ? AND minute < ?").params(mois + "-01", mois + "-99").query(String.class).list();
        List<Soutien.Fenetre> fenetres = listerMaintenances().stream().filter(m -> "planifiee".equals(m.statut())).map(m -> new Soutien.Fenetre(m.debut(), m.fin())).toList();
        return Soutien.calculerDisponibilite(mois, battements, fenetres, maintenant(), premier, engagementsEnUsage());
    }

    /** La politique standard, puis chaque politique qu'un organisme a reçue. */
    private List<EngagementsDeService.Engagement> engagementsEnUsage() {
        java.util.EnumSet<EngagementsDeService.Politique> enUsage = java.util.EnumSet.of(EngagementsDeService.Politique.STANDARD);
        jdbc.sql("SELECT DISTINCT politique FROM soutien_engagements").query(String.class).list().forEach(c -> enUsage.add(EngagementsDeService.Politique.de(c)));
        return enUsage.stream().map(EngagementsDeService::de).toList();
    }
}
