package ai.attenso.courriel;

/**
 * L'envoi d'un courriel, réduit à ce dont ce produit a besoin.
 *
 * <p>Un seul usage aujourd'hui : le lien de récupération de mot de passe. Pas
 * de gabarits, pas de HTML, pas de suivi d'ouverture — un texte court, qui
 * passe les filtres et se lit sur un téléphone.
 */
public interface ServiceCourriel {

	/**
	 * @return vrai si le message est parti. Faux si l'envoi est éteint ou a
	 *         échoué : l'appelant ne doit PAS en déduire quoi dire à
	 *         l'utilisateur, sous peine de révéler quels comptes existent.
	 */
	boolean envoyer(String destinataire, String sujet, String corps);

	boolean actif();
}
