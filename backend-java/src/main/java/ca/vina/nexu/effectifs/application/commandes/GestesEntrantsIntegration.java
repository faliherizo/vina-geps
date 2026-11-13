package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.domaine.AnciennetesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.ConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.DossierEmploye;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.HorodatageExterne;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Les gestes entrants (jumeaux de {@code tirerHorodatages} et {@code synchroniserAnciennetes}) : on tire la source par son
 * port, puis on écrit. Une source injoignable lève {@code SourceIndisponibleException} <strong>avant</strong> toute écriture.
 */
public class GestesEntrantsIntegration {

    /** Ce que les gestes empruntent : le registre des sources, l'import d'horodatages (R8), l'ancienneté (R3), les périmètres (R1). */
    public record Appuis(ConnecteursIntegrationPort registre, GestionnaireHorodatagesExternes horodatages, AnciennetesIntegrationPort anciennetes,
                         LecturePerimetres perimetres) { }

    public record Tirage(int lus, int importes, int doublons, int rejets) { }

    public record Synchronisation(int lus, int modifiees, int ignorees) { }

    private final Appuis appuis;
    private final LectureIntegrations lecture;
    private final DepotConnecteursIntegrationPort connecteurs;
    private final JournalEffectifsPort journal;

    public GestesEntrantsIntegration(Appuis appuis, LectureIntegrations lecture, DepotConnecteursIntegrationPort connecteurs, JournalEffectifsPort journal) {
        this.appuis = appuis;
        this.lecture = lecture;
        this.connecteurs = connecteurs;
        this.journal = journal;
    }

    /** RAO : tire les horodatages depuis le curseur et les importe comme horodatages externes (corroboration, E-RF-13). */
    public Tirage tirerHorodatages(Contexte ctx) {
        ConnecteurEnregistre c = lecture.exigerActif(ctx.tenantId(), CodeConnecteur.RAO);
        String depuis = c.curseur() == null ? IntegrationsEffectifs.ORIGINE_DES_TEMPS : c.curseur();
        List<HorodatageExterne> lus = appuis.registre().horodatages(ctx.tenantId(), c).lire(depuis);
        Tirage tirage = lus.isEmpty() ? new Tirage(0, 0, 0, 0) : importer(ctx, depuis, lus);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("depuis", depuis);
        apres.put("lus", tirage.lus());
        apres.put("importes", tirage.importes());
        apres.put("doublons", tirage.doublons());
        apres.put("rejets", tirage.rejets());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.horodatages", "connecteur", CodeConnecteur.RAO.name(), null, null, apres));
        return tirage;
    }

    private Tirage importer(Contexte ctx, String depuis, List<HorodatageExterne> lus) {
        List<Map<String, Object>> lignes = lus.stream().map(GestesEntrantsIntegration::ligne).toList();
        GestionnaireHorodatagesExternes.Bilan bilan = appuis.horodatages().importer(ctx, Map.of("source", CodeConnecteur.RAO.name(), "lignes", lignes));
        String dernier = lus.stream().map(HorodatageExterne::horodatage).reduce(depuis, (a, b) -> b.compareTo(a) > 0 ? b : a);
        connecteurs.poserCurseur(ctx.tenantId(), CodeConnecteur.RAO, dernier);
        return new Tirage(lus.size(), bilan.importes(), bilan.doublons(), bilan.rejets().size());
    }

    private static Map<String, Object> ligne(HorodatageExterne h) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("cle", h.cle());
        l.put("matricule", h.matricule());
        l.put("sens", h.sens());
        l.put("horodatage", h.horodatage());
        return l;
    }

    /**
     * BD Corpo : met à jour l'ancienneté des ressources actives d'une unité, telle que le dossier employé la donne ; une
     * valeur absente ou invalide ne touche à rien et se compte.
     */
    public Synchronisation synchroniserAnciennetes(Contexte ctx, String uniteCode) {
        appuis.perimetres().exiger(ctx, uniteCode, Perimetres.Niveau.GESTION);
        ConnecteurEnregistre c = lecture.exigerActif(ctx.tenantId(), CodeConnecteur.BD_CORPO);
        List<AnciennetesIntegrationPort.RessourceAnciennete> ressources = appuis.anciennetes().actives(ctx.tenantId(), uniteCode);
        List<DossierEmploye> dossiers = appuis.registre().dossiers(ctx.tenantId(), c)
                .lire(ressources.stream().map(AnciennetesIntegrationPort.RessourceAnciennete::matricule).toList());
        int modifiees = 0;
        int ignorees = 0;
        for (DossierEmploye d : dossiers) {
            Optional<AnciennetesIntegrationPort.RessourceAnciennete> r = ressources.stream().filter(x -> x.matricule().equals(d.matricule())).findFirst();
            OptionalDouble a = IntegrationsEffectifs.ancienneteValide(d.anciennete());
            if (r.isEmpty() || a.isEmpty()) {
                ignorees++;
            } else if (a.getAsDouble() != r.get().anciennete()) {
                poser(ctx, uniteCode, r.get(), a.getAsDouble());
                modifiees++;
            }
        }
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("lus", dossiers.size());
        apres.put("modifiees", modifiees);
        apres.put("ignorees", ignorees);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.anciennetes", "connecteur", CodeConnecteur.BD_CORPO.name(), uniteCode, null, apres));
        return new Synchronisation(dossiers.size(), modifiees, ignorees);
    }

    private void poser(Contexte ctx, String uniteCode, AnciennetesIntegrationPort.RessourceAnciennete r, double a) {
        appuis.anciennetes().poser(ctx.tenantId(), r.id(), a);
        journal.ecrire(ctx, new JournalEffectifs.Consignation("ressource.anciennete", "ressource", r.id(), uniteCode,
                Map.of("anciennete", IntegrationsEffectifs.nombreAffiche(r.anciennete())), Map.of("anciennete", IntegrationsEffectifs.nombreAffiche(a)),
                IntegrationsEffectifs.MECANISME));
    }
}
