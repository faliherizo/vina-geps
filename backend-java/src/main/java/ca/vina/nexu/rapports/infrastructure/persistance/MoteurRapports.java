package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.rapports.domaine.Agregation;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Exécution des rapports</b> (FIN191).
 *
 * <p>Le domaine valide la définition contre le catalogue et produit un plan. Chaque source est
 * servie par un <b>fournisseur de faits</b> qui applique le périmètre de l'acteur ; le moteur agrège
 * ces faits en mémoire et borne le résultat. Aucune chaîne de l'usager n'atteint la base : les faits
 * sont lus par les mêmes calculs que les écrans, et les filtres s'appliquent après.
 */
@Repository
public class MoteurRapports {

    private final List<FournisseurFaits> fournisseurs;

    public MoteurRapports(List<FournisseurFaits> fournisseurs) {
        this.fournisseurs = List.copyOf(fournisseurs);
    }

    public record Resultat(List<Map<String, Object>> colonnes, List<Map<String, Object>> lignes,
                           int total, boolean tronque) {
    }

    /** Exécuter un rapport : les faits de sa source, bornés au périmètre du {@code demandeur}, puis agrégés en mémoire. */
    public Resultat executer(CatalogueRapports.Definition d, FournisseurFaits.Demandeur demandeur) {
        CatalogueRapports.PlanRapport plan = CatalogueRapports.planifier(d);
        List<Map<String, Object>> lignes = Agregation.agreger(plan, faits(plan.source().code(), demandeur, CatalogueRapports.bornesDe(plan)));

        boolean tronque = lignes.size() > plan.limite();
        List<Map<String, Object>> colonnes = plan.colonnes().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("champ", c.champ());
            m.put("libelle", c.libelle());
            m.put("type", c.type().code());
            return m;
        }).toList();

        return new Resultat(colonnes, tronque ? List.copyOf(lignes.subList(0, plan.limite())) : lignes,
                tronque ? plan.limite() : lignes.size(), tronque);
    }

    /**
     * Les faits bruts d'une source calculée, par <b>le même</b> fournisseur que les rapports : un indicateur
     * mesuré par un second chemin donnerait un second chiffre pour la même question.
     */
    public List<Map<String, Object>> faits(String code, FournisseurFaits.Demandeur demandeur, CatalogueRapports.Periode periode) {
        FournisseurFaits f = fournisseurs.stream().filter(x -> x.sources().contains(code)).findFirst()
                .orElseThrow(() -> new CatalogueRapports.RapportInvalideException("la source « " + code + " » n'a pas de fournisseur de faits branché"));
        if (demandeur == null) {
            // Un fournisseur applique le périmètre de l'acteur : sans acteur, il n'y a rien à borner.
            throw new CatalogueRapports.RapportInvalideException("la source « " + code + " » exige l'identité du demandeur");
        }
        return f.faits(code, demandeur, periode);
    }

    public record Fichier(String contenu, String type, String extension) {
    }

    /** Les formats <b>réellement branchés</b>, et non ceux que l'énumération déclare. */
    public static final List<CatalogueRapports.FormatExport> FORMATS_DISPONIBLES = List.of(CatalogueRapports.FormatExport.CSV,
            CatalogueRapports.FormatExport.XLSX, CatalogueRapports.FormatExport.PDF, CatalogueRapports.FormatExport.JSON);

    /** Un fichier exporté, textuel ou binaire, prêt à servir. */
    public record Octets(byte[] contenu, String type, String extension) {
    }

    /** L'en-tête d'un export : ce que le relevé compte, et sur quelle période. */
    public record Entete(String titre, String sousTitre) {
    }

    /**
     * <b>L'export dans tous les formats branchés</b>, textuels comme binaires (Excel par POI, PDF par
     * PDFBox — les générateurs déjà présents au dépôt).
     */
    public static Octets exporterFichier(Resultat r, String format, Entete entete) {
        CatalogueRapports.FormatExport f = CatalogueRapports.FormatExport.de(format);
        return switch (f) {
            case XLSX -> new Octets(ExportsRapports.xlsx(r, entete.titre()),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
            case PDF -> new Octets(ExportsRapports.pdf(r, entete), "application/pdf", "pdf");
            case CSV, JSON -> {
                Fichier t = exporter(r, format);
                yield new Octets(t.contenu().getBytes(java.nio.charset.StandardCharsets.UTF_8), t.type(), t.extension());
            }
        };
    }

    /**
     * <b>L'export</b> (FIN191, « différents formats »).
     *
     * <p>{@code xlsx} et {@code pdf} sont rendus par les générateurs déjà présents au dépôt ; les
     * nommer au catalogue sans les brancher ferait échouer l'export au clic, alors que les refuser
     * <b>nommément</b> dit à l'usager ce qui est disponible aujourd'hui.
     */
    public static Fichier exporter(Resultat r, String format) {
        CatalogueRapports.FormatExport f = CatalogueRapports.FormatExport.de(format);
        return switch (f) {
            case JSON -> new Fichier(ca.vina.nexu.partage.json.infrastructure.Json.texte(r.lignes()),
                    "application/json", "json");
            case CSV -> csv(r);
            case XLSX, PDF -> throw new CatalogueRapports.RapportInvalideException("l'export « " + f.code()
                    + " » est binaire : il se produit par exporterFichier (les formats disponibles sont"
                    + " annoncés par /api/rapports/formats)");
        };
    }

    /** Le séparateur attendu par Excel en français, et le BOM sans lequel il lit les accents de travers. */
    private static final String SEPARATEUR = ";";
    private static final String BOM = "﻿";

    private static Fichier csv(Resultat r) {
        List<String> lignes = new ArrayList<>();
        lignes.add(String.join(SEPARATEUR, r.colonnes().stream()
                .map(c -> echapper(c.get("libelle"))).toList()));
        for (Map<String, Object> l : r.lignes()) {
            lignes.add(String.join(SEPARATEUR, r.colonnes().stream()
                    .map(c -> echapper(l.get(c.get("champ")))).toList()));
        }
        return new Fichier(BOM + String.join("\r\n", lignes), "text/csv; charset=utf-8", "csv");
    }

    private static String echapper(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        // Guillemets et retours de ligne échappés : une seule cellule décalerait tout le fichier.
        return s.matches("(?s).*[\";\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
