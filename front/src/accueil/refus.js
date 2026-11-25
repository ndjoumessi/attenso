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
