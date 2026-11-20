package ca.vina.nexu.saas.domaine;

import ca.vina.nexu.partage.erreurs.domaine.RefusException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * <strong>L'attestation au gabarit du contrat</strong> — FX-AO-20260810, clause 10.18.04 b) iv) et annexe 10.18.04 C.
 * Au terme du contrat, le fournisseur transmet dans les soixante jours l'attestation de destruction, au libellé de
 * l'annexe C et signée par une personne autorisée désignée à cette fin. La destruction elle-même et son empreinte
 * restent celles de {@link Destruction} : ce rendu ne fait que les porter au gabarit exigé.
 */
public final class AnnexeDestruction {

    /** Clause 10.18.04 b) iv) : « dans les SOIXANTE (60) jours suivant la fin du Contrat ». */
    public static final int DELAI_TRANSMISSION_JOURS = 60;

    private static final String COCHEE = "☒";
    private static final String VIDE = "☐";

    private AnnexeDestruction() {
    }

    /**
     * La personne autorisée qui signe, son employeur (le fournisseur) et l'adresse de son bureau principal — trois
     * données de configuration. Lue telle quelle ; {@link #exigerComplete()} refuse de signer à blanc.
     */
    public record Signataire(String nom, String fournisseur, String adresse) {

        public Signataire {
            nom = nom == null ? "" : nom.strip();
            fournisseur = fournisseur == null ? "" : fournisseur.strip();
            adresse = adresse == null ? "" : adresse.strip();
        }

        public Signataire exigerComplete() {
            List<String> manquants = new ArrayList<>();
            if (nom.isEmpty()) {
                manquants.add("SAAS_ATTESTATION_SIGNATAIRE");
            }
            if (fournisseur.isEmpty()) {
                manquants.add("SAAS_ATTESTATION_FOURNISSEUR");
            }
            if (adresse.isEmpty()) {
                manquants.add("SAAS_ATTESTATION_ADRESSE");
            }
            if (!manquants.isEmpty()) {
                throw new VerrouException("signataire autorisé non configuré (annexe 10.18.04 C) : " + String.join(", ", manquants));
            }
            return this;
        }
    }

    /** La fin du contrat et la date limite de transmission ; {@code horsDelai} si l'attestation part après elle. */
    public record Echeance(LocalDate finContrat, LocalDate transmettreAuPlusTard, boolean horsDelai) {

        public static Echeance de(LocalDate finContrat, LocalDate aujourdhui) {
            LocalDate limite = finContrat.plusDays(DELAI_TRANSMISSION_JOURS);
            return new Echeance(finContrat, limite, aujourdhui.isAfter(limite));
        }
    }

    /** Convertit la date de fin du contrat reçue à la frontière ; absente ou mal formée, le refus la nomme. */
    public static LocalDate finContrat(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            throw new RefusException(List.of("finContrat : date de fin du contrat attendue (AAAA-MM-JJ)"));
        }
        try {
            return LocalDate.parse(saisie.strip());
        } catch (DateTimeParseException e) {
            throw new RefusException(List.of("finContrat : « " + saisie + " » n'est pas une date AAAA-MM-JJ"));
        }
    }

    /** Le texte de l'annexe 10.18.04 C, rempli, suivi du relevé de la destruction et de son empreinte. */
    public static String texte(Destruction.Attestation a, Signataire signataire, Echeance echeance, LocalDate aujourdhui) {
        Signataire s = signataire.exigerComplete();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("ANNEXE 10.18.04 C - ATTESTATION DE DESTRUCTION DES RENSEIGNEMENTS\n\n");
        sb.append("Date : ").append(aujourdhui).append("\n\n");
        sb.append("Je, soussigné(e), ").append(s.nom()).append(", exerçant mes fonctions au sein de ").append(s.fournisseur())
                .append(", dont le bureau principal est situé à l'adresse ").append(s.adresse()).append(",\n");
        sb.append("déclare solennellement que je suis dûment autorisé(e) pour certifier que les renseignements communiqués par\n");
        sb.append("l'ORGANISME PUBLIC (").append(a.tenantNom()).append(") ou toute autre personne dans le cadre du projet octroyé\n");
        sb.append("à ").append(s.fournisseur()).append(" et qui prend fin le ").append(echeance.finContrat())
                .append(", ont été détruits selon les méthodes suivantes :\n\n");
        sb.append(VIDE).append(" par déchiquetage (renseignements sur support papier)\n");
        sb.append(COCHEE).append(" par destruction logique et effacement physique en utilisant un logiciel de réécriture\n");
        sb.append("  (renseignements sur support informatique) : suppression de toutes les lignes de l'organisme, réécriture\n");
        sb.append("  des pages libérées (SQLite secure_delete), compaction du fichier (VACUUM), le ").append(a.detruitLe()).append('\n');
        sb.append(VIDE).append(" par un autre mode de destruction\n\n");
        sb.append("_________________________\n").append(s.nom()).append(" (signature de l'employé)\n\n");
        sb.append("Relevé : ").append(a.totalLignes()).append(" lignes détruites dans ").append(a.tables().size())
                .append(" table(s) ; empreinte SHA-256 ").append(a.empreinte()).append('\n');
        sb.append("Transmission à l'organisme public au plus tard le ").append(echeance.transmettreAuPlusTard())
                .append(" (clause 10.18.04 b iv, ").append(DELAI_TRANSMISSION_JOURS).append(" jours suivant la fin du contrat)");
        if (echeance.horsDelai()) {
            sb.append(" — DÉLAI DÉPASSÉ");
        }
        return sb.append('\n').toString();
    }
}
