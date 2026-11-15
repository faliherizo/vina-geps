package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Canal;
import ca.vina.nexu.notifications.domaine.CanalJournal;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Livraisons;
import ca.vina.nexu.notifications.domaine.Notifications;
import ca.vina.nexu.notifications.domaine.StatutEnvoi;
import ca.vina.nexu.notifications.domaine.TypeDestinataire;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le journal des notifications ({@code varpay_notifications}) — port de la persistance de
 * {@code varpay/notifications.ts} : une ligne par destinataire. Le courriel part vraiment par le
 * port {@link Courrier}, à l'adresse connue du destinataire, sauf s'il a fermé ce canal
 * ({@code desactive}) ou qu'aucune adresse n'est connue ({@code failed}, erreur nommée) ; le texto
 * est journalisé ici après être passé par le journal des textos. Le téléphone reçoit aussi la
 * notification (devis 2.6), sans que son échec touche le journal.
 */
@Repository
public class DepotNotifications {

    /** Un envoi neuf est la première tentative ; une relance compte la suivante. */
    private static final int PREMIERE_TENTATIVE = 1;

    /** Le contexte d'un texto d'employé au journal des textos, quand aucun événement ne le nomme. */
    private static final String CONTEXTE_TEXTO = "notification";

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotAbonnementsPoussee poussee;
    private final Courrier courrier;
    private final DepotJournalTextos textos;
    private final DepotPreferences preferences;

    public DepotNotifications(JdbcClient jdbc, Clock horloge, DepotAbonnementsPoussee poussee, Courrier courrier, DepotPreferences preferences, DepotJournalTextos textos) {
        this.textos = textos;
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.poussee = poussee;
        this.courrier = courrier;
        this.preferences = preferences;
    }

    /** L'adresse d'un professeur : la sienne, sinon celle de l'employé lié ; d'un employé ou d'un compte sinon. */
    public String adresseDe(String tenantId, String typeDestinataire, String destinataire) {
        return adresseDe(tenantId, TypeDestinataire.de(typeDestinataire).orElse(TypeDestinataire.ESTABLISHMENT), destinataire);
    }

    public String adresseDe(String tenantId, TypeDestinataire type, String destinataire) {
        if (type == TypeDestinataire.ESTABLISHMENT) {
            return null;
        }
        if (type == TypeDestinataire.EMPLOYEE) {
            return jdbc.sql("SELECT email FROM employees WHERE id = ?").params(destinataire).query(String.class).optional().map(DepotNotifications::propre)
                    .or(() -> jdbc.sql("SELECT email FROM users WHERE employeeId = ? AND email IS NOT NULL LIMIT 1").params(destinataire).query(String.class).optional().map(DepotNotifications::propre))
                    .orElse(null);
        }
        Optional<Map<String, Object>> prof = jdbc.sql("SELECT email, linkedEmployeeId FROM varpay_teachers WHERE tenantId = ? AND id = ?").params(tenantId, destinataire).query().listOfRows().stream().findFirst();
        String adresse = prof.map(p -> propre(p.get("email"))).orElse(null);
        if (adresse != null) {
            return adresse;
        }
        String employe = prof.map(p -> (String) p.get("linkedEmployeeId")).orElse(destinataire);
        return jdbc.sql("SELECT email FROM employees WHERE id = ?").params(employe).query(String.class).optional().map(DepotNotifications::propre)
                .or(() -> jdbc.sql("SELECT email FROM users WHERE id = ? OR employeeId = ? ORDER BY CASE WHEN id = ? THEN 0 ELSE 1 END LIMIT 1").params(destinataire, destinataire, destinataire).query(String.class).optional().map(DepotNotifications::propre))
                .orElse(null);
    }

    private static String propre(Object v) {
        return v instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    /** Le sort d'un envoi et, s'il n'est pas parti, la raison nommée. */
    private record Sort(StatutEnvoi statut, String erreur) {
        static final Sort PARTI = new Sort(StatutEnvoi.SENT, null);
    }

    /** Le statut d'un courriel et son erreur : fermé par le destinataire, sans adresse, refusé par le transport, ou parti. */
    private Sort envoyerCourriel(String tenantId, Notifications.Demande d, String destinataire, String sujet, String corps) {
        if (!preferences.canalOuvert(tenantId, destinataire, Canal.COURRIEL, d.evenement())) {
            return new Sort(StatutEnvoi.DESACTIVE, "courriel fermé par le destinataire");
        }
        List<String> adresses = adressesDe(tenantId, d.type(), destinataire);
        if (adresses.isEmpty()) {
            return new Sort(StatutEnvoi.FAILED, "aucune adresse de courriel connue");
        }
        // L'institutionnelle et, si la personne l'a donnée, la personnelle (CSSMB 21) : un refus du transport sur l'une nomme l'adresse.
        for (String adresse : adresses) {
            if (!courrier.envoyer(new Courrier.Message(adresse, sujet, corps))) {
                return new Sort(StatutEnvoi.FAILED, adresse + " : courriel refusé par le transport (" + courrier.transport().mode() + ")");
            }
        }
        return Sort.PARTI;
    }

    /** Les adresses d'un destinataire : l'institutionnelle, puis la personnelle (ADR 0039) — sans doublon, en minuscules. */
    public List<String> adressesDe(String tenantId, TypeDestinataire type, String destinataire) {
        List<String> adresses = new ArrayList<>();
        String institutionnelle = adresseDe(tenantId, type, destinataire);
        if (institutionnelle != null) {
            adresses.add(institutionnelle.toLowerCase(Locale.ROOT));
        }
        if (type != TypeDestinataire.ESTABLISHMENT) {
            preferences.courrielPersonnelDe(tenantId, destinataire).map(a -> a.toLowerCase(Locale.ROOT)).filter(a -> !adresses.contains(a)).ifPresent(adresses::add);
        }
        return adresses;
    }

    /** Le numéro d'un employé : celui de son dossier de suppléant s'il existe, sinon celui du dossier d'employé. */
    private String numeroDEmploye(String tenantId, String employeeId) {
        return jdbc.sql("SELECT telephone FROM scol_suppleants WHERE tenantId = ? AND personId = ?").params(tenantId, employeeId).query(String.class).optional().map(DepotNotifications::propre)
                .or(() -> jdbc.sql("SELECT phone FROM employees WHERE id = ?").params(employeeId).query(String.class).optional().map(DepotNotifications::propre))
                .orElse(null);
    }

    /**
     * Le texto d'un employé part vraiment (ADR 0029) : porteur réel, quota, journal des textos — un « envoyé » qui n'est
     * jamais parti est la faute la plus silencieuse de toutes. Fermé par la personne, sans numéro, ou refusé : nommé.
     */
    private Sort envoyerTexto(String tenantId, Notifications.Demande d, String destinataire, String corps) {
        if (!preferences.canalOuvert(tenantId, destinataire, Canal.TEXTO, d.evenement())) {
            return new Sort(StatutEnvoi.DESACTIVE, "texto fermé par le destinataire");
        }
        String numero = numeroDEmploye(tenantId, destinataire);
        if (numero == null) {
            return new Sort(StatutEnvoi.FAILED, "aucun numéro de téléphone connu");
        }
        JournalTextos.Texto t = textos.envoyer(new JournalTextos.Demande(tenantId, numero, corps, null, d.evenement() == null ? CONTEXTE_TEXTO : d.evenement()));
        return t.echoue() ? new Sort(StatutEnvoi.FAILED, t.erreur() == null ? "texto refusé par le porteur" : t.erreur()) : Sort.PARTI;
    }

    private static Map<String, Object> notification(ResultSet rs, int i) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("tenantId", rs.getString("tenantId"));
        m.put("channel", rs.getString("channel"));
        m.put("recipientType", rs.getString("recipientType"));
        m.put("recipientId", rs.getString("recipientId"));
        m.put("subject", rs.getString("subject"));
        m.put("body", rs.getString("body"));
        m.put("status", rs.getString("status"));
        m.put("createdAt", rs.getString("createdAt"));
        m.put("sentAt", rs.getString("sentAt"));
        m.put("error", rs.getString("error"));
        return m;
    }

    public List<Map<String, Object>> envoyer(String tenantId, Notifications.Demande d) {
        return envoyer(tenantId, d, true);
    }

    /** {@code pousser} : faux quand l'appelant a déjà poussé la même nouvelle par un autre canal — une notification bicanal ne sonne qu'une fois. */
    public List<Map<String, Object>> envoyer(String tenantId, Notifications.Demande d, boolean pousser) {
        List<String> erreurs = Notifications.valider(d);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(erreurs.get(0));
        }
        String sujet = Notifications.rendre(d.sujet().trim(), d.variables());
        String corps = Notifications.rendre(d.corps().trim(), d.variables());
        List<Map<String, Object>> envoyees = new ArrayList<>();
        for (String destinataire : d.destinataires()) {
            envoyees.add(envoyerA(tenantId, d, new Message(destinataire, sujet, corps), pousser, PREMIERE_TENTATIVE));
        }
        return envoyees;
    }

    /**
     * Relance un envoi échoué, à l'identique, en comptant la tentative. La poussée a déjà
     * accompagné le premier envoi : la refaire doublerait la notification sur le téléphone alors
     * que c'est le courriel qui a échoué.
     */
    public Livraisons.Envoi renvoyer(String tenantId, Livraisons.Envoi e) {
        Notifications.Demande d = new Notifications.Demande(e.canal(), e.typeDestinataire(), List.of(e.destinataire()), e.sujet(), e.corps(),
                Map.of(), e.evenement(), "/admin/notifications");
        Map<String, Object> n = envoyerA(tenantId, d, new Message(e.destinataire(), e.sujet(), e.corps()), false, e.tentative() + 1);
        return new Livraisons.Envoi((String) n.get("id"), e.evenement(), e.canal(), e.typeDestinataire(), e.destinataire(), e.sujet(), e.corps(),
                (String) n.get("status"), (String) n.get("error"), e.tentative() + 1, (String) n.get("createdAt"), (String) n.get("sentAt"));
    }

    /** Un message rendu, pour un destinataire. */
    private record Message(String destinataire, String sujet, String corps) {
    }

    private Map<String, Object> envoyerA(String tenantId, Notifications.Demande d, Message m, boolean pousser, int tentative) {
        String maintenant = Iso.de(horloge.instant());
        String id = UUID.randomUUID().toString();
        TypeDestinataire type = d.type();
        CanalJournal canal = d.canalJournal();
        // Un courriel part vraiment pour toute personne ; un texto ne part d'ici que pour un employé (le professeur texte par l'ordonnanceur) ; un établissement n'a que le journal.
        Sort sort = canal == CanalJournal.EMAIL && type.personne() ? envoyerCourriel(tenantId, d, m.destinataire(), m.sujet(), m.corps())
                : canal == CanalJournal.SMS && type == TypeDestinataire.EMPLOYEE ? envoyerTexto(tenantId, d, m.destinataire(), m.corps())
                : Sort.PARTI;
        String statut = sort.statut().code();
        String envoyeLe = sort.statut().parti() ? maintenant : null;
        jdbc.sql("INSERT INTO varpay_notifications (id, tenantId, channel, recipientType, recipientId, subject, body, status, createdAt, sentAt, error, evenement, tentative)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .params(id, tenantId, d.canal(), d.typeDestinataire(), m.destinataire(), m.sujet(), m.corps(), statut, maintenant, envoyeLe, sort.erreur(),
                        d.evenement(), tentative).update();
        if (pousser && (!type.personne() || preferences.canalOuvert(tenantId, m.destinataire(), Canal.POUSSEE, d.evenement()))) {
            poussee.pousserPourNotification(tenantId, new DepotAbonnementsPoussee.Poussee(d.typeDestinataire(), m.destinataire(), m.sujet(), m.corps(), d.url(),
                    ca.vina.nexu.notifications.domaine.Evenements.etiquetteDe(d.evenement())));
        }
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("id", id);
        n.put("tenantId", tenantId);
        n.put("channel", d.canal());
        n.put("recipientType", d.typeDestinataire());
        n.put("recipientId", m.destinataire());
        n.put("subject", m.sujet());
        n.put("body", m.corps());
        n.put("status", statut);
        n.put("createdAt", maintenant);
        n.put("sentAt", envoyeLe);
        n.put("error", sort.erreur());
        return n;
    }

    public List<Map<String, Object>> lister(String tenantId, String typeDestinataire, String destinataireId, String canal) {
        StringBuilder sql = new StringBuilder("SELECT * FROM varpay_notifications WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (typeDestinataire != null && !typeDestinataire.isEmpty()) {
            sql.append(" AND recipientType = ?");
            params.add(typeDestinataire);
        }
        if (destinataireId != null && !destinataireId.isEmpty()) {
            sql.append(" AND recipientId = ?");
            params.add(destinataireId);
        }
        if (canal != null && !canal.isEmpty()) {
            sql.append(" AND channel = ?");
            params.add(canal);
        }
        return jdbc.sql(sql + " ORDER BY createdAt DESC").params(params).query(DepotNotifications::notification).list();
    }
}
