package ai.attenso.mesure;

import java.time.LocalDate;

/**
 * Les chiffres d'une journée de pilote.
 *
 * @param arrivantsTotal   saisi par l'accueil. {@code null} tant qu'il ne l'a
 *                         pas été : un dénominateur absent n'est pas un zéro,
 *                         et afficher 0 % ferait croire à un échec là où il n'y
 *                         a qu'une saisie manquante.
 * @param ticketsComptoir  tickets créés par le client lui-même.
 * @param ticketsAccueil   tickets saisis par l'ASV pour quelqu'un sans
 *                         smartphone. Ils comptent dans les visites, pas dans
 *                         le numérateur du taux.
 * @param departs          départs sans consultation : le chiffre qui justifie
 *                         l'abonnement.
 */
public record MesureJour(LocalDate jour, Integer arrivantsTotal, long ticketsComptoir,
		long ticketsAccueil, long departs) {

	public long ticketsTotal() {
		return ticketsComptoir + ticketsAccueil;
	}

	/**
	 * Taux d'auto-enregistrement, en pourcentage, ou {@code null} si l'accueil
	 * n'a pas encore saisi le nombre d'arrivants.
	 *
	 * <p>Le numérateur ne retient que les tickets du comptoir : un ticket saisi
	 * par l'ASV prouve précisément que le client ne s'est PAS enregistré seul.
	 * Les compter gonflerait le taux et invaliderait la mesure.
	 */
	public Integer tauxAutoEnregistrement() {
		if (arrivantsTotal == null || arrivantsTotal == 0) {
			return null;
		}
		return (int) Math.round(100.0 * ticketsComptoir / arrivantsTotal);
	}

	/** Le verdict du cahier des charges, §12. */
	public String zone() {
		Integer taux = tauxAutoEnregistrement();
		if (taux == null) {
			return "NON_MESURE";
		}
		if (taux < 30) {
			return "INVALIDE";
		}
		return taux < 50 ? "ZONE_GRISE" : "VALIDE";
	}
}
