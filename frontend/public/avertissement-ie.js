// Internet Explorer n'exécute pas l'application : on y affiche l'avertissement, et rien d'autre. Ce script vit dans un
// fichier, et non dans la page, pour que la politique de sécurité du contenu (script-src 'self') n'ait aucune exception.
if (typeof document.documentMode === "number") {
  document.getElementById("ie-avertissement").removeAttribute("hidden");
}
