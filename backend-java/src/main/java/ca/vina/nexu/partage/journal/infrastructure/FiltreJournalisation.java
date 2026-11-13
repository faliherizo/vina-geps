package ca.vina.nexu.partage.journal.infrastructure;

import ca.vina.nexu.partage.journal.Masquage;
import ca.vina.nexu.partage.journal.Niveau;
import ca.vina.nexu.partage.securite.ContexteRequete;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Le filtre du journal (ADR 0041), miroir du middleware Node : un identifiant par requête
 * ({@code X-Requete-Id}), le contexte MDC pour les lignes émises pendant la requête, une ligne
 * {@code http} à la fin — niveau selon la méthode et le statut — et, pour une requête mutante d'un
 * compte authentifié, une ligne du journal des actions. Il ne lit ni le corps ni les en-têtes.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreJournalisation extends OncePerRequestFilter {

    static final String EN_TETE = "X-Requete-Id";
    static final String MDC_REQUETE = "requete";
    static final String MDC_UTILISATEUR = "utilisateur";
    static final String MDC_ORGANISME = "organisme";
    static final String MDC_ACTEUR_REEL = "acteurReel";
    static final String EVENEMENT_HTTP = "http";
    static final String EVENEMENT_JOURNAL = "journal";
    static final String PREFIXE_API = "/api/";
    private static final int OCTETS_IDENTIFIANT = 6;
    private static final Logger JOURNAL = LoggerFactory.getLogger(FiltreJournalisation.class);
    private static final SecureRandom ALEA = new SecureRandom();

    private final DepotJournalActions actions;
    private final Clock horloge;
    private final int retentionJours;

    public FiltreJournalisation(DepotJournalActions actions, Clock horloge, @Value("${JOURNAL_ACTIONS_RETENTION_JOURS:}") String retention) {
        this.actions = actions;
        this.horloge = horloge;
        this.retentionJours = DepotJournalActions.retentionLue(retention);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite) throws ServletException, IOException {
        byte[] octets = new byte[OCTETS_IDENTIFIANT];
        ALEA.nextBytes(octets);
        String identifiant = HexFormat.of().formatHex(octets);
        long debut = System.nanoTime();
        reponse.setHeader(EN_TETE, identifiant);
        MDC.put(MDC_REQUETE, identifiant);
        try {
            suite.doFilter(requete, reponse);
        } finally {
            conclure(requete, reponse, identifiant, (System.nanoTime() - debut) / 1_000_000);
            MDC.clear();
        }
    }

    private void conclure(HttpServletRequest requete, HttpServletResponse reponse, String identifiant, long dureeMs) {
        Object attribut = requete.getAttribute(ContexteRequete.ATTRIBUT);
        ContexteRequete contexte = attribut instanceof ContexteRequete c ? c : null;
        situer(contexte);
        String chemin = Masquage.masquerChemin(requete.getRequestURI(), requete.getQueryString());
        int statut = reponse.getStatus();
        LoggingEventBuilder ligne = selon(Masquage.niveauDeLaRequete(requete.getMethod(), statut))
                .addKeyValue("evenement", EVENEMENT_HTTP).addKeyValue("methode", requete.getMethod()).addKeyValue("chemin", chemin)
                .addKeyValue("statut", statut).addKeyValue("dureeMs", dureeMs);
        String motif = Masquage.libelleDuStatut(statut);
        if (motif != null) {
            ligne = ligne.addKeyValue("motif", motif);
        }
        ligne.log("{} {} → {}", requete.getMethod(), chemin, statut);
        if (contexte != null && Masquage.estMutante(requete.getMethod()) && requete.getRequestURI().startsWith(PREFIXE_API)) {
            String sansRequete = chemin.contains("?") ? chemin.substring(0, chemin.indexOf('?')) : chemin;
            actions.consigner(new DepotJournalActions.Action(null, Instant.now(horloge).toString(), contexte.tenantId(), contexte.userId(), contexte.emprunteur(),
                    requete.getMethod(), sansRequete, statut, dureeMs, identifiant));
            int purgees = actions.purgerSiDu(retentionJours);
            if (purgees > 0) {
                JOURNAL.atInfo().addKeyValue("evenement", EVENEMENT_JOURNAL).addKeyValue("purgees", purgees).addKeyValue("retentionJours", retentionJours)
                        .log("journal des actions : {} ligne(s) de plus de {} jours purgée(s)", purgees, retentionJours);
            }
        }
    }

    /** Le contexte MDC apprend qui agit — le filtre de session est passé — pour la ligne http et celles qui suivraient. */
    private static void situer(ContexteRequete contexte) {
        if (contexte == null) {
            return;
        }
        MDC.put(MDC_UTILISATEUR, contexte.userId());
        if (contexte.tenantId() != null) {
            MDC.put(MDC_ORGANISME, contexte.tenantId());
        }
        if (contexte.emprunteur() != null) {
            MDC.put(MDC_ACTEUR_REEL, contexte.emprunteur());
        }
    }

    private static LoggingEventBuilder selon(Niveau niveau) {
        return switch (niveau) {
            case DEBUG -> JOURNAL.atDebug();
            case INFO -> JOURNAL.atInfo();
            case WARN -> JOURNAL.atWarn();
            case ERROR -> JOURNAL.atError();
        };
    }
}
