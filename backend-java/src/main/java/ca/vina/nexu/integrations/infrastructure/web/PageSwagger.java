package ca.vina.nexu.integrations.infrastructure.web;

/**
 * La page Swagger UI ({@code services/swaggerUi.ts}, NF-08) : une coquille servie depuis le CDN, un
 * formulaire de connexion en tête qui place le jeton dans <em>Authorize</em> et l'établissement dans
 * {@code X-Tenant-Id}, puis « Try it out » sur chaque point d'entrée. La politique de sécurité de la
 * page remplace celle du pare-feu ({@code default-src 'none'}) : scripts du CDN et amorçage par nonce.
 */
public final class PageSwagger {

    private PageSwagger() {
    }

    static final String VERSION = "5.17.14";
    static final String CDN = "https://unpkg.com";
    private static final String GABARIT = """
<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Nexu VINA — API (Swagger UI)</title>
  <link rel="stylesheet" href="%CDN%/swagger-ui-dist@%VERSION%/swagger-ui.css" />
  <style>
    body{margin:0} .topbar{display:none}
    #hdr{font-family:-apple-system,system-ui,sans-serif;padding:12px 18px;background:#0f172a;color:#fff}
    #hdr b{color:#60a5fa}
    #connexion{display:flex;flex-wrap:wrap;gap:8px;align-items:flex-end;margin-top:10px}
    #connexion label{display:flex;flex-direction:column;font-size:12px;gap:4px;min-width:140px}
    #connexion input,#connexion select{min-height:32px;padding:4px 8px;border-radius:6px;border:1px solid #475569;background:#1e293b;color:#fff;font-size:14px}
    #connexion button{min-height:32px;padding:6px 14px;border-radius:6px;border:0;background:#2563eb;color:#fff;font-weight:600;cursor:pointer}
    #connexion button.secondaire{background:#334155}
    #etat{font-size:13px;margin-top:8px;min-height:18px}
    #etat.ok{color:#86efac} #etat.erreur{color:#fca5a5}
  </style>
</head>
<body>
  <div id="hdr">
    🧩 <b>Nexu VINA</b> — Documentation &amp; test interactif des API REST (OpenAPI 3.0).
    Connectez-vous ci-dessous : le jeton obtenu est placé dans <em>Authorize</em> (bearerAuth) et l'établissement dans <em>X-Tenant-Id</em> ; « Try it out » appelle ensuite chaque point d'entrée en votre nom.
    <form id="connexion" autocomplete="off">
      <label>Identifiant <input id="cx-user" name="username" value="admin" required autocomplete="username" /></label>
      <label>Mot de passe <input id="cx-pass" name="password" type="password" required autocomplete="current-password" /></label>
      <label>Établissement <select id="cx-tenant" name="tenantId"><option value="">— Compte système (aucun) —</option></select></label>
      <label id="cx-mfa-label" hidden>Vérification <input id="cx-mfa" name="mfa" autocomplete="one-time-code" /></label>
      <button type="submit" id="cx-connecter">Se connecter et obtenir un jeton</button>
      <button type="button" class="secondaire" id="cx-whoami">Qui suis-je ?</button>
      <button type="button" class="secondaire" id="cx-deconnecter">Oublier le jeton</button>
    </form>
    <div id="etat" role="status" aria-live="polite"></div>
  </div>
  <div id="swagger-ui"></div>
  <script src="%CDN%/swagger-ui-dist@%VERSION%/swagger-ui-bundle.js" crossorigin></script>
  <script src="%CDN%/swagger-ui-dist@%VERSION%/swagger-ui-standalone-preset.js" crossorigin></script>
  <script nonce="%NONCE%">
    function readCookie(n){var m=document.cookie.match('(^|;)\\\\s*'+n+'\\\\s*=\\\\s*([^;]+)');return m?m.pop():'';}
    var jeton = null, tenantChoisi = "";
    var etat = document.getElementById("etat");
    function dire(texte, classe){ etat.textContent = texte; etat.className = classe || ""; }
    // Sans les actifs du CDN, la page reste utile : connexion et « Qui suis-je ? » ne dépendent pas de Swagger UI.
    try { window.ui = SwaggerUIBundle({
      url: %SPEC%,
      dom_id: "#swagger-ui",
      deepLinking: true,
      persistAuthorization: true,
      presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
      layout: "StandaloneLayout",
      requestInterceptor: function (req) {
        req.credentials = "include";
        // Jeton obtenu par le formulaire : il prime sur le cookie ; sinon la session du navigateur sert.
        if (jeton && !req.headers["Authorization"]) req.headers["Authorization"] = "Bearer " + jeton;
        // Établissement : celui du formulaire, sinon celui de l'application (localStorage).
        var tid = tenantChoisi;
        try { if (!tid) tid = localStorage.getItem("establishmentId") || ""; } catch (e) {}
        if (tid && !req.headers["X-Tenant-Id"]) req.headers["X-Tenant-Id"] = tid;
        var csrf = readCookie("csrf");
        if (csrf && ["POST","PUT","PATCH","DELETE"].indexOf((req.method||"").toUpperCase()) >= 0) {
          req.headers["X-CSRF-Token"] = csrf;
        }
        return req;
      },
      onComplete: function () { if (!jeton) dire("Spécification chargée avec votre session. Connectez-vous ci-dessus pour obtenir un jeton et choisir un établissement.", "ok"); }
    }); } catch (e) { dire("Swagger UI n'a pas pu se charger (actifs du CDN inaccessibles) ; la connexion et « Qui suis-je ? » fonctionnent quand même, et la spécification reste servie par " + %SPEC% + ".", "erreur"); }
    // Les établissements offerts à la connexion (route publique), pour choisir le locataire.
    fetch("/api/auth/tenants", { credentials: "include" }).then(function (r) { return r.ok ? r.json() : []; }).then(function (liste) {
      var sel = document.getElementById("cx-tenant");
      (Array.isArray(liste) ? liste : []).forEach(function (t) { var o = document.createElement("option"); o.value = t.id; o.textContent = t.name + " (" + t.id + ")"; sel.appendChild(o); });
    }).catch(function () {});
    // La session du navigateur suffit à « Try it out » (cookie) : on le dit dès l'ouverture, plutôt que de
    // laisser croire qu'une connexion est exigée ici.
    fetch("/api/auth/whoami", { credentials: "include" }).then(function (r) { return r.ok ? r.json() : null; }).then(function (j) {
      if (!j || !j.utilisateur) return;
      var l = j.locataire;
      if (l && l.id) { tenantChoisi = l.id; try { document.getElementById("cx-tenant").value = l.id; } catch (e) {} }
      dire("Session du navigateur reconnue : " + j.utilisateur.username + " (" + j.utilisateur.role + ")" + (l ? ", établissement " + l.name : "") + ". « Try it out » appelle l'API en votre nom ; connectez-vous ci-dessous seulement pour un jeton ou un autre établissement.", "ok");
    }).catch(function () {});
    // Une seconde étape de connexion (question secrète, code TOTP) demande un champ de plus ; la réponse 200 sans jeton le dit.
    var mfa = null;
    function montrerMfa(question) {
      var label = document.getElementById("cx-mfa-label");
      label.hidden = false;
      label.firstChild.textContent = question + " ";
      document.getElementById("cx-mfa").value = "";
      document.getElementById("cx-mfa").focus();
    }
    document.getElementById("connexion").addEventListener("submit", function (e) {
      e.preventDefault();
      var corps = { username: document.getElementById("cx-user").value, password: document.getElementById("cx-pass").value };
      tenantChoisi = document.getElementById("cx-tenant").value;
      if (tenantChoisi) corps.tenantId = tenantChoisi;
      var reponseMfa = document.getElementById("cx-mfa").value;
      if (mfa === "totp" && reponseMfa) corps.totpCode = reponseMfa;
      if (mfa === "question" && reponseMfa) corps.securityAnswer = reponseMfa;
      dire("Connexion…");
      fetch("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" }, credentials: "include", body: JSON.stringify(corps) })
        .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, statut: r.status, j: j }; }); })
        .then(function (res) {
          if (!res.ok) { dire("Connexion refusée (" + res.statut + ") : " + ((res.j.errors || ["identifiants refusés"]).join(" ; ")), "erreur"); return; }
          if (res.j.tenantChoiceRequired) {
            var sel = document.getElementById("cx-tenant");
            sel.innerHTML = "";
            (res.j.tenants || []).forEach(function (t) { var o = document.createElement("option"); o.value = t.id; o.textContent = t.name + " (" + t.id + ")"; sel.appendChild(o); });
            sel.focus();
            dire("Ce compte existe dans plusieurs établissements : choisissez-en un, puis reconnectez-vous.", "erreur");
            return;
          }
          if (res.j.mfaRequired) {
            mfa = res.j.securityMethod === "totp" ? "totp" : "question";
            var q = res.j.securityQuestions && res.j.securityQuestions[0];
            montrerMfa(mfa === "totp" ? "Code de l'application d'authentification" : (typeof q === "string" ? q : "Réponse à votre question secrète"));
            dire(mfa === "totp" ? "Deuxième étape : saisissez le code de votre application d'authentification, puis reconnectez-vous." : "Deuxième étape : répondez à votre question secrète, puis reconnectez-vous.", "erreur");
            return;
          }
          if (!res.j.token) { dire("Réponse inattendue (" + res.statut + ") : aucun jeton dans la réponse.", "erreur"); return; }
          mfa = null; document.getElementById("cx-mfa-label").hidden = true;
          jeton = res.j.token;
          // Authorize : le jeton et l'établissement apparaissent dans le cadenas, et suivent chaque essai.
          try { ui.preauthorizeApiKey("bearerAuth", jeton); } catch (err) {}
          try { if (tenantChoisi) ui.preauthorizeApiKey("tenantHeader", tenantChoisi); } catch (err) {}
          try { ui.specActions.download(); } catch (err) {}
          dire("Connecté : " + (res.j.user && res.j.user.username) + " (" + (res.j.user && res.j.user.role) + ")" + (tenantChoisi ? ", établissement " + tenantChoisi : "") + ". Jeton placé dans Authorize ; la spécification se recharge.", "ok");
          document.getElementById("cx-pass").value = "";
        })
        .catch(function (err) { dire("Connexion impossible : " + err, "erreur"); });
    });
    document.getElementById("cx-whoami").addEventListener("click", function () {
      var entetes = {};
      if (jeton) entetes["Authorization"] = "Bearer " + jeton;
      if (tenantChoisi) entetes["X-Tenant-Id"] = tenantChoisi;
      fetch("/api/auth/whoami", { credentials: "include", headers: entetes })
        .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, statut: r.status, j: j }; }); })
        .then(function (res) {
          if (!res.ok) { dire("Non authentifié (" + res.statut + ") : connectez-vous d'abord.", "erreur"); return; }
          var u = res.j.utilisateur || {}; var l = res.j.locataire;
          dire("Vous êtes " + u.username + " (" + u.role + ", " + res.j.methode + ")" + (l ? " — " + l.name + " : modules " + (l.modules || []).join(", ") : " — compte système"), "ok");
        })
        .catch(function (err) { dire("Appel impossible : " + err, "erreur"); });
    });
    document.getElementById("cx-deconnecter").addEventListener("click", function () {
      jeton = null; tenantChoisi = "";
      try { ui.authActions.logout(["bearerAuth", "tenantHeader"]); } catch (err) {}
      dire("Jeton oublié : les essais reposent de nouveau sur la session du navigateur, s'il y en a une.");
    });
  </script>
</body>
</html>""";

    public static String csp(String nonce) {
        return String.join("; ", "default-src 'none'", "script-src " + CDN + " 'nonce-" + nonce + "'", "style-src " + CDN + " 'unsafe-inline'", "font-src " + CDN + " data:", "img-src 'self' data: https:", "connect-src 'self'",
                "form-action 'self'", "frame-ancestors 'none'", "base-uri 'none'");
    }

    public static String html(String specUrl, String nonce) {
        return GABARIT.replace("%CDN%", CDN).replace("%VERSION%", VERSION).replace("%NONCE%", nonce).replace("%SPEC%", "\"" + specUrl.replace("\"", "\\\"") + "\"");
    }
}
