/**
 * **Exporter un résultat de recherche** des effectifs — P-TN-8 : « exporter les résultats de
 * recherche sur demande dans ces différents formats : Excel, PDF, CSV, JSON ».
 *
 * Le fichier est produit par le serveur, avec les filtres de l'écran : ce qui s'emporte est
 * exactement ce qui a été cherché, et non ce que la page a fini d'afficher.
 */
import { useState } from "react";
import { Message } from "../composants";
import { useApiEffectifs } from "./api";

const FORMATS = [
  { code: "csv", libelle: "CSV" },
  { code: "xlsx", libelle: "Excel (XLSX)" },
  { code: "pdf", libelle: "PDF" },
  { code: "json", libelle: "JSON" },
] as const;

interface Props {
  /** Le chemin de la recherche, sans le paramètre `format`. */
  chemin: string;
  parametres: URLSearchParams;
  /** Le nom du fichier, sans extension : le format la donne. */
  nomFichier: string;
  /** Préfixe des identifiants d'essai : `<prefixe>-format` et `<prefixe>-exporter`. */
  prefixe: string;
  desactive?: boolean;
}

export default function ExportResultats({ chemin, parametres, nomFichier, prefixe, desactive = false }: Props) {
  const { apiFetch } = useApiEffectifs();
  const [format, setFormat] = useState<string>("csv");
  const [erreur, setErreur] = useState("");

  async function exporter() {
    setErreur("");
    const p = new URLSearchParams(parametres);
    p.set("format", format);
    const r = await apiFetch(`${chemin}?${p}`);
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      setErreur(j.errors?.join(", ") || `L'export a échoué (erreur ${r.status}).`);
      return;
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url; a.download = `${nomFichier}.${format}`; a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="eff-export" role="group" aria-label="Exporter les résultats">
      <p>
        <label htmlFor={`${prefixe}-format`}>Format d'export</label>
        <select id={`${prefixe}-format`} data-testid={`${prefixe}-format`} value={format} onChange={(e) => setFormat(e.target.value)}>
          {FORMATS.map((f) => <option key={f.code} value={f.code}>{f.libelle}</option>)}
        </select>
      </p>
      <button type="button" className="secondary" data-testid={`${prefixe}-exporter`} disabled={desactive} onClick={() => void exporter()}>
        Exporter
      </button>
      {erreur && <Message variante="erreur" testId={`${prefixe}-export-erreur`}>{erreur}</Message>}
    </div>
  );
}
