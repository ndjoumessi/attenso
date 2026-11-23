package ai.attenso.notification;

/** Les trois seuls messages du V0, et chacun plafonné à un par ticket. */
public enum TypeNotification {

	/** À l'enregistrement : ton numéro et ton lien. */
	TICKET,

	/** Sous le seuil de prévenance : « revenez ». */
	PREVENIR,

	/** Une urgence est passée devant : « comptez X min de plus ». */
	CORRECTIF
}
