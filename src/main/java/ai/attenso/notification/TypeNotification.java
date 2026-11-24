package ai.attenso.notification;

/**
 * Les messages du produit, chacun plafonné à un par objet notifié.
 *
 * <p>Le plafond est une contrainte d'unicité en base — index
 * {@code notification_plafond} pour les tickets, {@code notification_plafond_rdv}
 * pour les rendez-vous — et non un {@code if} applicatif : c'est l'exigence
 * EF-303, et c'est la seule façon de la tenir quand deux instances tournent.
 */
public enum TypeNotification {

	/** À l'enregistrement : ton numéro et ton lien. */
	TICKET,

	/** Sous le seuil de prévenance : « revenez ». */
	PREVENIR,

	/** Une urgence est passée devant : « comptez X min de plus ». */
	CORRECTIF,

	/** La veille d'un rendez-vous (EF-905). */
	RAPPEL_RDV,

	/** Un rappel de vaccin arrive à échéance (EF-906). */
	RAPPEL_VACCIN
}
