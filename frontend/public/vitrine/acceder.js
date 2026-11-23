/*
 * Le choix de l'organisme (ADR 0094) : la liste vient de l'API (`/api/auth/organismes`), c'est-à-dire des organismes que
 * le superadmin crée et gère, ouverts et pourvus d'un sous-domaine. Choisir mène à la connexion de l'organisme, sur son
 * hôte : seule une adresse rendue par le serveur est suivie, jamais une valeur forgée dans la page.
 */
(function () {
  "use strict";
  var MESSAGE_INDISPONIBLE = "La liste des organismes n'est pas disponible pour le moment. Les comptes d'un organisme se connectent aussi directement sur l'adresse reçue de leur organisme.";
  var MESSAGE_AUCUN = "Aucun organisme n'est ouvert pour le moment.";
  var MESSAGE_CHOISIR = "Choisissez un organisme dans la liste, puis « Accéder à la connexion ».";

  function initialiser() {
    var formulaire = document.getElementById("acceder-form");
    var liste = document.getElementById("acceder-organisme");
    var adresse = document.getElementById("acceder-adresse");
    var etat = document.getElementById("acceder-etat");
    var connues = {};

    function dire(texte, estErreur) {
      etat.textContent = texte;
      etat.className = estErreur ? "erreur" : "";
      etat.setAttribute("role", estErreur ? "alert" : "status");
    }

    function remplir(organismes) {
      liste.textContent = "";
      var invite = new Option(organismes.length ? "— Choisir votre organisme —" : "Aucun organisme", "");
      liste.appendChild(invite);
      organismes.forEach(function (o) {
        connues[o.adresse] = true;
        liste.appendChild(new Option(o.nom, o.adresse));
      });
      liste.disabled = organismes.length === 0;
      if (!organismes.length) dire(MESSAGE_AUCUN, false);
    }

    liste.addEventListener("change", function () {
      adresse.textContent = liste.value ? new URL(liste.value).host : "";
      dire("", false);
    });

    formulaire.addEventListener("submit", function (e) {
      e.preventDefault();
      if (!liste.value || !connues[liste.value]) {
        dire(MESSAGE_CHOISIR, true);
        liste.focus();
        return;
      }
      window.location.assign(liste.value);
    });

    fetch("/api/auth/organismes", { headers: { Accept: "application/json" } })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (l) { remplir(Array.isArray(l) ? l.filter(function (o) { return o && o.nom && /^https?:\/\//.test(o.adresse); }) : []); })
      .catch(function () {
        liste.textContent = "";
        liste.appendChild(new Option("Liste indisponible", ""));
        dire(MESSAGE_INDISPONIBLE, false);
      });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initialiser);
  else initialiser();
})();
