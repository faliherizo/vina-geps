/**
 * Documentation API (OpenAPI 3.0) — AO-5338 NF-03 / API ouverte.
 *
 * Affiche la spécification OpenAPI générée par le serveur : chemins REST, schémas de sécurité, et
 * surtout les **champs personnalisés exposés dynamiquement** (un champ ajouté dans l'interface
 * apparaît ici instantanément). Bouton de téléchargement du document `openapi.json`.
 */
import { useCallback, useEffect, useState } from "react";
import { EnTeteEcran, Icone } from "./composants";
import TbodyPagine from "./TbodyPagine";
import { useAuth } from "./AuthContext";
import { downloadApiFile } from "./FileViewerModal";

interface OpenApiSpec {
  info: { title: string; version: string; description?: string };
  paths: Record<string, Record<string, { summary?: string; tags?: string[] }>>;
  components: { schemas: Record<string, any> };
}

export default function ApiDocs() {
  const { apiFetch } = useAuth();
  const [spec, setSpec] = useState<OpenApiSpec | null>(null);

  const load = useCallback(async () => {
    const s = await apiFetch("/api/integrations/openapi.json").then((r) => (r.ok ? r.json() : null)).catch(() => null);
    // Une réponse qui n'est pas une spécification (vide, module absent) laisse l'écran « sans spécification » : elle ne le casse pas.
    setSpec(s && s.paths && s.components?.schemas ? s : null);
  }, [apiFetch]);
  useEffect(() => { load(); }, [load]);

  const cfSchemas = spec ? Object.entries(spec.components.schemas).filter(([k]) => k.startsWith("CustomFields_")) : [];
  const paths = spec ? Object.entries(spec.paths) : [];

  return (
    <div data-testid="apidocs-page">
      <EnTeteEcran icone="lien" titre="API ouverte (OpenAPI 3.0)" description={<>Documentation REST générée par le serveur. Les champs personnalisés ajoutés dans l'application sont exposés ici automatiquement (NF-03).</>} />

      <div className="actions" style={{ marginBottom: 12 }}>
        <a className="btn primary" data-testid="apidocs-swagger" href="/api/integrations/swagger" target="_blank" rel="noreferrer" style={{ textDecoration: "none" }}>🧪 Ouvrir Swagger UI (test interactif)</a>
        <button className="secondary" data-testid="apidocs-refresh" onClick={load}><Icone nom="rafraichir" /> Rafraîchir</button>
        <button className="secondary" data-testid="apidocs-download"
          onClick={() => downloadApiFile("/api/integrations/openapi.json", "openapi.json")}>⬇ Télécharger openapi.json</button>
      </div>

      {!spec && <p className="muted">Chargement de la spécification…</p>}

      {spec && (
        <>
          <div className="card">
            <h3>{spec.info.title} <span className="badge badge-rule">v{spec.info.version}</span></h3>
            <p className="muted">{spec.info.description}</p>
          </div>

          <div className="card" data-testid="apidocs-paths">
            <h3>Points d'accès REST ({paths.length})</h3>
            <div className="table-wrap" tabIndex={0} role="region" aria-label="Points d'accès de l'API (zone défilable)">
              <table>
                <caption className="sr-only">Points d'accès de l'API : méthode, chemin et description</caption>
                <thead><tr><th scope="col">Méthode</th><th scope="col">Chemin</th><th scope="col">Description</th></tr></thead>
                <TbodyPagine items={paths} rendre={([path, methods]) => Object.entries(methods).map(([method, op]) => (
                      <tr key={`${method}-${path}`} data-testid="apidocs-path">
                        <td><span className="badge badge-rule">{method.toUpperCase()}</span></td>
                        <td><code>{path}</code></td>
                        <td>{op.summary || "—"}</td>
                      </tr>
                    )) } />
              </table>
            </div>
          </div>

          <div className="card" data-testid="apidocs-custom-fields">
            <h3>Champs personnalisés exposés (NF-03)</h3>
            <p className="subtitle">Un champ ajouté dans « Administration → Champs personnalisés » apparaît ici sans redéploiement.</p>
            {cfSchemas.map(([name, schema]) => {
              const scope = name.replace("CustomFields_", "");
              const props = Object.entries(schema.properties || {});
              return (
                <div key={name} data-testid={`apidocs-cf-${scope}`} style={{ marginBottom: 10 }}>
                  <strong>{scope}</strong>{" "}
                  <span className="count-badge" data-testid={`apidocs-cf-count-${scope}`}>{schema["x-custom-field-count"] ?? 0}</span>
                  {props.length > 0 && (
                    <ul className="bullet-list">
                      {props.map(([key, def]: [string, any]) => (
                        <li key={key}>
                          <code>{key}</code> — {def.description || key} <span className="muted">({def.type}{def.enum ? ` : ${def.enum.join(", ")}` : ""})</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
