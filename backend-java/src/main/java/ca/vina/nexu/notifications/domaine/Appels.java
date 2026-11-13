package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Port d'appel sortant — port de {@code notifications/appel.ts} : la plateforme met un agent en
 * relation avec une personne, et c'est le numéro du client que la personne appelée voit. Le
 * téléphone personnel de l'agent ne quitte jamais la plateforme (ADR 0022).
 */
public final class Appels {

    private Appels() {
    }

    public static final String EN_COURS = "en_cours";
    public static final String TERMINE = "termine";
    public static final String ECHOUE = "echoue";

    public record AppelSortant(String id, String statut, String emisLe) {
    }

    /** Les trois numéros d'un appel : celui que le destinataire voit, celui qu'on sonne d'abord, celui qu'on relie ensuite. */
    public record Trio(String expediteur, String agent, String destinataire) {
    }

    public interface PorteurAppelSortant {
        AppelSortant appeler(String expediteur, String agent, String destinataire);

        String statut(String appelId);
    }

    /** Les trois numéros, normalisés ; chacun nommé s'il est illisible. */
    public static Trio normaliserTrio(String expediteur, String agent, String destinataire) {
        String e = Textos.normaliserNumero(expediteur);
        String a = Textos.normaliserNumero(agent);
        String d = Textos.normaliserNumero(destinataire);
        if (e == null) {
            throw new IllegalArgumentException("numéro du client invalide : « " + expediteur + " »");
        }
        if (a == null) {
            throw new IllegalArgumentException("numéro de l'agent invalide : « " + agent + " »");
        }
        if (d == null) {
            throw new IllegalArgumentException("numéro du destinataire invalide : « " + destinataire + " »");
        }
        return new Trio(e, a, d);
    }

    private static final Set<String> TERMINES = Set.of("completed", "termine", "ended", "hangup");
    private static final Set<String> ECHOUES = Set.of("failed", "busy", "no-answer", "canceled", "cancelled", "echoue", "error", "rejected");

    /** Les statuts des fournisseurs varient ; on les ramène aux trois qui comptent. */
    public static String traduireStatutAppel(Object brut) {
        String s = String.valueOf(brut == null ? "" : brut).toLowerCase(Locale.ROOT);
        if (TERMINES.contains(s)) {
            return TERMINE;
        }
        return ECHOUES.contains(s) ? ECHOUE : EN_COURS;
    }

    private static String echapperXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Le TwiML de la mise en relation : un mot en français, puis la composition au nom du client. */
    public static String twimlMiseEnRelation(String expediteur, String destinataire) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Say language=\"fr-CA\">Mise en relation par Nexu VINA.</Say><Dial callerId=\""
                + echapperXml(expediteur) + "\">" + echapperXml(destinataire) + "</Dial></Response>";
    }

    public static final class AppelSimule implements PorteurAppelSortant {
        public record Trace(String id, String expediteur, String agent, String destinataire, String statut) {
        }

        private final List<String> injoignables;
        private final Supplier<String> horloge;
        private final List<Trace> appels = new ArrayList<>();

        public AppelSimule(List<String> injoignables, Supplier<String> horloge) {
            this.injoignables = List.copyOf(injoignables);
            this.horloge = horloge;
        }

        @Override
        public synchronized AppelSortant appeler(String expediteur, String agent, String destinataire) {
            Trio n = normaliserTrio(expediteur, agent, destinataire);
            String statut = injoignables.contains(n.destinataire()) ? ECHOUE : EN_COURS;
            String id = "sim-appel-" + (appels.size() + 1);
            appels.add(new Trace(id, n.expediteur(), n.agent(), n.destinataire(), statut));
            return new AppelSortant(id, statut, horloge.get());
        }

        @Override
        public synchronized String statut(String appelId) {
            return appels.stream().filter(a -> a.id().equals(appelId)).map(Trace::statut).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("appel introuvable"));
        }

        public synchronized List<Trace> appels() {
            return List.copyOf(appels);
        }
    }
}
