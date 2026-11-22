package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.partage.modules.EmetteurEvenements;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AD-06c (addenda 5 b) : l'organisme est <strong>informé en quasi temps réel</strong> des modifications des quarts. Le
 * journal est la source de tout correctif ; ce décorateur annonce, à chaque écriture qui touche un quart exposé, l'événement
 * {@value #EVENEMENT} aux webhooks abonnés. Aucun geste métier n'a donc à y penser, et un nouveau geste qui consigne une
 * place est annoncé sans autre développement.
 *
 * <p>L'avis est <em>mince</em> : il nomme le quart, sa date et son unité, et renvoie à la lecture des correctifs par curseur,
 * qui fait foi. Un avis perdu ne perd donc rien — la lecture suivante rattrape —, et plusieurs écritures d'un même geste
 * sur un même quart ne font qu'un avis (clé quart + instant). Un quart d'une période en confection n'est pas annoncé :
 * l'horaire n'est pas encore opposable, et l'API ne l'expose pas non plus. La livraison (signature, reprises, suspension)
 * est celle de tout webhook.
 */
public class JournalAvecAvisDesQuarts implements JournalEffectifsPort {

    public static final String EVENEMENT = "effectifs.quart.modifie";
    /** Ce que l'organisme lit pour connaître l'état présent du quart. */
    public static final String LECTURE = "/api/echanges/v1/quarts/modifications";

    private final JournalEffectifsPort journal;
    private final EchangesApiPort quarts;
    private final EmetteurEvenements emetteur;

    public JournalAvecAvisDesQuarts(JournalEffectifsPort journal, EchangesApiPort quarts, EmetteurEvenements emetteur) {
        this.journal = journal;
        this.quarts = quarts;
        this.emetteur = emetteur;
    }

    @Override
    public void ecrire(Contexte ctx, JournalEffectifs.Consignation c) {
        journal.ecrire(ctx, c);
        List<EchangesApi.Cible> cibles = EchangesApi.cibles(c.action(), c.objetType(), c.objetId(), c.apres());
        if (!cibles.isEmpty()) {
            annoncer(ctx, c.action(), cibles);
        }
    }

    @Override
    public List<JournalEffectifs.Entree> lire(String tenantId, JournalEffectifs.Filtre filtre, Set<String> unites, int limite) {
        return journal.lire(tenantId, filtre, unites, limite);
    }

    private void annoncer(Contexte ctx, String action, List<EchangesApi.Cible> cibles) {
        Set<String> places = new HashSet<>();
        Set<String> equipages = new HashSet<>();
        cibles.forEach(cb -> (EchangesApi.OBJET_PLACE.equals(cb.objetType()) ? places : equipages).add(cb.objetId()));
        if (!places.isEmpty()) {
            equipages.addAll(quarts.equipagesDesPlaces(ctx.tenantId(), places).values());
        }
        quarts.quartsExposesParId(ctx.tenantId(), equipages).values().forEach(q ->
                emetteur.emettre(ctx.tenantId(), EVENEMENT, avis(q, action, ctx.maintenant()), q.id() + "@" + ctx.maintenant(), ctx.maintenant()));
    }

    static Map<String, Object> avis(EchangesApi.Quart q, String action, String horodatage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("quartId", q.id());
        m.put("date", q.date());
        m.put("uniteCode", q.uniteCode());
        m.put("action", action);
        m.put("horodatage", horodatage);
        m.put("lire", LECTURE);
        return m;
    }
}
