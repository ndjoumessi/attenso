package ai.attenso.securite;

import ai.attenso.config.ProprietesJeton;
import ai.attenso.utilisateur.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Jeton du personnel de la clinique. Porté par l'en-tête {@code Authorization},
 * jamais par une URL : contrairement au lien client, il n'a aucune contrainte
 * de longueur.
 */
@Component
public class JetonPersonnel {

	private static final String EMETTEUR = "attenso";

	private static final String CLAIM_TENANT = "tid";

	private static final String CLAIM_ROLE = "rol";

	private final JwtEncoder encodeur;

	private final JwtDecoder decodeur;

	private final ProprietesJeton proprietes;

	private final Clock horloge;

	public JetonPersonnel(JwtEncoder encodeur, JwtDecoder decodeur,
			ProprietesJeton proprietes, Clock horloge) {
		this.encodeur = encodeur;
		this.decodeur = decodeur;
		this.proprietes = proprietes;
		this.horloge = horloge;
	}

	public String emettre(UUID utilisateurId, UUID tenantId, Role role) {
		Instant maintenant = horloge.instant();
		JwtClaimsSet revendications = JwtClaimsSet.builder()
				.issuer(EMETTEUR)
				.subject(utilisateurId.toString())
				.claim(CLAIM_TENANT, tenantId.toString())
				.claim(CLAIM_ROLE, role.name())
				.issuedAt(maintenant)
				.expiresAt(maintenant.plus(proprietes.dureePersonnel()))
				.build();
		return encodeur.encode(JwtEncoderParameters.from(
				JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
						.build(),
				revendications)).getTokenValue();
	}

	/** Rend le principal, ou vide si le jeton est absent, expiré ou falsifié. */
	public PrincipalPersonnel lire(String jeton) {
		try {
			Jwt decode = decodeur.decode(jeton);
			// getClaimAsString et non getIssuer() : ce dernier rend une URL, et
			// « attenso » n'en est pas une — il lève, et le jeton valide serait
			// rejeté.
			if (!EMETTEUR.equals(decode.getClaimAsString(JwtClaimNames.ISS))) {
				return null;
			}
			return new PrincipalPersonnel(
					UUID.fromString(decode.getSubject()),
					UUID.fromString(decode.getClaimAsString(CLAIM_TENANT)),
					Role.valueOf(decode.getClaimAsString(CLAIM_ROLE)),
					decode.getIssuedAt());
		}
		catch (JwtException | IllegalArgumentException | NullPointerException invalide) {
			return null;
		}
	}
}
