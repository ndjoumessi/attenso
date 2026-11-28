/**
 * Ce que la page fait d'un refus de l'API.
 *
 * <p>Un 401 ou un 403 n'est pas une panne : le serveur a compris la demande et
 * dit non, et il dira non de nouveau cinq minutes plus tard. Un rôle ACCUEIL
 * qui ouvre une vue réservée au gérant (la barre est peinte entière) ne doit
 * donc pas redemander. Un 500 ou un réseau tombé, eux, passent : relire est
 * alors exactement ce qu'il faut.
 *
 * <p>Ici plutôt que dans la page : c'est une règle, elle se teste.
 *
 * @param statut le code HTTP de l'échec, ou `undefined` si le réseau est tombé
 * @returns {boolean} `false` quand relire ne ferait que se faire refuser
 */
export function doitContinuerARelire(statut) {
  return statut !== 401 && statut !== 403;
}

/**
 * Le refus est-il un jeton expiré plutôt qu'un droit manquant ?
 *
 * <p>Les deux ne se relisent pas — `doitContinuerARelire` garde son sens pour
 * les deux — mais ils ne se DISENT pas pareil. Un 401 affiché en « Réservé au
 * gérant » fait croire à la personne qu'elle n'a pas les droits ; elle le dira
 * au gérant, qui ne comprendra pas. C'est la confusion des deux qui produisait
 * le mensonge, pendant les trente secondes qui séparent l'expiration de la
 * purge par `/api/file`.
 *
 * @param statut le code HTTP de l'échec, ou `undefined` si le réseau est tombé
 */
export function sessionExpiree(statut) {
  return statut === 401;
}
