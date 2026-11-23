package ai.attenso.numero;

/**
 * Ce que la vérification HLR nous apprend d'un numéro.
 *
 * <p>Trois valeurs et non deux, et c'est le cœur de la décision. Les deux
 * erreurs possibles n'ont pas du tout le même prix :
 * <ul>
 *   <li>refuser à tort renvoie du comptoir un client qui est venu — coût :
 *       le client, et la confiance de la clinique dans l'outil ;</li>
 *   <li>accepter à tort coûte 4,5 centimes de SMS perdu.</li>
 * </ul>
 * On ne refuse donc que sur un négatif <strong>certain</strong>, et tout le
 * reste — ligne injoignable, opérateur muet, réponse ambiguë, panne de
 * l'agrégateur — passe en {@link #INDETERMINE}, qui laisse entrer.
 */
public enum Verdict {

	/** La ligne existe et reçoit des SMS. */
	JOIGNABLE,

	/**
	 * La ligne ne recevra pas de SMS, et c'est établi : numéro mort, fixe,
	 * format invalide, ou SIM sans service de messagerie.
	 */
	INJOIGNABLE,

	/**
	 * On ne sait pas. Téléphone éteint, hors couverture, opérateur
	 * indisponible, HLR désactivé ou en panne. <strong>On laisse entrer.</strong>
	 */
	INDETERMINE
}
