/**
 * Page principale « Notifications » (extraite de MIFI).
 *
 * Envoi de notifications email/SMS aux professeurs ou établissements, individuel ou en lot,
 * avec variables {{cle}} (#9, #77). Multi-tenant : sélecteur de tenant (admin) → X-Tenant-Id.
 */
import { useCallback, useEffect, useState } from "react";
import { Message, EnTeteEcran } from "./composants";
import NotificationsPoussees from "./NotificationsPoussees";
import NotifierPersonne from "./NotifierPersonne";
import AnnoncesAdmin from "./Annonces";
import Textos from "./Textos";
import { useAuth } from "./AuthContext";
import { useCurrentTenant } from "./organismeCourant";
import { useT } from "./i18n";

export default function Notifications() {
  const { apiFetch, user } = useAuth();
  const { t } = useT();
  const isAdmin = user?.role === "admin" || user?.role === "superadmin";

  const { tenantId, tenantName } = useCurrentTenant();
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  const call = useCallback((path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers || {});
    if (tenantId) headers.set("X-Tenant-Id", tenantId);
    return apiFetch(path, { ...init, headers });
  }, [apiFetch, tenantId]);

  useEffect(() => {
    if (!error && !success) return;
    const h = setTimeout(() => { setError(""); setSuccess(""); }, 4000);
    return () => clearTimeout(h);
  }, [error, success]);

  const ready = !isAdmin || !!tenantId;

  return (
    <div className="page" data-testid="notifications-page">
      <EnTeteEcran icone="cloche" titre={<>{t("nav.notifications")}</>} />{isAdmin && tenantId && (
        <div className="grid-form" style={{ marginBottom: 12 }}>
          <span className="muted" style={{ fontSize: 13 }}>Organisme&nbsp;</span>
          <span className="badge badge-active" data-testid="notifications-tenant">🏫 {tenantName ?? "…"}</span>
        </div>
      )}
      {error && <Message variante="erreur" testId="notifications-error">{error}</Message>}
      {success && <Message variante="succes" testId="notifications-success">{success}</Message>}
      {!ready && <p className="section-help">Aucun organisme associé à ce compte (créé par le superadmin).</p>}

      {ready && (
        <>
          <NotificationsPoussees call={call} />
          <Textos call={call} adminSysteme={user?.role === "superadmin" || (user?.role === "admin" && !user?.tenantId)} signaler={setError} confirmer={setSuccess} />
          <NotifierPersonne call={call} signaler={setError} apresEnvoi={() => setSuccess("Avis envoyé.")} />
          {isAdmin && <AnnoncesAdmin call={call} signaler={setError} confirmer={setSuccess} />}
        </>
      )}
    </div>
  );
}
