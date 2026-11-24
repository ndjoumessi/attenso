package ai.attenso.web;

import ai.attenso.affiche.Qr;

/**
 * Ce qu'il faut pour imprimer l'affiche du comptoir (EF-101).
 *
 * @param nom le nom de la clinique, imprimé en haut : l'affiche sert aussi à
 *        dire au client qu'il est au bon endroit
 * @param url l'adresse encodée dans le QR, écrite en clair sous lui — un
 *        téléphone qui ne scanne pas, ça existe, et il faut pouvoir la taper
 * @param sms si cette clinique peut réellement envoyer des SMS. L'affiche
 *        promet d'être prévenu par message ; tant que l'expéditeur n'est pas
 *        validé auprès des opérateurs, elle ne doit pas le promettre. Une
 *        affiche qui ment est pire qu'une affiche moins engageante : le client
 *        range son téléphone et attend un message qui ne viendra pas.
 * @param qr la grille du QR et son tracé
 */
public record VueAffiche(String nom, String url, boolean sms, Qr qr) {
}
