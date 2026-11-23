package ai.attenso.web;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.config.ProprietesReseau;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Ces tests gardent le quota. Lire {@code X-Forwarded-For} du mauvais côté
 * rend le limiteur de débit décoratif : il suffit d'envoyer soi-même l'en-tête
 * pour se donner une adresse neuve à chaque requête.
 */
class AdresseAppelantTest {

	private static final String PAIR = "203.0.113.7";

	private MockHttpServletRequest requete(String entete) {
		MockHttpServletRequest requete = new MockHttpServletRequest();
		requete.setRemoteAddr(PAIR);
		if (entete != null) {
			requete.addHeader("X-Forwarded-For", entete);
		}
		return requete;
	}

	private AdresseAppelant derriereUnRelais() {
		return new AdresseAppelant(new ProprietesReseau(true, 1));
	}

	private AdresseAppelant sansRelais() {
		return new AdresseAppelant(new ProprietesReseau(false, 1));
	}

	@Test
	@DisplayName("sans proxy déclaré, l'en-tête est ignoré — sinon n'importe "
			+ "qui se forge une adresse")
	void sansProxyLEnteteEstIgnore() {
		assertThat(sansRelais().de(requete("1.2.3.4"))).isEqualTo(PAIR);
	}

	@Test
	@DisplayName("derrière un relais, on prend l'entrée de DROITE : c'est la "
			+ "seule que le client ne contrôle pas")
	void laDerniereEntreeEstLaSeuleFiable() {
		// Le client prétend venir de 1.2.3.4 ; notre relais ajoute, à droite,
		// l'adresse qu'il a réellement vue.
		assertThat(derriereUnRelais().de(requete("1.2.3.4, 198.51.100.9")))
				.isEqualTo("198.51.100.9");
	}

	@Test
	@DisplayName("une seule entrée est celle qu'a ajoutée le relais, donc on la prend")
	void uneSeuleEntreeVientDuRelais() {
		// Un client qui forge l'en-tête voit le relais en ajouter une seconde à
		// droite — c'est le test précédent qui couvre ce cas.
		assertThat(derriereUnRelais().de(requete("198.51.100.9")))
				.isEqualTo("198.51.100.9");
	}

	@Test
	@DisplayName("deux relais annoncés mais une seule entrée : on retombe sur le pair")
	void enteteTropCourteRetombeSurLePair() {
		AdresseAppelant deuxRelais = new AdresseAppelant(new ProprietesReseau(true, 2));
		assertThat(deuxRelais.de(requete("1.2.3.4"))).isEqualTo(PAIR);
	}

	@Test
	@DisplayName("en-tête absente ou vide : on retombe sur le pair")
	void enteteAbsenteOuVide() {
		assertThat(derriereUnRelais().de(requete(null))).isEqualTo(PAIR);
		assertThat(derriereUnRelais().de(requete("   "))).isEqualTo(PAIR);
		assertThat(derriereUnRelais().de(requete("1.2.3.4,  "))).isEqualTo(PAIR);
	}
}
