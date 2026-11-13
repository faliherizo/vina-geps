/**
 * Les pièces jointes d'un billet de soutien : le champ qui les reçoit (captures d'écran,
 * journaux, exports), avec ses limites dites avant le refus, et la liste qui les rend
 * téléchargeables. Partagé par l'écran de l'auteur et la console du soutien.
 */
import { useState } from "react";
import { downloadApiFile } from "./FileViewerModal";

export interface PieceLocale { nom: string; typeMime: string; contenu: string; taille: number }
export interface Piece { id: string; messageId: string | null; nom: string; typeMime: string; taille: number; ajouteLe: string; ajouteParNom: string }
export interface LimitesPieces { maxParEnvoi: number; tailleMaxOctets: number; typesAdmis: string[] }

export const LIMITES_PAR_DEFAUT: LimitesPieces = {
  maxParEnvoi: 5, tailleMaxOctets: 10 * 1024 * 1024,
  typesAdmis: ["image/png", "image/jpeg", "image/gif", "image/webp", "application/pdf", "text/plain", "text/csv", "application/json",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip"],
};

export function tailleLisible(octets: number): string {
  if (octets < 1024) return `${octets} o`;
  if (octets < 1024 * 1024) return `${Math.round(octets / 1024)} Ko`;
  return `${(octets / (1024 * 1024)).toFixed(1).replace(".", ",")} Mo`;
}

/** Le type MIME d'un fichier, sinon déduit de son extension (les journaux `.log` arrivent sans type). */
export function typeDe(fichier: { name: string; type: string }): string {
  if (fichier.type) return fichier.type;
  const ext = fichier.name.toLowerCase().split(".").pop() ?? "";
  const parExtension: Record<string, string> = { log: "text/plain", txt: "text/plain", csv: "text/csv", json: "application/json", png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", pdf: "application/pdf" };
  return parExtension[ext] ?? "";
}

/** Les refus, dans les mots de l'écran, avant l'envoi : la même règle que le serveur, mais dite tout de suite. */
export function refusPiece(fichier: { name: string; type: string; size: number }, deja: number, limites: LimitesPieces): string | null {
  if (deja >= limites.maxParEnvoi) return `${limites.maxParEnvoi} pièces au plus par envoi`;
  if (fichier.size > limites.tailleMaxOctets) return `${fichier.name} : trop volumineux (${tailleLisible(limites.tailleMaxOctets)} au plus)`;
  if (!limites.typesAdmis.includes(typeDe(fichier))) return `${fichier.name} : type non admis`;
  return null;
}

function lireEnBase64(fichier: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const lecteur = new FileReader();
    lecteur.onload = () => resolve(String(lecteur.result).split(",")[1] ?? "");
    lecteur.onerror = () => reject(new Error("lecture impossible"));
    lecteur.readAsDataURL(fichier);
  });
}

/**
 * Le champ de pièces jointes : un sélecteur de fichiers, la liste de ce qui est retenu, et un
 * bouton pour en retirer. Ce que le composant refuse est dit sous le champ, jamais avalé.
 */
export function ChampPieces({ pieces, onChange, limites, testId }: { pieces: PieceLocale[]; onChange: (p: PieceLocale[]) => void; limites: LimitesPieces; testId: string }) {
  const [refus, setRefus] = useState("");
  async function ajouter(fichiers: FileList | null) {
    if (!fichiers) return;
    const retenues = [...pieces];
    const refuses: string[] = [];
    for (const f of Array.from(fichiers)) {
      const motif = refusPiece(f, retenues.length, limites);
      if (motif) { refuses.push(motif); continue; }
      retenues.push({ nom: f.name, typeMime: typeDe(f), contenu: await lireEnBase64(f), taille: f.size });
    }
    setRefus(refuses.join(" ; "));
    onChange(retenues);
  }
  return (
    <div className="field field-full" data-testid={testId}>
      <label className="field"><span>Pièces jointes</span>
        <input type="file" multiple accept={limites.typesAdmis.join(",")} data-testid={`${testId}-fichiers`} aria-describedby={`${testId}-aide`}
               onChange={(e) => { void ajouter(e.target.files); e.target.value = ""; }} />
        <span className="aide" id={`${testId}-aide`}>
          Captures d'écran, journaux, exports — {limites.maxParEnvoi} fichiers au plus, {tailleLisible(limites.tailleMaxOctets)} chacun (images, PDF, texte, CSV, Excel, Word, ZIP).
        </span>
      </label>
      {refus && <p className="error" role="alert" data-testid={`${testId}-refus`}>{refus}</p>}
      {pieces.length > 0 && (
        <ul className="pieces-liste" data-testid={`${testId}-liste`}>
          {pieces.map((p, i) => (
            <li key={`${p.nom}-${i}`}>
              <span>{p.nom} <span className="muted">({tailleLisible(p.taille)})</span></span>
              <button type="button" className="secondary" data-testid={`${testId}-retirer-${i}`} aria-label={`Retirer ${p.nom}`}
                      onClick={() => onChange(pieces.filter((_, j) => j !== i))}>Retirer</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Les pièces d'un billet (ou d'un message), téléchargeables sous leur nom. */
export function ListePieces({ ticketId, pieces, messageId, testId, signaler }: { ticketId: string; pieces: Piece[]; messageId?: string | null; testId: string; signaler: (m: string) => void }) {
  const visibles = messageId === undefined ? pieces : pieces.filter((p) => p.messageId === messageId);
  if (visibles.length === 0) return null;
  return (
    <ul className="pieces-liste" data-testid={testId} aria-label="Pièces jointes">
      {visibles.map((p) => (
        <li key={p.id}>
          <button type="button" className="lien" data-testid={`${testId}-${p.id}`}
                  onClick={() => downloadApiFile(`/api/soutien/tickets/${ticketId}/pieces/${p.id}`, p.nom).catch((e) => signaler(`Téléchargement impossible : ${e.message}`))}>
            📎 {p.nom}
          </button>
          <span className="muted"> ({tailleLisible(p.taille)}, {p.ajouteParNom})</span>
        </li>
      ))}
    </ul>
  );
}

/** Le navigateur et l'appareil, tels que le poste les déclare — relevés d'office, jamais demandés. */
export function decrireNavigateur(): string {
  if (typeof navigator === "undefined") return "";
  const ua = navigator.userAgent;
  const nav = /Edg\/([\d.]+)/.exec(ua) ? `Edge ${/Edg\/([\d.]+)/.exec(ua)![1]}`
    : /Chrome\/([\d.]+)/.exec(ua) ? `Chrome ${/Chrome\/([\d.]+)/.exec(ua)![1]}`
    : /Firefox\/([\d.]+)/.exec(ua) ? `Firefox ${/Firefox\/([\d.]+)/.exec(ua)![1]}`
    : /Version\/([\d.]+).*Safari/.exec(ua) ? `Safari ${/Version\/([\d.]+)/.exec(ua)![1]}` : "Navigateur inconnu";
  const os = /Windows/.test(ua) ? "Windows" : /Mac OS X/.test(ua) ? "macOS" : /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS" : /Linux/.test(ua) ? "Linux" : "";
  return [nav, os, typeof window !== "undefined" ? `${window.innerWidth}×${window.innerHeight}` : ""].filter(Boolean).join(" · ").slice(0, 500);
}
