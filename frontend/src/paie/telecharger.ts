/**
 * Télécharger un PDF de la paie (fiche ou registre, ADR 0084) : la requête porte le jeton de la session, puis le
 * navigateur enregistre le fichier sous le nom que donne le serveur.
 */
import { useCallback, useState } from "react";
import { useAuth } from "../AuthContext";

const NOM = /filename="([^"]+)"/;

export function useTelechargementPdf() {
  const { apiFetch } = useAuth();
  const [erreurPdf, setErreurPdf] = useState("");
  const telecharger = useCallback(async (chemin: string, nomParDefaut: string) => {
    setErreurPdf("");
    const r = await apiFetch(chemin);
    if (!r.ok) {
      setErreurPdf(`Le PDF n'a pas pu être produit (erreur ${r.status}).`);
      return;
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = NOM.exec(r.headers.get("Content-Disposition") ?? "")?.[1] ?? nomParDefaut;
    a.click();
    URL.revokeObjectURL(url);
  }, [apiFetch]);
  return { telecharger, erreurPdf };
}
