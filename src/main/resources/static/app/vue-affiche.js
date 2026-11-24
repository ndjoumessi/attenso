// Ce que l'affiche du comptoir dit, et comment elle l'écrit. Deux fonctions
// pures, parce que ce sont les deux seuls endroits de cette page où un choix
// se fait — le reste n'est que de la mise en page.

/**
 * La promesse faite au client sous le QR.
 *
 * Tant que l'expéditeur SMS de la clinique n'est pas validé auprès des
 * opérateurs, aucun message ne partira. L'affiche ne peut donc pas promettre
 * un SMS : elle promet ce qui marche de toute façon, le suivi sur le téléphone
 * du client. Mentir ici coûterait une salle d'attente de gens qui ont rangé
 * leur téléphone.
 */
export function promesse({ sms }) {
  return sms
    ? 'Vous recevrez un message quand votre tour approchera.'
    : 'Vous suivrez votre tour depuis votre téléphone.';
}

/**
 * L'adresse telle qu'on l'écrit sur du papier, pour qui ne peut pas scanner.
 *
 * `https://` saute : personne ne le tape, et il mange une ligne sur une
 * affiche. Ce qui n'est pas en HTTPS reste écrit en entier — c'est une adresse
 * de développement, et la cacher ferait croire à une vraie.
 */
export function urlLisible(url) {
  return url.replace(/^https:\/\//, '').replace(/\/+$/, '');
}
