package ca.vina.nexu.securite.application.requetes;

import ca.vina.nexu.partage.temps.domaine.Horloge;
import ca.vina.nexu.securite.application.dto.FichierDto;
import ca.vina.nexu.securite.domaine.Incidents;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Le registre des incidents tel qu'on le lit et le remet : vues, CSV et PDF (Loi 25, conservation cinq ans). */
public final class LectureIncidents {

    /** Port : le rendu PDF du registre — la mise en page est une affaire d'infrastructure. */
    public interface RenduPdf {
        byte[] registre(String titre, String sousTitre, String filtres, List<List<String>> incidents);
    }

    private final Incidents.Registre registre;
    private final RenduPdf pdf;
    private final Horloge horloge;

    public LectureIncidents(Incidents.Registre registre, RenduPdf pdf, Horloge horloge) {
        this.registre = registre;
        this.pdf = pdf;
        this.horloge = horloge;
    }

    public Map<String, Object> vocabulaire() {
        return Map.of("natures", Incidents.NATURES, "renseignements", Incidents.RENSEIGNEMENTS, "gravites", Incidents.GRAVITES);
    }

    public Map<String, Object> vue(Incidents.Incident i) {
        return Incidents.vue(i, registre.nomsEtablissements().get(i.tenantId()));
    }

    /** {@code tenantId} nul : tous les établissements (administration du système). */
    public List<Map<String, Object>> lister(String tenantId, String statut, String gravite) {
        Map<String, String> noms = registre.nomsEtablissements();
        return registre.lister(tenantId, statut, gravite).stream().map(i -> Incidents.vue(i, noms.get(i.tenantId()))).toList();
    }

    public FichierDto csv(String tenantId, String statut, String gravite) {
        String corps = Incidents.registreCsv(registre.lister(tenantId, statut, gravite), registre.nomsEtablissements(), tenantId == null);
        return new FichierDto(Incidents.nomFichier(statut, gravite, "csv"), "text/csv;charset=UTF-8", corps.getBytes(StandardCharsets.UTF_8));
    }

    /** Le registre en PDF : ce qu'on remet à la CAI ou à une direction qui n'ouvre pas un CSV. */
    public FichierDto pdf(String tenantId, String statut, String gravite) {
        List<Incidents.Incident> incidents = registre.lister(tenantId, statut, gravite);
        Map<String, String> noms = registre.nomsEtablissements();
        String etablissement = tenantId == null ? "Tous les établissements" : noms.getOrDefault(tenantId, tenantId);
        String filtre = String.join(" ; ", java.util.stream.Stream.of(statut == null || statut.isBlank() ? null : "statut : " + statut,
                gravite == null || gravite.isBlank() ? null : "gravité : " + gravite).filter(java.util.Objects::nonNull).toList());
        List<List<String>> lignes = incidents.stream().map(i -> Incidents.lignesRegistre(i, noms.get(i.tenantId()), tenantId == null)).toList();
        byte[] octets = pdf.registre("Registre des incidents de confidentialité",
                etablissement + " — produit le " + horloge.aujourdhui() + " — Loi 25, conservation 5 ans", filtre, lignes);
        return new FichierDto(Incidents.nomFichier(statut, gravite, "pdf"), "application/pdf", octets);
    }
}
