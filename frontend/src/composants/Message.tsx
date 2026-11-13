/**
 * **Message** — la seule façon d'annoncer quelque chose à l'usager : information, succès,
 * avertissement ou erreur. Il remplace `.errors`, `.error`, `.success`, `.info-box` et les
 * « toasts » maison. Chaque variante porte une **icône et un texte** : la couleur seule ne dit
 * rien (WCAG 1.4.1). Une erreur est annoncée (`role="alert"`) ; le reste est un statut poli
 * (`role="status"`), sans voler le focus (WCAG 4.1.3).
 */
import { ReactNode } from "react";
import Icone, { type NomIcone } from "./Icone";

export type VarianteMessage = "info" | "succes" | "avertissement" | "erreur";

interface Props {
  variante?: VarianteMessage;
  children: ReactNode;
  /** Titre court en tête du message (« Enregistrement impossible »). */
  titre?: string;
  testId?: string;
  /** Un bouton « Fermer » : l'appelant retire alors le message de son état. */
  onFermer?: () => void;
  className?: string;
  /** Rôle ARIA explicite quand l'appelant sait mieux (une liste d'erreurs de validation reste un `alert`). */
  role?: "alert" | "status";
}

const ICONES: Record<VarianteMessage, NomIcone> = { info: "info", succes: "succes", avertissement: "avertissement", erreur: "erreur" };
const LIBELLES: Record<VarianteMessage, string> = { info: "Information", succes: "Succès", avertissement: "Avertissement", erreur: "Erreur" };

export default function Message({ variante = "info", children, titre, testId, onFermer, className, role }: Props) {
  const roleEffectif = role ?? (variante === "erreur" ? "alert" : "status");
  return (
    <div className={["message", `message-${variante}`, className].filter(Boolean).join(" ")} role={roleEffectif} data-testid={testId}>
      {/* Le nom de la variante est porté par l'icône (rôle image), pas par du texte : le contenu textuel
          du message reste exactement ce que l'appelant a écrit — ce que les essais lisent et découpent. */}
      <span className="message-icone" role="img" aria-label={LIBELLES[variante]}><Icone nom={ICONES[variante]} taille={18} /></span>
      <div className="message-corps">
        {titre && <strong className="message-titre">{titre}</strong>}
        <div className="message-texte">{children}</div>
      </div>
      {onFermer && (
        <button type="button" className="message-fermer" onClick={onFermer} aria-label="Fermer le message" data-testid={testId ? `${testId}-fermer` : undefined}>
          <Icone nom="fermer" />
        </button>
      )}
    </div>
  );
}
