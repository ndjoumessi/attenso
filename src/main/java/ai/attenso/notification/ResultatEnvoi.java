package ai.attenso.notification;

import java.math.BigDecimal;

/**
 * @param coutCentimes     notre estimation : segments × tarif négocié. C'est le
 *                         chiffre sur lequel le modèle économique raisonne.
 * @param coutFournisseur  le coût brut renvoyé par l'agrégateur, tel quel.
 *                         Conservé à côté et non à la place : l'unité du champ
 *                         n'est pas documentée, et seule la première facture
 *                         réelle tranchera.
 */
public record ResultatEnvoi(boolean reussi, String fournisseurId, BigDecimal coutCentimes,
		BigDecimal coutFournisseur, String erreur) {

	public static ResultatEnvoi reussi(String fournisseurId, BigDecimal coutCentimes,
			BigDecimal coutFournisseur) {
		return new ResultatEnvoi(true, fournisseurId, coutCentimes, coutFournisseur, null);
	}

	public static ResultatEnvoi echec(String erreur) {
		return new ResultatEnvoi(false, null, null, null, erreur);
	}
}
