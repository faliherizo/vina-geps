package ca.vina.nexu.soutien.domaine;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <strong>Soutien</strong> — port de {@code soutien/domaine.ts} : billets, maintenances annoncées,
 * disponibilité. Les délais et l'objectif de disponibilité ne sont plus des constantes : ils
 * viennent de la politique de l'organisme ({@link EngagementsDeService}). Une maintenance ne se
 * planifie pas à moins de 72 h ; la disponibilité se mesure en minutes observées hors fenêtres
 * planifiées (6.01.02 du contrat 26-015-DO : le temps de maintenance planifiée sort du temps prévu).
 * L'horloge est reçue en paramètre : aucune E/S.
 */
public final class Soutien {

    /** Port : billets, pièces, satisfaction, avis, maintenances, disponibilité — ce que le Node tient dans {@code soutien/service.ts}. */
    public interface Depot {
        List<Ticket> listerTickets(Acteur acteur, String statut);

        List<GroupeEtablissement> listerParEtablissement(Acteur acteur);

        Resume resume();

        Ticket ouvrirTicket(Acteur acteur, Map<String, Object> entree);

        java.util.Optional<Ticket> lireTicket(Acteur acteur, String ticketId);

        Ticket repondre(Acteur acteur, String ticketId, String texte, boolean interne, List<Map<String, Object>> pieces);

        java.util.Optional<Contenu> lirePiece(Acteur acteur, String ticketId, String pieceId);

        Ticket noterSatisfaction(Acteur acteur, String ticketId, Object note, Object commentaire);

        EtatNotification etatNotification(String userId);

        EtatNotification reglerCourriel(String userId, Object courrielBrut);

        Ticket changerStatut(Acteur acteur, String ticketId, String vers);

        List<Maintenance> listerMaintenances();

        Maintenance planifierMaintenance(String acteurId, Map<String, Object> m);

        Maintenance annulerMaintenance(String id);

        Disponibilite disponibilite(String mois);
    }

    public record Acteur(String id, String nom, String tenantId, boolean superadmin) { }
    public record Message(String id, String auteurId, String auteurNom, String texte, boolean interne, String le) { }
    /** Une pièce telle que le billet la montre : jamais son contenu, qui se télécharge à part. */
    public record Piece(String id, String messageId, String nom, String typeMime, long taille, String ajouteLe, String ajouteParNom) { }
    public record Contenu(String nom, String typeMime, byte[] contenu) { }
    public record Ticket(String id, Integer numero, String tenantId, String auteurId, String auteurNom, String sujet, String description, String niveau, String categorie, String impact,
                         String ecran, String navigateur, String statut, String langue, String ouvertLe, String echeanceLe, String prisEnChargeLe, String resoluLe, String fermeLe, String traitePar,
                         Integer satisfaction, String satisfactionCommentaire, String satisfactionLe, Sla sla, List<Message> messages, List<Piece> pieces,
                         String politique, SuiviReponse reponse) { }
    /** Le délai de réponse d'un billet, quand la politique en promet un ; {@code atteintLe} = le retour d'appel. */
    public record SuiviReponse(String echeanceLe, String atteintLe, String etat, long ecartMinutes) { }
    /** Une fenêtre annoncée ; {@code nature} est le code de {@link NatureChangement} (critère 99.1 : la nature se communique). */
    public record Maintenance(String id, String debut, String fin, String description, String statut, String annonceeLe, String annonceePar, String nature) { }
    /** Le résumé du soutien : un billet ouvert est « nouveau » tant que le soutien n'y a rien écrit. */
    public record Resume(int ouverts, int nouveaux, int enRetard, String dernierOuvertLe) { }
    public record EtatNotification(List<String> destinataires, String courrielDuCompte, String boitePartagee, ca.vina.nexu.partage.courrier.Courrier.Transport transport) { }
    /** Les billets par établissement, pour le soutien : retards d'abord, puis charge, puis nom. */
    public record GroupeEtablissement(String tenantId, String nom, int total, int ouverts, int enRetard, List<Ticket> tickets) {
    }

    private Soutien() {
    }

    public static final List<String> NIVEAUX = EngagementsDeService.Priorite.codes();
    public static final List<String> STATUTS_TICKET = List.of("nouveau", "en_cours", "resolu", "ferme");
    /** Le seul enchaînement admis : un billet fermé ne se rouvre pas — on en ouvre un autre. */
    private static final Map<String, List<String>> TRANSITIONS = Map.of(
            "nouveau", List.of("en_cours", "resolu"), "en_cours", List.of("resolu"), "resolu", List.of("en_cours", "ferme"), "ferme", List.of());
    public static final int PREAVIS_MAINTENANCE_HEURES = 72;
    public static final String LANGUE_SOUTIEN = "fr";
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}");
    private static final Pattern MOIS = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** Un niveau tel que l'écran le montre ; les délais de réponse sont absents quand la politique n'en promet pas. */
    public record Niveau(String code, String description, int delaiResolutionMinutes, Integer delaiReponseEnHeuresMinutes, Integer delaiReponseHorsHeuresMinutes) { }

    public record Heures(String debut, String fin, String fuseau) { }

    /** La catégorie, dans les mots du demandeur ; l'impact, pour départager l'urgence réelle d'une gêne personnelle. */
    public static final List<String> CATEGORIES_TICKET = List.of("incident", "question", "demande", "acces", "donnees", "autre");
    public static final Map<String, String> LIBELLE_CATEGORIE = new java.util.LinkedHashMap<>();
    public static final List<String> IMPACTS_TICKET = List.of("moi", "equipe", "etablissement");
    public static final Map<String, String> LIBELLE_IMPACT = new java.util.LinkedHashMap<>();
    /** Cinq pièces par envoi, dix mégaoctets chacune ; les types qu'un agent ouvre sans outil particulier. */
    public static final int PIECES_MAX_PAR_ENVOI = 5;
    public static final int PIECE_TAILLE_MAX_OCTETS = 10 * 1024 * 1024;
    public static final List<String> TYPES_PIECE_ADMIS = List.of("image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain", "text/csv", "application/json",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip");

    static {
        LIBELLE_CATEGORIE.put("incident", "Quelque chose ne fonctionne pas (erreur, blocage)");
        LIBELLE_CATEGORIE.put("question", "Une question sur l'utilisation");
        LIBELLE_CATEGORIE.put("demande", "Une demande d'amélioration ou de nouvelle fonction");
        LIBELLE_CATEGORIE.put("acces", "Un accès, un compte ou un mot de passe");
        LIBELLE_CATEGORIE.put("donnees", "Une donnée fausse ou manquante");
        LIBELLE_CATEGORIE.put("autre", "Autre");
        LIBELLE_IMPACT.put("moi", "Moi seulement");
        LIBELLE_IMPACT.put("equipe", "Mon équipe ou mon service");
        LIBELLE_IMPACT.put("etablissement", "Tout l'établissement");
    }

    public record Valeur(String code, String libelle) { }

    public record LimitesPieces(int maxParEnvoi, int tailleMaxOctets, List<String> typesAdmis) { }

    public record Politique(String langue, List<Niveau> niveaux, double objectifDisponibilitePourcent, int preavisMaintenanceHeures,
                            List<Valeur> categories, List<Valeur> impacts, LimitesPieces pieces,
                            String code, String libelle, Heures heuresDeSoutien, Double seuilManquementMajeurPourcent) { }

    public record Sla(String etat, long ecartMinutes) { }

    public record Fenetre(String debut, String fin) { }

    /** Le mois jugé contre une politique en usage : objectif, seuil majeur s'il y en a un, verdict. */
    public record Verdict(String politique, String libelle, double objectifPourcent, Double seuilManquementMajeurPourcent, String manquement) { }

    /**
     * {@code objectifPourcent} et {@code objectifAtteint} restent ceux de la politique standard ;
     * {@code engagements} juge le même mois contre chaque politique en usage.
     */
    public record Disponibilite(String mois, String observeDepuis, String observeJusqua, int minutesObservees, int minutesEnMaintenance,
                                int minutesIndisponibles, Double pourcentage, double objectifPourcent, Boolean objectifAtteint, List<Verdict> engagements) { }

    private static final EngagementsDeService.Engagement STANDARD = EngagementsDeService.de(EngagementsDeService.Politique.STANDARD);

    private static Integer minutes(java.util.Optional<java.time.Duration> d) {
        return d.map(x -> (int) x.toMinutes()).orElse(null);
    }

    public static Politique politique(EngagementsDeService.Engagement e) {
        List<Niveau> niveaux = java.util.Arrays.stream(EngagementsDeService.Priorite.values()).map(p -> {
            EngagementsDeService.Clause c = e.clause(p);
            return new Niveau(p.code(), c.description(), (int) c.resolution().toMinutes(),
                    minutes(c.reponse().map(EngagementsDeService.Reponse::enHeures)), minutes(c.reponse().map(EngagementsDeService.Reponse::horsHeures)));
        }).toList();
        Heures heures = e.heures().map(h -> new Heures(h.debut().toString(), h.fin().toString(), h.fuseau().getId())).orElse(null);
        return new Politique(LANGUE_SOUTIEN, niveaux, e.objectifPourcent(), PREAVIS_MAINTENANCE_HEURES,
                CATEGORIES_TICKET.stream().map(c -> new Valeur(c, LIBELLE_CATEGORIE.get(c))).toList(), IMPACTS_TICKET.stream().map(c -> new Valeur(c, LIBELLE_IMPACT.get(c))).toList(),
                new LimitesPieces(PIECES_MAX_PAR_ENVOI, PIECE_TAILLE_MAX_OCTETS, TYPES_PIECE_ADMIS),
                e.politique().code(), e.politique().libelle(), heures,
                e.seuilManquementMajeurPourcent().isPresent() ? e.seuilManquementMajeurPourcent().getAsDouble() : null);
    }

    /** La description d'un niveau dans la politique standard — celle que l'avis au soutien cite. */
    public static String descriptionNiveau(String code) {
        return EngagementsDeService.Priorite.de(code).map(p -> STANDARD.clause(p).description()).orElse(code);
    }

    static String iso(Instant i) {
        return FORMAT.format(i);
    }

    /** {@code Date.parse} du Node sur un instant ISO : accepte « 2026-09-16T02:00 » comme « …:00.000Z » ; vide si impossible. */
    static Instant instant(String texte) {
        if (texte == null) {
            return null;
        }
        String s = texte.trim();
        if (!ISO.matcher(s).find()) {
            return null;
        }
        String complet = s;
        if (s.length() == 16) {
            complet = s + ":00Z";
        } else if (!s.endsWith("Z") && !s.matches(".*[+-]\\d{2}:\\d{2}$")) {
            complet = s + "Z";
        }
        try {
            return Instant.parse(complet);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** L'échéance de résolution de la politique standard, depuis l'ouverture : celle que la ligne du billet conserve. */
    public static String echeanceDe(String niveau, String ouvertLe) {
        EngagementsDeService.Priorite p = EngagementsDeService.Priorite.de(niveau).orElseThrow(() -> new IllegalArgumentException("niveau inconnu : " + niveau));
        return iso(Instant.parse(ouvertLe).plus(STANDARD.clause(p).resolution()));
    }

    /** L'état du délai, lu à {@code maintenant} : résolu, la comparaison se fait à l'instant de résolution. */
    public static Sla etatSla(String echeanceLe, String resoluLe, String maintenant) {
        String reference = resoluLe != null ? resoluLe : maintenant;
        long ecart = Math.round((Instant.parse(echeanceLe).toEpochMilli() - Instant.parse(reference).toEpochMilli()) / 60_000.0);
        if (resoluLe != null) {
            return new Sla(ecart >= 0 ? "resolu_a_temps" : "resolu_en_retard", ecart);
        }
        return new Sla(ecart >= 0 ? "dans_les_delais" : "en_retard", ecart);
    }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    public static List<String> validerTicket(Map<String, Object> t) {
        List<String> erreurs = new ArrayList<>();
        String sujet = texte(t.get("sujet")).trim();
        if (sujet.length() < 5) {
            erreurs.add("sujet requis (5 caractères au moins)");
        }
        if (sujet.length() > 200) {
            erreurs.add("sujet trop long (200 caractères au plus)");
        }
        if (texte(t.get("description")).trim().length() < 10) {
            erreurs.add("description requise (10 caractères au moins) : dites ce qui se passe et ce qui était attendu");
        }
        if (texte(t.get("description")).length() > 5000) {
            erreurs.add("description trop longue (5000 caractères au plus)");
        }
        if (!NIVEAUX.contains(texte(t.get("niveau")))) {
            erreurs.add("niveau requis : " + String.join(", ", NIVEAUX));
        }
        // Catégorie et impact sont optionnels (défauts « incident », « moi ») mais, donnés, doivent être connus.
        if (t.containsKey("categorie") && !CATEGORIES_TICKET.contains(texte(t.get("categorie")))) {
            erreurs.add("catégorie inconnue : " + String.join(", ", CATEGORIES_TICKET));
        }
        if (t.containsKey("impact") && !IMPACTS_TICKET.contains(texte(t.get("impact")))) {
            erreurs.add("impact inconnu : " + String.join(", ", IMPACTS_TICKET));
        }
        if (texte(t.get("ecran")).length() > 500) {
            erreurs.add("écran trop long (500 caractères au plus)");
        }
        if (texte(t.get("navigateur")).length() > 500) {
            erreurs.add("navigateur trop long (500 caractères au plus)");
        }
        return erreurs;
    }

    /** Les erreurs d'un lot de pièces, toutes ensemble ; {@code tailles} = ce que chaque contenu décodé pèse. */
    public static List<String> validerPieces(List<Map<String, Object>> pieces, List<Integer> tailles) {
        List<String> erreurs = new ArrayList<>();
        if (pieces.size() > PIECES_MAX_PAR_ENVOI) {
            erreurs.add(PIECES_MAX_PAR_ENVOI + " pièces jointes au plus par envoi");
        }
        for (int i = 0; i < pieces.size(); i++) {
            Map<String, Object> p = pieces.get(i);
            String nom = texte(p.get("nom")).trim();
            if (nom.isEmpty() || nom.length() > 200) {
                erreurs.add("pièce " + (i + 1) + " : nom requis (200 caractères au plus)");
            }
            if (texte(p.get("contenu")).isEmpty()) {
                erreurs.add("pièce " + (i + 1) + " : contenu requis");
            }
            String type = texte(p.get("typeMime"));
            if (!TYPES_PIECE_ADMIS.contains(type)) {
                erreurs.add("pièce " + (i + 1) + " : type non admis (" + (type.isEmpty() ? "inconnu" : type) + ")");
            }
            if (i < tailles.size() && tailles.get(i) > PIECE_TAILLE_MAX_OCTETS) {
                erreurs.add("pièce " + (i + 1) + " : trop volumineuse (10 Mo maximum)");
            }
        }
        return erreurs;
    }

    /** La satisfaction se donne une fois le billet résolu ou fermé, de 1 à 5, par l'auteur seulement. */
    public static List<String> validerSatisfaction(String statut, Object note, Object commentaire) {
        List<String> erreurs = new ArrayList<>();
        if (!"resolu".equals(statut) && !"ferme".equals(statut)) {
            erreurs.add("la satisfaction se donne une fois le billet résolu");
        }
        boolean entiere = note instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue()) && n.intValue() >= 1 && n.intValue() <= 5;
        if (!entiere) {
            erreurs.add("note de 1 à 5 requise");
        }
        if (commentaire != null && String.valueOf(commentaire).length() > 1000) {
            erreurs.add("commentaire trop long (1000 caractères au plus)");
        }
        return erreurs;
    }

    public static boolean transitionPermise(String de, String vers) {
        return TRANSITIONS.getOrDefault(de, List.of()).contains(vers);
    }

    /** Une maintenance s'annonce 72 h à l'avance (exigence 29) : la règle est dans le code, pas dans une promesse. */
    public static List<String> validerMaintenance(Map<String, Object> m, String maintenant) {
        List<String> erreurs = new ArrayList<>();
        Instant debut = instant(m.get("debut") == null ? null : String.valueOf(m.get("debut")));
        Instant fin = instant(m.get("fin") == null ? null : String.valueOf(m.get("fin")));
        if (debut == null) {
            erreurs.add("debut : instant ISO attendu");
        }
        if (fin == null) {
            erreurs.add("fin : instant ISO attendu");
        }
        if (texte(m.get("description")).trim().length() < 10) {
            erreurs.add("description requise (10 caractères au moins) : ce qui sera indisponible, et pourquoi");
        }
        if (!erreurs.isEmpty()) {
            return erreurs;
        }
        if (!fin.isAfter(debut)) {
            erreurs.add("la fin doit suivre le début");
        }
        if (fin.toEpochMilli() - debut.toEpochMilli() > 12 * 3_600_000L) {
            erreurs.add("une fenêtre de maintenance ne dépasse pas 12 h");
        }
        preavis(m.get("nature"), debut, Instant.parse(maintenant)).ifPresent(erreurs::add);
        return erreurs;
    }

    /**
     * Le préavis suit la nature du changement (ST-H02) : 72 h pour un entretien, 3 jours ouvrables pour un correctif,
     * 21 pour un rehaussement. Le refus dit la première date qui l'aurait respecté.
     */
    static java.util.Optional<String> preavis(Object natureBrute, Instant debut, Instant maintenant) {
        NatureChangement nature;
        try {
            nature = NatureChangement.de(natureBrute == null ? null : String.valueOf(natureBrute));
        } catch (NatureChangement.NatureInconnueException e) {
            return java.util.Optional.of(e.getMessage());
        }
        Instant admis = nature.premierDebutAdmis(maintenant);
        if (!debut.isBefore(admis)) {
            return java.util.Optional.empty();
        }
        if (nature == NatureChangement.ENTRETIEN) {
            double preavisHeures = (debut.toEpochMilli() - maintenant.toEpochMilli()) / 3_600_000.0;
            return java.util.Optional.of("préavis insuffisant : " + PREAVIS_MAINTENANCE_HEURES + " h au moins avant le début (il en reste "
                    + Math.max(0, (long) Math.floor(preavisHeures)) + ")");
        }
        return java.util.Optional.of("préavis insuffisant pour un " + nature.code() + " : " + nature.joursOuvrables()
                + " jours ouvrables au moins ; premier début admis : " + admis.atZone(JoursOuvrablesDuQuebec.FUSEAU).toLocalDate() + " (heure de Montréal)");
    }

    /** La minute d'un instant, clé de comptage : « 2026-09-12T14:03 ». */
    public static String cleMinute(String iso) {
        return iso(Instant.parse(iso)).substring(0, 16);
    }

    private static Disponibilite vide(String mois) {
        return new Disponibilite(mois, null, null, 0, 0, 0, null, STANDARD.objectifPourcent(), null, List.of());
    }

    private static List<Verdict> verdicts(List<EngagementsDeService.Engagement> engagements, Double pourcentage) {
        if (pourcentage == null) {
            return List.of();
        }
        return engagements.stream().map(e -> new Verdict(e.politique().code(), e.politique().libelle(), e.objectifPourcent(),
                e.seuilManquementMajeurPourcent().isPresent() ? e.seuilManquementMajeurPourcent().getAsDouble() : null,
                EngagementsDeService.manquementDe(e, pourcentage).code())).toList();
    }

    /**
     * Disponibilité du mois = minutes avec battement / minutes observées hors maintenance
     * planifiée. Une minute sans battement est une minute indisponible : le serveur ne peut pas
     * témoigner de sa propre panne, son silence le fait pour lui. L'observation commence au premier
     * battement connu et s'arrête à {@code maintenant}.
     */
    public static Disponibilite calculerDisponibilite(String mois, Iterable<String> battements, List<Fenetre> maintenances, String maintenant, String premierBattement,
                                                      List<EngagementsDeService.Engagement> engagements) {
        if (!MOIS.matcher(mois).matches() || premierBattement == null) {
            return vide(mois);
        }
        long debutMois = Instant.parse(mois + "-01T00:00:00.000Z").toEpochMilli();
        long finMois = YearMonth.parse(mois).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long depuis = Math.max(debutMois, Instant.parse(cleMinute(premierBattement) + ":00.000Z").toEpochMilli());
        long jusqua = Math.min(finMois, Instant.parse(cleMinute(maintenant) + ":00.000Z").toEpochMilli());
        if (jusqua <= depuis) {
            return vide(mois);
        }
        Set<String> vus = new HashSet<>();
        battements.forEach(b -> vus.add(cleMinute(b)));
        List<long[]> fenetres = maintenances.stream().map(f -> new long[] {Instant.parse(f.debut()).toEpochMilli(), Instant.parse(f.fin()).toEpochMilli()}).toList();
        int observees = 0;
        int exclues = 0;
        int indisponibles = 0;
        for (long t = depuis; t < jusqua; t += 60_000) {
            long instant = t;
            if (fenetres.stream().anyMatch(f -> instant >= f[0] && instant < f[1])) {
                exclues++;
                continue;
            }
            observees++;
            if (!vus.contains(iso(Instant.ofEpochMilli(t)).substring(0, 16))) {
                indisponibles++;
            }
        }
        Double pourcentage = observees == 0 ? null : Math.round(((observees - indisponibles) / (double) observees) * 10_000) / 100.0;
        return new Disponibilite(mois, iso(Instant.ofEpochMilli(depuis)), iso(Instant.ofEpochMilli(jusqua)), observees, exclues, indisponibles, pourcentage,
                STANDARD.objectifPourcent(), pourcentage == null ? null : pourcentage >= STANDARD.objectifPourcent(), verdicts(engagements, pourcentage));
    }
}
