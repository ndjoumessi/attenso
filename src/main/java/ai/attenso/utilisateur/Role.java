package ai.attenso.utilisateur;

public enum Role {

	/** L'ASV : fait avancer la file toute la journée. C'est l'utilisatrice. */
	ACCUEIL,

	/** Le gérant : signe l'abonnement et lit le compteur de départs. C'est l'acheteur. */
	GERANT;

	public String autorite() {
		return "ROLE_" + name();
	}
}
