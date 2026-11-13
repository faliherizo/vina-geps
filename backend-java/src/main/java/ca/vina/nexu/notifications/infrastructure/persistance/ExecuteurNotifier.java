package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Canal;
import ca.vina.nexu.notifications.domaine.CanalJournal;
import ca.vina.nexu.notifications.domaine.TypeDestinataire;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Notifications;
import ca.vina.nexu.ordonnanceur.domaine.Executeur;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * L'exécuteur « notifier » — un envoi différé, bicanal, tracé (S1-05, grille 21 et 25) : un besoin
 * annulé à 23 h avise le suppléant attribué par courriel et texto sans qu'aucun écran ne soit
 * ouvert. Il ne rédige rien — sujet et corps viennent de la charge — et un canal qui échoue fait
 * échouer la tâche entière, donc la retente (ADR 0014).
 */
@Component
public class ExecuteurNotifier implements Executeur {

    public static final String TYPE = "notifier";

    private final JdbcClient jdbc;
    private final DepotTaches taches;
    private final DepotNotifications notifications;
    private final DepotJournalTextos textos;
    private final DepotPreferences preferences;

    public ExecuteurNotifier(JdbcClient jdbc, DepotTaches taches, DepotNotifications notifications, DepotJournalTextos textos, DepotPreferences preferences, RegistreExecuteurs registre) {
        this.jdbc = jdbc;
        this.taches = taches;
        this.notifications = notifications;
        this.textos = textos;
        this.preferences = preferences;
        registre.enregistrer(TYPE, this);
    }

    public TacheDifferee planifier(String tenantId, String cibleId, String echeanceLe, List<String> canaux, String typeDestinataire, List<String> destinataires,
                                   String sujet, String corps, Map<String, Object> variables, String creePar) {
        return planifier(tenantId, cibleId, echeanceLe, canaux, typeDestinataire, destinataires, sujet, corps, variables, creePar, null);
    }

    /** Avec l'événement du catalogue (ADR 0029) : les préférences par événement de la personne s'appliquent à l'exécution. */
    public TacheDifferee planifier(String tenantId, String cibleId, String echeanceLe, List<String> canaux, String typeDestinataire, List<String> destinataires,
                                   String sujet, String corps, Map<String, Object> variables, String creePar, String evenement) {
        Map<String, Object> charge = new LinkedHashMap<>();
        if (evenement != null) {
            charge.put("evenement", evenement);
        }
        charge.put("canaux", canaux);
        charge.put("destinataireType", typeDestinataire);
        charge.put("destinataires", destinataires);
        charge.put("sujet", sujet);
        charge.put("corps", corps);
        charge.put("variables", variables == null ? Map.of() : variables);
        return taches.planifier(tenantId, TYPE, cibleId, echeanceLe, charge, creePar == null ? "systeme" : creePar);
    }

    /** Le numéro d'une personne : celui du dossier de suppléant s'il existe, sinon celui de l'employé ; il ne quitte jamais cette méthode. */
    private String numeroDe(String tenantId, String personId) {
        return jdbc.sql("SELECT telephone FROM scol_suppleants WHERE tenantId = ? AND personId = ?").params(tenantId, personId).query(String.class).optional()
                .or(() -> jdbc.sql("SELECT phone FROM employees WHERE id = ? AND COALESCE(tenantId, 'default') = ?").params(personId, tenantId).query(String.class).optional())
                .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<String> executer(TacheDifferee t, String maintenant) {
        Map<String, Object> c = t.charge();
        List<String> canaux = c.get("canaux") instanceof List<?> l ? (List<String>) l : List.of();
        if (canaux.isEmpty()) {
            throw new IllegalArgumentException("notification sans canal");
        }
        List<String> destinataires = c.get("destinataires") instanceof List<?> l ? (List<String>) l : List.of();
        Map<String, Object> variables = c.get("variables") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String typeDestinataire = (String) c.get("destinataireType");
        String evenement = c.get("evenement") instanceof String e ? e : null;
        for (int rang = 0; rang < canaux.size(); rang++) {
            String canal = canaux.get(rang);
            // Le texto passe par le journal des textos : un texto « envoyé » qui n'est jamais parti est la faute la plus silencieuse.
            // Pour un employé, le journal des notifications porte lui-même le texto (ADR 0029) : l'envoyer ici aussi le doublerait.
            if (CanalJournal.de(canal).orElse(null) == CanalJournal.SMS && TypeDestinataire.de(typeDestinataire).orElse(null) != TypeDestinataire.EMPLOYEE) {
                for (String personId : destinataires) {
                    texter(t, personId, Notifications.rendre((String) c.get("corps"), variables), evenement);
                }
            }
            // Une nouvelle bicanal ne sonne qu'une fois sur le téléphone.
            notifications.envoyer(t.tenantId(), new Notifications.Demande(canal, typeDestinataire, destinataires, (String) c.get("sujet"), (String) c.get("corps"), variables, evenement, "/admin/notifications"), rang == 0);
        }
        return Optional.empty();
    }

    private void texter(TacheDifferee t, String personId, String corps, String evenement) {
        if (!preferences.canalOuvert(t.tenantId(), personId, Canal.TEXTO, evenement)) {
            return;
        }
        String numero = numeroDe(t.tenantId(), personId);
        if (numero == null) {
            throw new IllegalStateException("aucun numéro de téléphone pour " + personId);
        }
        JournalTextos.Texto texto = textos.envoyer(new JournalTextos.Demande(t.tenantId(), numero, corps, null, t.cibleId()));
        if (texto.echoue()) {
            throw new IllegalStateException("texto non livré à " + personId + " (texto " + texto.id() + " : " + texto.erreur() + ")");
        }
    }
}
