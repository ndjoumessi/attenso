package ai.attenso.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NumeroTelephoneTest {

	@ParameterizedTest
	@CsvSource({
			"06 12 34 56 78, +33612345678",
			"0612345678,     +33612345678",
			"06.12.34.56.78, +33612345678",
			"06-12-34-56-78, +33612345678",
			"+33 6 12 34 56 78, +33612345678",
			"0033612345678,  +33612345678",
			"07 99 88 77 66, +33799887766",
	})
	@DisplayName("les écritures courantes d'un mobile français donnent le même E.164")
	void normalisation(String brut, String attendu) {
		assertThat(NumeroTelephone.normaliser(brut)).contains(attendu);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"01 42 00 00 00",   // fixe : ne reçoit pas de SMS
			"0512345678",       // fixe
			"061234567",        // trop court
			"06123456789",      // trop long
			"+4477009988776",   // hors France
			"pas un numero",
			"",
	})
	@DisplayName("tout ce qui n'est pas un mobile français est refusé — un SMS "
			+ "envoyé sur un fixe est payé et perdu")
	void refus(String brut) {
		assertThat(NumeroTelephone.normaliser(brut)).isEmpty();
	}

	@org.junit.jupiter.api.Test
	void nullEstRefuse() {
		assertThat(NumeroTelephone.normaliser(null)).isEmpty();
	}
}
