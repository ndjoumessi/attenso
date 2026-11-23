package ai.attenso.notification;

public enum Canal {

	SMS,

	/**
	 * Hors périmètre V0. Le push web n'atteint pas un iPhone dont la page
	 * n'est pas ajoutée à l'écran d'accueil, ce qui est le cas de presque tous
	 * les clients : c'est pour ça que le V0 paie des SMS.
	 */
	PUSH
}
