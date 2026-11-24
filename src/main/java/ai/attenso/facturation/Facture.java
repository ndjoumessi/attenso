package ai.attenso.facturation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une facture mensuelle (EF-902).
 *
 * @param coutSmsCentimes ce que les SMS de la période ont réellement coûté.
 *        Stocké à côté du prix et non déduit : c'est la marge brute du §13, et
 *        c'est le chiffre qui dira si le modèle « SMS inclus » tient.
 */
public record Facture(UUID id, String numero, LocalDate periode, Palier palier, int visites,
		int visitesIncluses, int abonnementCentimes, int depassementCentimes,
		int totalCentimes, BigDecimal coutSmsCentimes, Instant emiseLe) {

	/** Marge brute de la période, en centimes. Négative = le palier est mal placé. */
	public BigDecimal margeCentimes() {
		return BigDecimal.valueOf(totalCentimes).subtract(coutSmsCentimes);
	}
}
