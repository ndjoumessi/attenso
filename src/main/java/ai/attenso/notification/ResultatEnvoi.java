package ai.attenso.notification;

import java.math.BigDecimal;

public record ResultatEnvoi(boolean reussi, String fournisseurId, BigDecimal coutCentimes,
		String erreur) {

	public static ResultatEnvoi reussi(String fournisseurId, BigDecimal cout) {
		return new ResultatEnvoi(true, fournisseurId, cout, null);
	}

	public static ResultatEnvoi echec(String erreur) {
		return new ResultatEnvoi(false, null, null, erreur);
	}
}
