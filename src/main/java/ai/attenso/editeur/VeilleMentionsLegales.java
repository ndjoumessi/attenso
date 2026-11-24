package ai.attenso.editeur;

import ai.attenso.paiement.ProprietesPaiement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dit au démarrage si l'on vend sans mentions légales complètes.
 *
 * <p>Le niveau dépend de ce qui est branché, et c'est tout l'intérêt : un
 * serveur de développement sans identité d'éditeur est normal et ne mérite
 * qu'une ligne d'information. Un serveur où la souscription payante est
 * configurée, en revanche, ENCAISSE — et vendre en ligne sans mentions
 * légales ni politique de confidentialité complètes n'est pas une négligence
 * d'affichage, c'est une infraction. D'où l'ERROR, réservée au cas où elle
 * veut dire quelque chose.
 */
@Component
public class VeilleMentionsLegales {

	private static final Logger JOURNAL =
			LoggerFactory.getLogger(VeilleMentionsLegales.class);

	public VeilleMentionsLegales(ProprietesEditeur editeur, ProprietesPaiement paiement) {
		if (editeur.complet()) {
			JOURNAL.info("mentions légales complètes");
			return;
		}
		if (paiement.complete()) {
			JOURNAL.error("LA SOUSCRIPTION EST OUVERTE MAIS LES MENTIONS LÉGALES SONT "
					+ "INCOMPLÈTES : il manque {}. Vendre en ligne sans ces "
					+ "informations est une infraction.", editeur.manquants());
			return;
		}
		JOURNAL.info("mentions légales incomplètes ({}) ; sans effet tant que la "
				+ "souscription n'est pas ouverte", editeur.manquants());
	}
}
