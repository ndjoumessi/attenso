package ai.attenso.web;

import ai.attenso.config.ProprietesReseau;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Adresse de l'appelant, pour le limiteur de débit et le journal d'accès. */
@Component
public class AdresseAppelant {

	private final ProprietesReseau proprietes;

	public AdresseAppelant(ProprietesReseau proprietes) {
		this.proprietes = proprietes;
	}

	public String de(HttpServletRequest requete) {
		if (proprietes.derriereProxy()) {
			String transmise = requete.getHeader("X-Forwarded-For");
			if (transmise != null && !transmise.isBlank()) {
				return transmise.split(",")[0].trim();
			}
		}
		return requete.getRemoteAddr();
	}
}
