package ai.attenso.securite;

import ai.attenso.tenant.ContexteTenant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lit le jeton du personnel et lie le tenant au fil de la requête.
 *
 * <p>Le déliement dans le {@code finally} n'est pas une précaution de style :
 * le fil est recyclé par le conteneur, et un tenant resté lié ferait voir à la
 * requête suivante les lignes d'une autre clinique.
 */
@Component
public class FiltreJetonPersonnel extends OncePerRequestFilter {

	private static final String PREFIXE = "Bearer ";

	private final JetonPersonnel jetons;

	private final SessionsRevoquees revocations;

	public FiltreJetonPersonnel(JetonPersonnel jetons, SessionsRevoquees revocations) {
		this.jetons = jetons;
		this.revocations = revocations;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
			FilterChain suite) throws ServletException, IOException {
		String entete = requete.getHeader("Authorization");
		PrincipalPersonnel principal = (entete != null && entete.startsWith(PREFIXE))
				? jetons.lire(entete.substring(PREFIXE.length()))
				: null;
		try {
			if (principal != null) {
				// Le tenant est lié AVANT la vérification : la table
				// « utilisateur » est soumise au RLS, donc une lecture sans
				// liage ne verrait rien et révoquerait tout le monde.
				ContexteTenant.lier(principal.tenantId());
				if (revocations.revoque(principal.utilisateurId(), principal.emisLe())) {
					// Jeton plus ancien que le dernier changement de mot de
					// passe : la requête continue SANS authentification, et la
					// chaîne répondra 401. Rien à expliquer de plus au porteur.
					ContexteTenant.delier();
				}
				else {
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null,
									List.of(new SimpleGrantedAuthority(
											principal.role().autorite()))));
				}
			}
			suite.doFilter(requete, reponse);
		}
		finally {
			ContexteTenant.delier();
			SecurityContextHolder.clearContext();
		}
	}
}
