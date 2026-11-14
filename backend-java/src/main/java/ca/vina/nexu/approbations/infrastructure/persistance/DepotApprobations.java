package ca.vina.nexu.approbations.infrastructure.persistance;

import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Règles, demandes, étapes et délégations d'approbation — port de la persistance de {@code approvals/service.ts}. */
@Repository
public class DepotApprobations implements Approbations.Depot {


    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotComptes comptes;
    private final Courrier courrier;
    private final AtomicLong sequence = new AtomicLong();

    public DepotApprobations(JdbcClient jdbc, Clock horloge, DepotComptes comptes, Courrier courrier) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.comptes = comptes;
        this.courrier = courrier;
    }

    private String identifiant(String prefixe) {
        return prefixe + "-" + horloge.millis() + "-" + sequence.incrementAndGet();
    }

    private String maintenant() {
        return Iso.de(horloge.instant());
    }

    // ── Règles ───────────────────────────────────────────────────────────────────────────────

    private static Approbations.Regle regle(ResultSet rs, int i) throws SQLException {
        return new Approbations.Regle(rs.getString("id"), rs.getString("documentType"), rs.getString("field"), rs.getString("operator"), rs.getString("value"),
                rs.getInt("level"), rs.getString("approverRole"), rs.getString("approverId"));
    }


    public Map<String, Object> creerRegle(String tenantId, Approbations.Regle r, Object valeurBrute) {
        String id = identifiant("arule");
        jdbc.sql("INSERT INTO approval_rules (id, tenantId, documentType, field, operator, value, level, approverRole, approverId, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, r.documentType(), r.field(), r.operator(), r.value(), r.level(), r.approverRole(), r.approverId(), maintenant()).update();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenantId", tenantId);
        m.put("documentType", r.documentType());
        m.put("field", r.field());
        m.put("operator", r.operator());
        m.put("value", valeurBrute);
        m.put("level", r.level());
        m.put("approverRole", r.approverRole());
        m.put("approverId", r.approverId());
        return m;
    }

    @Override
    public void retirerRegle(String tenantId, String id) {
        if (jdbc.sql("DELETE FROM approval_rules WHERE tenantId = ? AND id = ?").params(tenantId, id).update() == 0) {
            throw new IntrouvableException("approval rule not found");
        }
    }

    public List<Approbations.Regle> regles(String tenantId, String documentType) {
        return documentType == null || documentType.isEmpty()
                ? jdbc.sql("SELECT * FROM approval_rules WHERE tenantId = ? ORDER BY level").params(tenantId).query(DepotApprobations::regle).list()
                : jdbc.sql("SELECT * FROM approval_rules WHERE tenantId = ? AND documentType = ? ORDER BY level").params(tenantId, documentType).query(DepotApprobations::regle).list();
    }

    // ── Demandes et étapes ───────────────────────────────────────────────────────────────────

    private static Map<String, Object> etape(ResultSet rs, int i) throws SQLException {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("id", rs.getString("id"));
        e.put("level", rs.getInt("level"));
        if (rs.getString("approverRole") != null) {
            e.put("approverRole", rs.getString("approverRole"));
        }
        if (rs.getString("approverId") != null) {
            e.put("approverId", rs.getString("approverId"));
        }
        e.put("status", rs.getString("status"));
        e.put("additional", rs.getInt("additional") == 1);
        e.put("decidedBy", rs.getString("decidedBy"));
        e.put("decidedAt", rs.getString("decidedAt"));
        e.put("onBehalfOf", rs.getString("onBehalfOf"));
        return e;
    }

    public Optional<Map<String, Object>> demande(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM approval_requests WHERE tenantId = ? AND id = ?").params(tenantId, id).query((rs, i) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("id", rs.getString("id"));
            d.put("tenantId", rs.getString("tenantId"));
            d.put("documentType", rs.getString("documentType"));
            d.put("documentId", rs.getString("documentId"));
            d.put("status", rs.getString("status"));
            d.put("createdAt", rs.getString("createdAt"));
            d.put("requestedBy", rs.getString("requestedBy"));
            return d;
        }).optional().map(d -> {
            d.put("steps", jdbc.sql("SELECT * FROM approval_steps WHERE tenantId = ? AND requestId = ? ORDER BY level, additional, id").params(tenantId, id).query(DepotApprobations::etape).list());
            return d;
        });
    }

    private Map<String, Object> exigerDemande(String tenantId, String id) {
        return demande(tenantId, id).orElseThrow(() -> new IntrouvableException("approval request not found"));
    }

    public List<Map<String, Object>> demandes(String tenantId, String statut) {
        List<String> ids = statut == null || statut.isEmpty()
                ? jdbc.sql("SELECT id FROM approval_requests WHERE tenantId = ? ORDER BY createdAt DESC").params(tenantId).query(String.class).list()
                : jdbc.sql("SELECT id FROM approval_requests WHERE tenantId = ? AND status = ? ORDER BY createdAt DESC").params(tenantId, statut).query(String.class).list();
        return ids.stream().map(id -> exigerDemande(tenantId, id)).toList();
    }

    @Transactional
    public Map<String, Object> creerDemande(String tenantId, String documentType, String documentId, Map<String, Object> contexte, String demandeur) {
        return creerDemande(tenantId, new Approbations.NouvelleDemande(documentType, documentId, contexte, demandeur, null));
    }

    @Override
    @Transactional
    public Map<String, Object> creerDemande(String tenantId, Approbations.NouvelleDemande d) {
        if (d.documentType() == null || d.documentType().isEmpty() || d.documentId() == null || d.documentId().isEmpty()) {
            throw new IllegalArgumentException("tenantId, documentType, documentId required");
        }
        List<Approbations.Regle> regles = d.regles() == null ? regles(tenantId, d.documentType()) : d.regles();
        List<Approbations.Etape> etapes = Approbations.selectionner(regles, d.contexte() == null ? Map.of() : d.contexte());
        if (etapes.isEmpty()) {
            throw new IllegalArgumentException("no approval rule matches this document context");
        }
        String id = identifiant("areq");
        jdbc.sql("INSERT INTO approval_requests (id, tenantId, documentType, documentId, status, createdAt, requestedBy) VALUES (?, ?, ?, ?, 'pending', ?, ?)")
                .params(id, tenantId, d.documentType(), d.documentId(), maintenant(), d.demandeur()).update();
        for (Approbations.Etape e : etapes) {
            jdbc.sql("INSERT INTO approval_steps (id, tenantId, requestId, level, approverRole, approverId, status, decidedBy, decidedAt, additional) VALUES (?, ?, ?, ?, ?, ?, 'pending', NULL, NULL, 0)")
                    .params(identifiant("astep"), tenantId, id, e.level(), e.approverRole(), e.approverId()).update();
        }
        return exigerDemande(tenantId, id);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> etapesDe(Map<String, Object> demande) {
        return (List<Map<String, Object>>) demande.get("steps");
    }

    /** Un approbateur additionnel, au niveau suivant, sans toucher aux règles. */
    public Map<String, Object> ajouterApprobateur(String tenantId, String demandeId, String approbateurId) {
        Map<String, Object> d = exigerDemande(tenantId, demandeId);
        if (!"pending".equals(d.get("status"))) {
            throw new IllegalArgumentException("request is not pending");
        }
        int niveauMax = etapesDe(d).stream().mapToInt(e -> (Integer) e.get("level")).max().orElse(0);
        jdbc.sql("INSERT INTO approval_steps (id, tenantId, requestId, level, approverRole, approverId, status, decidedBy, decidedAt, additional) VALUES (?, ?, ?, ?, NULL, ?, 'pending', NULL, NULL, 1)")
                .params(identifiant("astep"), tenantId, demandeId, niveauMax + 1, approbateurId).update();
        return exigerDemande(tenantId, demandeId);
    }

    public Optional<Map<String, Object>> demandeDuDocument(String tenantId, String documentType, String documentId) {
        return jdbc.sql("SELECT id FROM approval_requests WHERE tenantId = ? AND documentType = ? AND documentId = ? ORDER BY createdAt DESC, rowid DESC LIMIT 1")
                .params(tenantId, documentType, documentId).query(String.class).optional().flatMap(id -> demande(tenantId, id));
    }

    /** Relance si le montant a varié au-delà de la tolérance : toutes les étapes reviennent en attente. */
    public Map<String, Object> relancer(String tenantId, String documentType, String documentId, double ancien, double nouveau, Double tolerance) {
        double t = tolerance == null ? Approbations.TOLERANCE_PAR_DEFAUT : tolerance;
        Optional<Map<String, Object>> d = demandeDuDocument(tenantId, documentType, documentId);
        Map<String, Object> r = new LinkedHashMap<>();
        if (d.isEmpty()) {
            r.put("relaunched", false);
            r.put("reason", "no approval request for this document");
            r.put("request", null);
            return r;
        }
        long pourcent = Math.round(t * 100);
        if (!Approbations.doitRelancer(ancien, nouveau, t)) {
            r.put("relaunched", false);
            r.put("reason", "change within " + pourcent + "% tolerance");
            r.put("request", d.get());
            return r;
        }
        String id = (String) d.get().get("id");
        jdbc.sql("UPDATE approval_steps SET status = 'pending', decidedBy = NULL, decidedAt = NULL WHERE tenantId = ? AND requestId = ?").params(tenantId, id).update();
        jdbc.sql("UPDATE approval_requests SET status = 'pending' WHERE tenantId = ? AND id = ?").params(tenantId, id).update();
        r.put("relaunched", true);
        r.put("reason", "change exceeds " + pourcent + "% tolerance");
        r.put("request", exigerDemande(tenantId, id));
        return r;
    }

    // ── Délégations ──────────────────────────────────────────────────────────────────────────

    private static Map<String, Object> delegation(ResultSet rs, int i) throws SQLException {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", rs.getString("id"));
        d.put("tenantId", rs.getString("tenantId"));
        d.put("fromUserId", rs.getString("fromUserId"));
        d.put("toUserId", rs.getString("toUserId"));
        d.put("fromDate", rs.getString("fromDate"));
        d.put("toDate", rs.getString("toDate"));
        d.put("createdAt", rs.getString("createdAt"));
        return d;
    }

    /** Établit une délégation datée et avise le délégataire s'il a un courriel (meilleur effort). */
    public Map<String, Object> deleguer(String tenantId, Approbations.Delegation d) {
        String id = identifiant("adel");
        String createdAt = maintenant();
        jdbc.sql("INSERT INTO approval_delegations (id, tenantId, fromUserId, toUserId, fromDate, toDate, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, d.fromUserId(), d.toUserId(), d.fromDate(), d.toDate(), createdAt).update();
        comptes.parId(d.toUserId()).filter(c -> c.email() != null)
                .ifPresent(c -> courrier.envoyer(new Courrier.Message(c.email(), "Délégation d'approbation reçue", Approbations.texteDelegation(d.fromDate(), d.toDate()))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tenantId", tenantId);
        m.put("fromUserId", d.fromUserId());
        m.put("toUserId", d.toUserId());
        m.put("fromDate", d.fromDate());
        m.put("toDate", d.toDate());
        m.put("createdAt", createdAt);
        return m;
    }

    public List<Map<String, Object>> delegations(String tenantId, String deUserId) {
        return deUserId == null
                ? jdbc.sql("SELECT * FROM approval_delegations WHERE tenantId = ? ORDER BY createdAt DESC").params(tenantId).query(DepotApprobations::delegation).list()
                : jdbc.sql("SELECT * FROM approval_delegations WHERE tenantId = ? AND fromUserId = ? ORDER BY createdAt DESC").params(tenantId, deUserId).query(DepotApprobations::delegation).list();
    }

    /** Le délégataire actif d'un approbateur à la date du jour, la délégation la plus récente d'abord. */
    public String delegataireActif(String tenantId, String userId) {
        return delegationActive(tenantId, userId).map(Approbations.Delegation::toUserId).orElse(null);
    }

    private static Approbations.Delegation enDelegation(ResultSet rs, int i) throws SQLException {
        return new Approbations.Delegation(rs.getString("fromUserId"), rs.getString("toUserId"), rs.getString("fromDate"), rs.getString("toDate"));
    }

    private String aujourdhui() {
        return LocalDate.now(horloge.withZone(ZoneOffset.UTC)).toString();
    }

    @Override
    public Optional<Approbations.Delegation> delegationActive(String tenantId, String deUserId) {
        String jour = aujourdhui();
        return jdbc.sql("SELECT fromUserId, toUserId, fromDate, toDate FROM approval_delegations WHERE tenantId = ? AND fromUserId = ? ORDER BY createdAt DESC, rowid DESC")
                .params(tenantId, deUserId).query(DepotApprobations::enDelegation).list().stream().filter(d -> Approbations.delegationActive(d, jour)).findFirst();
    }

    @Override
    public List<Approbations.Delegation> delegationsRecues(String tenantId, String versUserId) {
        String jour = aujourdhui();
        // Seule la plus récente délégation de chaque auteur fait foi : une plus ancienne vers ce compte peut avoir été remplacée.
        return jdbc.sql("SELECT DISTINCT fromUserId FROM approval_delegations WHERE tenantId = ? AND toUserId = ?").params(tenantId, versUserId)
                .query(String.class).list().stream().map(auteur -> delegationActive(tenantId, auteur))
                .flatMap(Optional::stream).filter(d -> versUserId.equals(d.toUserId())).toList();
    }

    @Override
    public boolean revoquer(String tenantId, String id, String deUserId) {
        return jdbc.sql("DELETE FROM approval_delegations WHERE tenantId = ? AND id = ? AND fromUserId = ?").params(tenantId, id, deUserId).update() > 0;
    }

    /** Décide une étape, applique la séparation des tâches, recalcule l'état de la demande. */
    public Map<String, Object> decider(String tenantId, String etapeId, String decision, String acteurId, String role) {
        return decider(tenantId, etapeId, decision, Approbations.Decideur.de(acteurId, role));
    }

    /** Décide une étape (voie du décideur comprise), applique la séparation des tâches, recalcule l'état de la demande. */
    @Override
    public Map<String, Object> decider(String tenantId, String etapeId, String decision, Approbations.Decideur decideur) {
        Approbations.EtapeADecider etape = etapeADecider(tenantId, etapeId, decideur);
        String demandeId = jdbc.sql("SELECT requestId FROM approval_steps WHERE tenantId = ? AND id = ?").params(tenantId, etapeId).query(String.class).single();
        Map<String, Object> d = exigerDemande(tenantId, demandeId);
        String approbateur = etape.approverId();
        Approbations.Signature signature = Approbations.autoriser(etape, decideur.acteurId(), decideur.role(),
                approbateur == null ? null : delegataireActif(tenantId, approbateur), (String) d.get("requestedBy"),
                etapesDe(d).stream().map(s -> new Approbations.EtapeDecidee((String) s.get("id"), (String) s.get("status"), (String) s.get("decidedBy"))).toList());
        jdbc.sql("UPDATE approval_steps SET status = ?, decidedBy = ?, decidedAt = ?, onBehalfOf = ? WHERE tenantId = ? AND id = ?")
                .params(decision, decideur.acteurId(), maintenant(), signature.onBehalfOf(), tenantId, etapeId).update();
        Map<String, Object> apres = exigerDemande(tenantId, demandeId);
        String statut = Approbations.statutApres(decision, (String) apres.get("status"), etapesDe(apres).stream().map(s -> (String) s.get("status")).toList());
        if (!statut.equals(apres.get("status"))) {
            jdbc.sql("UPDATE approval_requests SET status = ? WHERE tenantId = ? AND id = ?").params(statut, tenantId, demandeId).update();
        }
        return exigerDemande(tenantId, demandeId);
    }

    /** L'étape telle que la décision la juge : sa désignation, son statut, et si le décideur la porte par sa voie propre. */
    private Approbations.EtapeADecider etapeADecider(String tenantId, String etapeId, Approbations.Decideur decideur) {
        return jdbc.sql("SELECT approverRole, approverId, status FROM approval_steps WHERE tenantId = ? AND id = ?").params(tenantId, etapeId)
                .query((rs, i) -> new Approbations.EtapeADecider(etapeId, rs.getString("approverRole"), rs.getString("approverId"), rs.getString("status"),
                        decideur.porte().test(new Approbations.Designation(rs.getString("approverRole"), rs.getString("approverId")))))
                .optional().orElseThrow(() -> new IntrouvableException("approval step not found"));
    }
}
