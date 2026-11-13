/**
 * Fenêtre de **visualisation en lecture** d'une pièce jointe (PJ) — images (JPG/PNG/GIF/WebP/SVG),
 * PDF, feuilles de calcul (XLSX/XLS/CSV via SheetJS), texte/JSON, avec repli « télécharger »
 * pour les types non prévisualisables.
 *
 * Le contenu est **toujours récupéré via `fetch`** (et non via `src` d'un `<img>`/`<iframe>`),
 * car le patch global de `fetch` y ajoute l'en-tête `X-Tenant-Id` requis par les modules de
 * base — un chargement d'élément natif ne le porterait pas et l'API répondrait 400. Les octets
 * sont ensuite exposés en **blob URL** (image/PDF) ou parsés (tableur/texte).
 *
 * Optionnellement **éditable** (renommer / remplacer / supprimer) si la PJ est enregistrée
 * (id présent) et que les rappels d'édition sont fournis. Les PJ « en attente » (non encore
 * enregistrées) sont prévisualisées depuis leur contenu base64 en mémoire.
 */
import { useEffect, useRef, useState } from "react";
import { Message } from "./composants";
import { apercuTableur } from "./apercuTableur";
import { Modal } from "./Modal";

export interface ViewerFile {
  /** Id serveur (PJ enregistrée) — requis pour l'édition. */
  id?: string;
  filename: string;
  mimeType?: string | null;
  /** URL serveur (PJ enregistrée). */
  url?: string;
  /** Contenu base64 (PJ en attente, non encore enregistrée). */
  dataBase64?: string;
}

export interface ViewerEdit {
  onRename: (newName: string) => Promise<void>;
  onReplace: (file: File) => Promise<void>;
  onDelete: () => Promise<void>;
}

type Kind = "image" | "pdf" | "sheet" | "text" | "other";

function kindOf(file: ViewerFile): Kind {
  const name = (file.filename || "").toLowerCase();
  const mt = (file.mimeType || "").toLowerCase();
  if (mt.startsWith("image/") || /\.(png|jpe?g|gif|webp|bmp|svg)$/.test(name)) return "image";
  if (mt === "application/pdf" || name.endsWith(".pdf")) return "pdf";
  if (/sheet|excel|csv|ms-excel|officedocument\.spreadsheet/.test(mt) || /\.(xlsx|xls|csv)$/.test(name)) return "sheet";
  if (mt.startsWith("text/") || /\.(txt|json|md|log|xml|html?)$/.test(name)) return "text";
  return "other";
}

/**
 * Télécharge un fichier servi par l'API **via `fetch`** (le patch global ajoute l'en-tête
 * `X-Tenant-Id` + cookies) puis déclenche l'enregistrement côté navigateur. À utiliser à la
 * place d'un `<a href="/api/...">` direct : une navigation/élément natif ne porte pas l'en-tête
 * de tenant et l'API répondrait 400 (« X-Tenant-Id header required »).
 */
export async function downloadApiFile(url: string, filename: string): Promise<void> {
  const r = await fetch(url);
  if (!r.ok) {
    let msg = `HTTP ${r.status}`;
    try { const j = await r.json(); if (j?.errors) msg = j.errors.join(", "); } catch { /* corps non-JSON */ }
    throw new Error(msg);
  }
  const objectUrl = URL.createObjectURL(await r.blob());
  const a = document.createElement("a");
  a.href = objectUrl; a.download = filename;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 10_000);
}

function base64ToBytes(b64: string): Uint8Array {
  const clean = b64.replace(/^data:[^;]+;base64,/, "");
  const bin = atob(clean);
  const arr = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  return arr;
}

export function FileViewerModal({ file, onClose, edit }: { file: ViewerFile | null; onClose: () => void; edit?: ViewerEdit }) {
  const [blobUrl, setBlobUrl] = useState("");
  const [sheetHtml, setSheetHtml] = useState("");
  const [textContent, setTextContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  const [renaming, setRenaming] = useState(false);
  const [newName, setNewName] = useState("");
  const [busy, setBusy] = useState(false);
  const replaceRef = useRef<HTMLInputElement>(null);

  const kind = file ? kindOf(file) : "other";

  useEffect(() => {
    if (!file) return;
    setNewName(file.filename); setRenaming(false); setError("");
    let cancelled = false;
    let createdUrl = "";
    (async () => {
      setLoading(true); setBlobUrl(""); setSheetHtml(""); setTextContent("");
      try {
        // Octets récupérés via fetch (en-tête X-Tenant-Id + cookies injectés) ou depuis le base64 en attente.
        let bytes: Uint8Array;
        if (file.url) {
          const r = await fetch(`${file.url}${file.url.includes("?") ? "&" : "?"}t=${reloadKey}`);
          if (!r.ok) throw new Error(`HTTP ${r.status}`);
          bytes = new Uint8Array(await r.arrayBuffer());
        } else {
          bytes = base64ToBytes(file.dataBase64 || "");
        }
        if (cancelled) return;
        if (kind === "sheet") {
          setSheetHtml(await apercuTableur(bytes, file.filename ?? ""));
        } else if (kind === "text") {
          setTextContent(new TextDecoder().decode(bytes));
        } else {
          // image / pdf / autre → blob URL (utilisable comme src et pour le téléchargement).
          createdUrl = URL.createObjectURL(new Blob([bytes as BlobPart], { type: file.mimeType || "application/octet-stream" }));
          setBlobUrl(createdUrl);
        }
      } catch (e: any) {
        if (!cancelled) setError(`Aperçu impossible : ${e.message}`);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; if (createdUrl) URL.revokeObjectURL(createdUrl); };
  }, [file, kind, reloadKey]);

  if (!file) return null;

  async function run(fn: () => Promise<void>) {
    setBusy(true); setError("");
    try { await fn(); } catch (e: any) { setError(e?.message || "Erreur"); } finally { setBusy(false); }
  }

  return (
    <Modal open={!!file} onClose={onClose} title={`📄 ${file.filename}`} testId="file-viewer" size="lg">
      {error && <Message variante="erreur" testId="file-viewer-error">{error}</Message>}

      <div className="file-viewer-body" data-testid="file-viewer-content"
        style={{ minHeight: 120, maxHeight: "65vh", overflow: "auto", background: "#fafafa", border: "1px solid #eee", borderRadius: 6, padding: 8 }}>
        {loading && <p className="muted" data-testid="file-viewer-loading">Chargement de l'aperçu…</p>}
        {!loading && kind === "image" && blobUrl && (
          <img src={blobUrl} alt={file.filename} data-testid="file-viewer-image"
            style={{ maxWidth: "100%", display: "block", margin: "0 auto" }} />
        )}
        {!loading && kind === "pdf" && blobUrl && (
          <iframe src={blobUrl} title={file.filename} data-testid="file-viewer-pdf"
            style={{ width: "100%", height: "60vh", border: 0 }} />
        )}
        {!loading && kind === "sheet" && (
          <div className="sheet-preview" data-testid="file-viewer-sheet"
            style={{ overflowX: "auto" }} dangerouslySetInnerHTML={{ __html: sheetHtml }} />
        )}
        {!loading && kind === "text" && (
          <pre data-testid="file-viewer-text" style={{ whiteSpace: "pre-wrap", margin: 0, fontSize: 12 }}>{textContent}</pre>
        )}
        {!loading && kind === "other" && (
          <p className="muted" data-testid="file-viewer-other">
            Aperçu non disponible pour ce type de fichier. Utilisez « Télécharger » pour l'ouvrir.
          </p>
        )}
      </div>

      <div className="modal-actions" style={{ flexWrap: "wrap", gap: 8, marginTop: 12 }}>
        {blobUrl && (
          <a className="btn" data-testid="file-viewer-download" href={blobUrl}
            download={file.filename}>⬇️ Télécharger</a>
        )}

        {edit && file.id && !renaming && (
          <button className="secondary" data-testid="file-viewer-rename-start" disabled={busy}
            onClick={() => { setNewName(file.filename); setRenaming(true); }}>✏️ Renommer</button>
        )}
        {edit && file.id && renaming && (
          <span style={{ display: "inline-flex", gap: 6, alignItems: "center" }}>
            <input data-testid="file-viewer-rename-input" aria-label="Nouveau nom du fichier" value={newName} onChange={(e) => setNewName(e.target.value)}
              style={{ minWidth: 200 }} />
            <button className="primary" data-testid="file-viewer-rename-save" disabled={busy || !newName.trim()}
              onClick={() => run(async () => { await edit.onRename(newName.trim()); file.filename = newName.trim(); setRenaming(false); })}>
              Enregistrer
            </button>
            <button className="secondary" disabled={busy} onClick={() => setRenaming(false)}>Annuler</button>
          </span>
        )}

        {edit && file.id && (
          <>
            <button className="secondary" data-testid="file-viewer-replace" disabled={busy}
              onClick={() => replaceRef.current?.click()}>🔁 Remplacer</button>
            <input ref={replaceRef} type="file" aria-label="Remplacer le fichier" style={{ display: "none" }}
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (f) run(async () => { await edit.onReplace(f); setReloadKey((k) => k + 1); });
                e.currentTarget.value = "";
              }} />
            <button className="danger" data-testid="file-viewer-delete" disabled={busy}
              onClick={() => run(async () => { await edit.onDelete(); onClose(); })}>🗑️ Supprimer</button>
          </>
        )}
        <button className="secondary" data-testid="file-viewer-dismiss" onClick={onClose} style={{ marginLeft: "auto" }}>Fermer</button>
      </div>
    </Modal>
  );
}
