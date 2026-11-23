package ai.attenso.numero;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vérificateur par défaut : il ne vérifie rien de plus que le format, déjà
 * contrôlé en amont, et rend donc {@link Verdict#INDETERMINE}.
 *
 * <p>Il rend « indéterminé » et non « joignable », et la nuance compte : la
 * colonne {@code telephone_valide} doit dire ce qu'on sait, pas ce qu'on
 * espère. Sans HLR, on sait seulement que le numéro ressemble à un mobile
 * français.
 */
@Component
@ConditionalOnProperty(name = "attenso.hlr.verificateur", havingValue = "format",
		matchIfMissing = true)
public class VerificateurNumeroFormat implements VerificateurNumero {

	@Override
	public ResultatVerification verifier(String telephoneE164) {
		return ResultatVerification.indetermine("format seul, pas de vérification HLR");
	}
}
