package ai.attenso.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NomExpediteurTest {

	@ParameterizedTest
	@ValueSource(strings = { "Cerisiers", "MonVeto", "ABC", "Vet12345678" })
	@DisplayName("3 à 11 caractères alphanumériques passent")
	void formesValides(String nom) {
		assertThat(NomExpediteur.valide(nom)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"AB",                // trop court
			"Veterinaires12",    // trop long
			"Cerisiers!",        // ponctuation
			"Mon Veto",          // espace
			"Cérisiers",         // accent
			"",
	})
	@DisplayName("tout le reste est refusé ICI, parce que l'API le « corrigerait » "
			+ "silencieusement au lieu de le refuser")
	void formesInvalides(String nom) {
		assertThat(NomExpediteur.valide(nom)).isFalse();
	}

	@Test
	void nullEstInvalide() {
		assertThat(NomExpediteur.valide(null)).isFalse();
	}

	@Test
	@DisplayName("la proposition retient le mot distinctif, pas « Clinique »")
	void propositionDepuisLaRaisonSociale() {
		assertThat(NomExpediteur.proposerDepuis("Clinique des Cerisiers")).isEqualTo("Cerisiers");
		assertThat(NomExpediteur.proposerDepuis("Clinique Vétérinaire Novavet"))
				.isEqualTo("Novavet");
		assertThat(NomExpediteur.proposerDepuis("Cabinet du Docteur Marchand"))
				.isEqualTo("Marchand");
	}

	@Test
	@DisplayName("une proposition trop longue est tronquée, une impossible rend null")
	void bornesDeLaProposition() {
		assertThat(NomExpediteur.proposerDepuis("Clinique Transfiguration"))
				.isEqualTo("Transfigura");
		assertThat(NomExpediteur.proposerDepuis("Clinique Vétérinaire")).isNull();
		assertThat(NomExpediteur.proposerDepuis(null)).isNull();
	}
}
