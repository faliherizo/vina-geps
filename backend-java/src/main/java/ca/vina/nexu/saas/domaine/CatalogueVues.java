package ca.vina.nexu.saas.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>Catalogue des vues du menu</b> — ce qu'un organisme peut voir, sous les modules qu'il possède.
 *
 * <p>Le catalogue des modules dit ce qu'un établissement <b>a</b> ; celui-ci dit ce qu'il
 * <b>montre</b>. Ce sont deux décisions différentes, et les confondre serait une faute :
 *
 * <p>⚠️ <b>Masquer une vue n'est pas un contrôle d'accès.</b> Les routes d'API d'une page restent
 * ouvertes quand un de ses onglets est masqué. La frontière de sécurité reste le <b>module</b>,
 * gardé au serveur en refus par défaut. Ce catalogue sert à dégarnir un menu — le nommer
 * « accès » laisserait croire à une garantie qui n'existe pas.
 *
 * <p>Miroir de {@code frontend/src/navigation.tsx} et de {@code frontend/src/urgencessante/menu.ts} :
 * {@code CatalogueVuesTest} échoue si le menu et ce catalogue divergent, dans un sens comme dans
 * l'autre — une vue réglable absente du menu, ou une entrée du menu que personne ne peut masquer.
 */
public final class CatalogueVues {

    /** Une section du menu. Son module coupé, elle disparaît de toute façon. */
    public record Section(String cle, String libelle, String module) {

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cle", cle);
            m.put("libelle", libelle);
            m.put("module", module);
            return m;
        }
    }

    /**
     * Une entrée réglable.
     *
     * @param cle {@code page} pour une page entière, {@code page:onglet} pour un onglet — c'est
     *            ce qui est stocké
     */
    public record Vue(String cle, String section, String notion, String page, String onglet, String i18nKey, String module) {

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cle", cle);
            m.put("section", section);
            m.put("notion", notion);
            m.put("page", page);
            m.put("onglet", onglet);
            m.put("i18nKey", i18nKey);
            m.put("module", module);
            return m;
        }
    }

    public static final List<Section> SECTIONS =
            List.of(
                    // Le tableau de bord a sa propre section, avant tout le reste : c'est l'écran d'arrivée.
                    new Section("pilotage", "Tableau de bord", null),
                    // Le socle gouverne la section entière.
                    new Section("urgencessante", "Urgences-santé", "effectifs"),
                    new Section("space", "Mon espace", null),
                    new Section("admin", "Administration", null)
            );

    public static final List<Vue> VUES =
            List.of(
                    new Vue("dashboard", "pilotage", null, "dashboard", null, null, null),
                    new Vue("rapports", "pilotage", null, "rapports", null, null, null),
                    new Vue("monhoraire", "urgencessante", null, "monhoraire", null, "nav.monhoraire", "effectifs_planification"),
                    new Vue("mesdisponibilites", "urgencessante", "usdisponibilitesnotion", "mesdisponibilites", null, "nav.mesdisponibilites", "effectifs_disponibilites"),
                    new Vue("usdisponibilites", "urgencessante", "usdisponibilitesnotion", "usdisponibilites", null, "nav.usdisponibilites", "effectifs_disponibilites"),
                    new Vue("mesabsences", "urgencessante", "usabsencesnotion", "mesabsences", null, "nav.mesabsences", "effectifs_absences"),
                    new Vue("usabsences", "urgencessante", "usabsencesnotion", "usabsences", null, "nav.usabsences", "effectifs_absences"),
                    new Vue("monagph", "urgencessante", "usagphnotion", "monagph", null, "nav.monagph", "effectifs_agph"),
                    new Vue("usagph", "urgencessante", "usagphnotion", "usagph", null, "nav.usagph", "effectifs_agph"),
                    new Vue("uscomblement", "urgencessante", "usquarts", "uscomblement", null, "nav.uscomblement", "effectifs_planification"),
                    new Vue("usequipages", "urgencessante", "usquarts", "usequipages", null, "nav.usequipages", "effectifs_planification"),
                    new Vue("uspostes", "urgencessante", "usquarts", "uspostes", null, "nav.uspostes", "effectifs_planification"),
                    new Vue("usperiodes", "urgencessante", "usquarts", "usperiodes", null, "nav.usperiodes", "effectifs_planification"),
                    new Vue("usressources", "urgencessante", "usquarts", "usressources", null, "nav.usressources", "effectifs_planification"),
                    new Vue("usquotas", "urgencessante", "usquarts", "usquotas", null, "nav.usquotas", "effectifs_planification"),
                    new Vue("monpointage", "urgencessante", "uspointagenotion", "monpointage", null, "nav.monpointage", "effectifs_presence"),
                    new Vue("uspresence", "urgencessante", "uspointagenotion", "uspresence", null, "nav.uspresence", "effectifs_presence"),
                    new Vue("matemps", "urgencessante", "ustempsnotion", "matemps", null, "nav.matemps", "effectifs_temps"),
                    new Vue("ustemps", "urgencessante", "ustempsnotion", "ustemps", null, "nav.ustemps", "effectifs_temps"),
                    new Vue("mesechanges", "urgencessante", "usechangesnotion", "mesechanges", null, "nav.mesechanges", "effectifs_echanges"),
                    new Vue("usechanges", "urgencessante", "usechangesnotion", "usechanges", null, "nav.usechanges", "effectifs_echanges"),
                    new Vue("monequipe", "urgencessante", null, "monequipe", null, "nav.monequipe", "effectifs"),
                    new Vue("alnouvelle", "urgencessante", "alertesnotion", "alnouvelle", null, "nav.alnouvelle", "alertes"),
                    new Vue("alsuivi", "urgencessante", "alertesnotion", "alsuivi", null, "nav.alsuivi", "alertes"),
                    new Vue("mesalertes", "urgencessante", "alertesnotion", "mesalertes", null, "nav.mesalertes", "alertes"),
                    new Vue("paietemps", "urgencessante", "paienotion", "paietemps", null, "nav.paietemps", "paie"),
                    new Vue("paiecycles", "urgencessante", "paienotion", "paiecycles", null, "nav.paiecycles", "paie"),
                    new Vue("paiedossiers", "urgencessante", "paienotion", "paiedossiers", null, "nav.paiedossiers", "paie"),
                    new Vue("paiedeclarations", "urgencessante", "paienotion", "paiedeclarations", null, "nav.paiedeclarations", "paie"),
                    new Vue("mesbulletins", "urgencessante", "paienotion", "mesbulletins", null, "nav.mesbulletins", "paie"),
                    new Vue("usmessages", "urgencessante", "uscommunication", "usmessages", null, "nav.usmessages", "effectifs_communication"),
                    new Vue("usappels", "urgencessante", "uscommunication", "usappels", null, "nav.usappels", "effectifs_communication"),
                    new Vue("usdiffusion", "urgencessante", "uscommunication", "usdiffusion", null, "nav.usdiffusion", "effectifs_communication"),
                    new Vue("paieparametres", "admin", null, "paieparametres", null, "nav.paieparametres", "paie"),
                    new Vue("paieregles", "admin", null, "paieregles", null, "nav.paieregles", "paie"),
                    new Vue("alparametres", "admin", null, "alparametres", null, "nav.alparametres", "alertes"),
                    new Vue("usreferentiels", "urgencessante", "usadministration", "usreferentiels", null, "nav.usreferentiels", "effectifs"),
                    new Vue("uscodesabsence", "urgencessante", "usadministration", "uscodesabsence", null, "nav.uscodesabsence", "effectifs_absences"),
                    new Vue("usregles", "urgencessante", "usadministration", "usregles", null, "nav.usregles", "effectifs_regles"),
                    new Vue("usmilieux", "urgencessante", "usadministration", "usmilieux", null, "nav.usmilieux", "effectifs"),
                    new Vue("usperimetres", "urgencessante", "usadministration", "usperimetres", null, "nav.usperimetres", "effectifs"),
                    new Vue("usintegrations", "urgencessante", "usadministration", "usintegrations", null, "nav.usintegrations", "effectifs_integrations"),
                    new Vue("usretention", "urgencessante", "usadministration", "usretention", null, "nav.usretention", "effectifs"),
                    new Vue("usjournal", "urgencessante", "usadministration", "usjournal", null, "nav.usjournal", "effectifs"),
                    new Vue("account", "space", null, "account", null, null, null),
                    new Vue("approvals", "space", null, "approvals", null, null, null),
                    new Vue("help", "space", null, "help", null, null, null),
                    new Vue("soutien", "space", null, "soutien", null, null, null),
                    new Vue("admin", "admin", null, "admin", null, null, null),
                    new Vue("access", "admin", "access", "access", null, "vue.access.objets", null),
                    new Vue("access:octrois", "admin", "access", "access", "octrois", "vue.access.octrois", null),
                    new Vue("access:qui", "admin", "access", "access", "qui", "vue.access.qui", null),
                    new Vue("notifications", "admin", null, "notifications", null, null, "notifications"),
                    new Vue("apidocs", "admin", null, "apidocs", null, null, null),
                    new Vue("integrations", "admin", null, "integrations", null, null, null),
                    new Vue("superadmin", "admin", null, "superadmin", null, null, null)
            );

    private static final Set<String> CLES = VUES.stream().map(Vue::cle).collect(Collectors.toUnmodifiableSet());

    private CatalogueVues() {
    }

    public static boolean connait(String cle) {
        return CLES.contains(cle);
    }

    /**
     * Les clés qui n'existent pas au catalogue.
     *
     * <p>Refusées plutôt qu'ignorées : une clé inconnue enregistrée sans bruit serait un menu
     * qu'on croit masqué et qui reste visible, et la faute se découvrirait chez le client.
     */
    public static List<String> clesInconnues(List<String> cles) {
        return cles.stream().filter(c -> !CLES.contains(c)).distinct().toList();
    }
}
