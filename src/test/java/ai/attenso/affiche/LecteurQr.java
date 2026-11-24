package ai.attenso.affiche;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.Binarizer;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relit un {@link Qr} comme le ferait un téléphone, pour les tests.
 *
 * <p>Un QR faux ne se voit pas : il est noir et carré comme un vrai, et c'est
 * au comptoir, le téléphone d'un client à la main, qu'on l'apprendrait. Tout
 * test qui produit une affiche passe donc par ici — le tracé, pas la grille
 * intermédiaire, parce que c'est le tracé qui part sur le réseau.
 */
public final class LecteurQr {

	private static final Pattern SUITE = Pattern.compile("M(\\d+) (\\d+)h(\\d+)v1h-\\d+z");

	private LecteurQr() {
	}

	/** Le texte encodé, ou une exception si rien ne se décode. */
	public static String lire(Qr qr) throws Exception {
		return decoder(grille(qr));
	}

	/**
	 * Reconstruit la grille à partir du tracé. C'est l'inverse exact de ce
	 * qu'écrit le générateur : un décalage d'un module dans l'émission des
	 * suites se verrait ici, et le décodage échouerait.
	 */
	public static BitMatrix grille(Qr qr) {
		BitMatrix grille = new BitMatrix(qr.cote(), qr.cote());
		Matcher suites = SUITE.matcher(qr.chemin());
		while (suites.find()) {
			int x = Integer.parseInt(suites.group(1));
			int y = Integer.parseInt(suites.group(2));
			for (int largeur = Integer.parseInt(suites.group(3)); largeur > 0; largeur--) {
				grille.set(x++, y);
			}
		}
		// Un tracé que la relecture n'épuise pas voudrait dire que le générateur
		// écrit une forme que ce lecteur ne comprend pas, et les assertions
		// porteraient alors sur une grille muette.
		assertThat(qr.chemin()).isNotEmpty();
		assertThat(qr.chemin().replaceAll(SUITE.pattern(), "")).as("tracé intégralement relu")
				.isEmpty();
		return grille;
	}

	private static String decoder(BitMatrix grille) throws Exception {
		// PURE_BARCODE : la grille vient d'un tracé, pas d'une photo. Sans cet
		// indice, zxing cherche les motifs de repérage à l'échelle d'une image
		// prise de travers et ne les trouve pas sur un module d'un pixel.
		return new QRCodeReader().decode(new BinaryBitmap(new GrilleToutePrete(grille)),
				Map.of(DecodeHintType.PURE_BARCODE, true)).getText();
	}

	/**
	 * Donne la grille au lecteur sans passer par une image.
	 *
	 * <p>Binariser, c'est deviner le noir et le blanc dans une photo ; ici il
	 * n'y a pas de photo, et ce n'est pas ce qu'on teste. Ce qu'il faut
	 * établir, c'est que les modules sont les bons — pas que zxing sait lire un
	 * cliché pris de biais.
	 */
	private static final class GrilleToutePrete extends Binarizer {

		private final BitMatrix grille;

		private GrilleToutePrete(BitMatrix grille) {
			super(null);
			this.grille = grille;
		}

		@Override
		public BitMatrix getBlackMatrix() {
			return grille;
		}

		@Override
		public BitArray getBlackRow(int y, BitArray ligne) throws NotFoundException {
			return grille.getRow(y, ligne);
		}

		@Override
		public Binarizer createBinarizer(LuminanceSource source) {
			return this;
		}
	}
}
