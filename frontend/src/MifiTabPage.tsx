/**
 * Wrapper de page autonome pour les fonctionnalités **extraites de MIFI**.
 *
 * L'établissement (tenant) provient du **compte connecté** (un admin = un établissement) ;
 * la gestion des tenants est réservée au superadmin. Plus de sélecteur ici : le composant
 * enfant reçoit directement `user.tenantId` (en-tête `X-Tenant-Id`). Fournit l'état error/success.
 */
import { ReactNode, useEffect, useState } from "react";
import { EnTeteEcran, Message, type NomIcone } from "./composants";
import { useAuth } from "./AuthContext";
import { useCurrentTenant } from "./organismeCourant";
import type { Msg } from "./organismeCourant";

export default function MifiTabPage({
  title, icon, testId, render,
}: {
  title: string;
  icon: NomIcone;
  storageKey?: string;
  testId: string;
  render: (tenantId: string, msg: Msg) => ReactNode;
}) {
  const { user } = useAuth();
  const isAdmin = user?.role === "admin" || user?.role === "superadmin";
  const { tenantId, tenantName } = useCurrentTenant();
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (!error && !success) return;
    const h = setTimeout(() => { setError(""); setSuccess(""); }, 4000);
    return () => clearTimeout(h);
  }, [error, success]);

  // Un admin doit être rattaché à un établissement ; les autres rôles opèrent sur leur tenant.
  const ready = !isAdmin || !!tenantId;

  return (
    <div className="page" data-testid={`${testId}-page`}>
      <EnTeteEcran titre={title} icone={icon}>
        {isAdmin && tenantId && (
          <p className="muted" style={{ fontSize: 13, margin: 0 }}>Organisme <span className="badge badge-active" data-testid={`${testId}-tenant`}>{tenantName ?? "…"}</span></p>
        )}
      </EnTeteEcran>
      {error && <Message variante="erreur" testId={`${testId}-error`}>{error}</Message>}
      {success && <Message variante="succes" testId={`${testId}-success`}>{success}</Message>}
      {!ready && (
        <p className="section-help">
          Aucun organisme n'est associé à ce compte. Connectez-vous avec un compte
          administrateur d'organisme (créé par le superadmin).
        </p>
      )}
      {ready && tenantId && render(tenantId, { setError, setSuccess })}
      {ready && !tenantId && !isAdmin && render("", { setError, setSuccess })}
    </div>
  );
}
