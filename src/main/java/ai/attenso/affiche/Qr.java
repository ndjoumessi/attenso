package ai.attenso.affiche;

/**
 * Un QR code réduit à ce qu'il faut pour le dessiner : la taille de la grille
 * et le tracé de ses modules noirs.
 *
 * <p>Pas de balise {@code <svg>} ici, et c'est délibéré. Le serveur pourrait
 * envoyer le document tout fait, mais la page devrait alors l'analyser comme du
 * balisage pour l'insérer. Un entier et un tracé, eux, ne sont que des données
 * : la page les pose en attributs, et rien de ce que le serveur dit n'est
 * interprété comme de la structure. Le gain est petit, le coût nul.
 *
 * @param cote nombre de modules sur un côté, zone de silence comprise
 * @param chemin le {@code d} d'un {@code <path>} SVG, en unités de module
 */
public record Qr(int cote, String chemin) {
}
