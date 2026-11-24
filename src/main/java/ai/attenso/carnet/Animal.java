package ai.attenso.carnet;

import java.time.Instant;
import java.util.UUID;

/**
 * @param jetonCarnet {@code null} quand aucun QR n'a été émis, ou quand il a
 *        été révoqué. Les deux situations se traitent pareil : il n'y a pas de
 *        carnet à ouvrir.
 */
public record Animal(UUID id, String nom, String espece, String telephoneE164,
		String jetonCarnet, Instant jetonPoseLe, boolean actif) {

	public boolean carnetOuvert() {
		return actif && jetonCarnet != null;
	}
}
