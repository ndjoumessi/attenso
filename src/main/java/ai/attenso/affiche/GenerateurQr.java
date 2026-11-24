package ai.attenso.affiche;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Encode une URL en QR code vectoriel (EF-101).
 *
 * <p>Vectoriel et non matriciel : l'affiche s'imprime, et une image en pixels
 * demanderait de choisir une résolution ici — trop basse, les modules bavent
 * sous l'encre ; trop haute, on envoie des centaines de kilo-octets pour une
 * figure faite de carrés noirs. Un tracé ne porte que la grille, et c'est
 * l'imprimante qui décide de la finesse.
 */
@Component
public class GenerateurQr {

	/**
	 * Niveau Q : 25 % de redondance.
	 *
	 * <p>Le choix est gratuit ici, et c'est ce qui le rend évident. L'URL du
	 * comptoir fait une trentaine de caractères ; aux niveaux M comme Q, elle
	 * tient dans la <strong>même</strong> version de symbole — donc dans le même
	 * nombre de modules, donc des carrés de la même taille sur le papier. Autant
	 * prendre la redondance la plus haute des deux. {@code GenerateurQrTest} le
	 * vérifie plutôt que de le croire.
	 *
	 * <p>Ce qui suit n'est pas théorique : une affiche de comptoir prend des
	 * traces de doigts, un reflet de néon, un coin qui se décolle.
	 */
	private static final ErrorCorrectionLevel REDONDANCE = ErrorCorrectionLevel.Q;

	/**
	 * Zone de silence, en modules. Quatre, c'est ce que la norme ISO/IEC 18004
	 * exige : sans elle, un lecteur ne trouve pas les bords du symbole.
	 */
	private static final int SILENCE = 4;

	/**
	 * Rend le QR d'une URL, à l'échelle d'un module par unité.
	 *
	 * <p>Aucune dimension en pixels ni en millimètres : le tracé est en modules
	 * et c'est la feuille de style de l'affiche qui l'étire, puisque c'est elle
	 * qui sait qu'elle imprime du A4.
	 */
	public Qr encoder(String url) {
		BitMatrix grille = grille(url);
		StringBuilder chemin = new StringBuilder();
		for (int y = 0; y < grille.getHeight(); y++) {
			int x = 0;
			while (x < grille.getWidth()) {
				if (!grille.get(x, y)) {
					x++;
					continue;
				}
				int debut = x;
				while (x < grille.getWidth() && grille.get(x, y)) {
					x++;
				}
				// Une passe par SUITE de modules noirs, et non par module : sur
				// un symbole de 29 modules de côté, cela divise par trois la
				// taille du chemin, et deux carrés voisins décrits séparément
				// laissent parfois voir un cheveu blanc entre eux à l'impression.
				chemin.append('M').append(debut).append(' ').append(y)
						.append('h').append(x - debut).append("v1h-").append(x - debut)
						.append('z');
			}
		}
		return new Qr(grille.getWidth(), chemin.toString());
	}

	/**
	 * La grille de modules, zone de silence comprise, sans mise à l'échelle.
	 *
	 * <p>Le 1 × 1 demandé n'est pas une erreur : {@code QRCodeWriter} agrandit
	 * jusqu'à la taille voulue mais ne réduit jamais en dessous du symbole, donc
	 * une demande d'un pixel rend exactement un module par cellule. C'est la
	 * seule façon d'obtenir la grille brute sans recopier son encodeur.
	 */
	private BitMatrix grille(String url) {
		try {
			return new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 1, 1,
					Map.of(EncodeHintType.ERROR_CORRECTION, REDONDANCE,
							EncodeHintType.MARGIN, SILENCE));
		} catch (WriterException echec) {
			// Sauf URL démesurée, inatteignable : un slug est borné à 40
			// caractères et la base de lien à un nom de domaine.
			throw new IllegalArgumentException("URL inencodable en QR : " + url, echec);
		}
	}
}
