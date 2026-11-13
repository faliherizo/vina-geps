package ca.vina.nexu.saas.domaine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * La destruction attestée des données d'un établissement (contrat 9.25.06, EFVP 7) — la partie
 * pure : les trois verrous, l'empreinte de l'attestation et son texte, port de `saas/destruction.ts`.
 */
public final class Destruction {

    private Destruction() {
    }

    /** Tables conservées : l'identité contractuelle, le journal de l'instance, les attestations. */
    public static final List<String> CONSERVEES = List.of("tenants", "saas_attestations_destruction", "audit_log");

    public record Table(String table, int lignes) { }

    public record Attestation(String id, String tenantId, String tenantNom, String detruitLe, String detruitPar, String motif,
                              List<Table> tables, int totalLignes, String empreinte) { }

    /** Résilié, nom retapé, jamais deux fois — les mêmes messages que le Node, dans le même ordre. */
    public static void verifierVerrous(String statut, String nom, String confirmation, boolean dejaDetruit) {
        if (dejaDetruit) {
            throw new IllegalStateException("les données de cet établissement ont déjà été détruites : voir l'attestation");
        }
        if (!"terminated".equals(statut)) {
            throw new IllegalArgumentException("l'établissement doit être résilié avant la destruction de ses données");
        }
        if (!nom.equals(confirmation)) {
            throw new IllegalArgumentException("confirmation : retapez le nom exact de l'établissement");
        }
    }

    /** SHA-256 de l'énumération (locataire, instant, acteur, tables) : l'attestation ne se réécrit pas sans que cela se voie. */
    public static String empreinte(String tenantId, String detruitLe, String detruitPar, List<Table> tables) {
        StringBuilder sb = new StringBuilder("{\"tenantId\":\"").append(tenantId).append("\",\"detruitLe\":\"").append(detruitLe)
                .append("\",\"detruitPar\":\"").append(detruitPar).append("\",\"detail\":[");
        for (int i = 0; i < tables.size(); i++) {
            sb.append(i > 0 ? "," : "").append("{\"table\":\"").append(tables.get(i).table()).append("\",\"lignes\":").append(tables.get(i).lignes()).append("}");
        }
        sb.append("]}");
        return HexFormat.of().formatHex(condensat("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** SHA-256 est obligatoire sur toute JVM (JCA) : son absence est une erreur de plateforme, pas un cas métier. */
    static MessageDigest condensat(String algorithme) {
        try {
            return MessageDigest.getInstance(algorithme);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("algorithme de condensat indisponible : " + algorithme, e);
        }
    }

    public static String texte(Attestation a) {
        StringBuilder sb = new StringBuilder();
        sb.append("ATTESTATION DE DESTRUCTION DES DONNÉES\n\n");
        sb.append("Établissement : ").append(a.tenantNom()).append(" (").append(a.tenantId()).append(")\n");
        sb.append("Date de destruction : ").append(a.detruitLe()).append('\n');
        sb.append("Exécutée par : ").append(a.detruitPar()).append('\n');
        sb.append("Motif : ").append(a.motif() == null ? "fin du contrat" : a.motif()).append("\n\n");
        sb.append("Méthode : suppression de toutes les lignes rattachées à l'établissement dans chaque table de la base,\n");
        sb.append("réécriture des pages libérées (SQLite secure_delete), compaction du fichier (VACUUM). Les copies de\n");
        sb.append("sauvegarde expirent selon la rétention comptée (96 incrémentales, 30 complètes).\n\n");
        sb.append("Tables et lignes détruites :\n");
        for (Table t : a.tables()) {
            sb.append("  - ").append(t.table()).append(" : ").append(t.lignes()).append('\n');
        }
        sb.append("Total : ").append(a.totalLignes()).append(" lignes\n\n");
        sb.append("Empreinte SHA-256 de l'attestation : ").append(a.empreinte());
        return sb.toString();
    }

    /** La forme JSON de l'attestation, telle que le Node la sert. */
    public static Map<String, Object> enCarte(Attestation a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", a.id());
        m.put("tenantId", a.tenantId());
        m.put("tenantNom", a.tenantNom());
        m.put("detruitLe", a.detruitLe());
        m.put("detruitPar", a.detruitPar());
        m.put("motif", a.motif());
        m.put("tables", a.tables().stream().map(t -> Map.of("table", t.table(), "lignes", t.lignes())).toList());
        m.put("totalLignes", a.totalLignes());
        m.put("empreinte", a.empreinte());
        return m;
    }
}
