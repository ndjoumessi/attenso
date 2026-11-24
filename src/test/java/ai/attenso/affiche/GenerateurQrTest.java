package ai.attenso.affiche;

import static org.assertj.core.api.Assertions.assertThat;

import ai.attenso.inscription.Slug;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ce que ces tests établissent, et qu'aucune relecture ne peut établir : le
 * tracé produit est bien un QR code, et il dit bien l'URL de départ.
 */
class GenerateurQrTest {

	/** Ce que porte vraiment une affiche : base de lien, {@code /t/}, slug. */
	private static final String URL_COMPTOIR = "https://attenso.fr/t/cerisiers";

	private final GenerateurQr generateur = new GenerateurQr();

	@Test
	@DisplayName("le tracé produit se décode et rend l'URL de départ")
	void leTraceSeRelitEtRendLUrl() throws Exception {
		assertThat(LecteurQr.lire(generateur.encoder(URL_COMPTOIR))).isEqualTo(URL_COMPTOIR);
	}

	@Test
	@DisplayName("la redondance Q ne coûte aucun module de plus que M")
	void laRedondanceQNeCoutePasDeModules() throws Exception {
		assertThat(cote(ErrorCorrectionLevel.Q)).isEqualTo(cote(ErrorCorrectionLevel.M));
		// Et le niveau H, lui, le coûterait : c'est ce qui rend le choix de Q
		// gratuit au lieu d'arbitraire.
		assertThat(cote(ErrorCorrectionLevel.H)).isGreaterThan(cote(ErrorCorrectionLevel.Q));
	}

	@Test
	@DisplayName("la zone de silence fait quatre modules clairs sur les quatre bords")
	void laZoneDeSilenceFaitQuatreModules() {
		BitMatrix grille = LecteurQr.grille(generateur.encoder(URL_COMPTOIR));
		int cote = grille.getWidth();

		for (int i = 0; i < cote; i++) {
			for (int marge = 0; marge < 4; marge++) {
				assertThat(grille.get(i, marge)).as("haut %d,%d", i, marge).isFalse();
				assertThat(grille.get(i, cote - 1 - marge)).as("bas %d,%d", i, marge).isFalse();
				assertThat(grille.get(marge, i)).as("gauche %d,%d", marge, i).isFalse();
				assertThat(grille.get(cote - 1 - marge, i)).as("droite %d,%d", marge, i)
						.isFalse();
			}
		}
	}

	@Test
	@DisplayName("le slug le plus long que l'inscription puisse fabriquer se décode encore")
	void leSlugLePlusLongSeDecodeEncore() throws Exception {
		String url = "https://attenso.fr/t/" + "c".repeat(Slug.LONGUEUR_MAX);

		assertThat(LecteurQr.lire(generateur.encoder(url))).isEqualTo(url);
	}

	private static int cote(ErrorCorrectionLevel niveau) throws Exception {
		return new QRCodeWriter().encode(URL_COMPTOIR, BarcodeFormat.QR_CODE, 1, 1,
				Map.of(EncodeHintType.ERROR_CORRECTION, niveau)).getWidth();
	}
}
