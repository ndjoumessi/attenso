package ai.attenso.web;

/**
 * Ce que la page du comptoir doit savoir avant que le client ne saisisse quoi
 * que ce soit : <strong>qui</strong> est responsable du traitement (EC-04).
 *
 * <p>Le responsable, c'est la clinique et non l'éditeur : c'est elle qui
 * décide de la finalité, elle qui détient la relation avec le client. Le nom
 * doit donc venir du serveur et non d'une constante — une mention
 * d'information qui nomme la mauvaise entité ne vaut rien.
 *
 * <p>Aucune autre donnée n'est exposée ici. Le nom d'une clinique est déjà
 * public : il est imprimé sur l'affiche qui porte le QR code.
 */
public record VueClinique(String nom) {
}
