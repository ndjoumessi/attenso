package ai.attenso.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param expediteur    nom du bean d'envoi : {@code journal} en développement.
 * @param coutCentimesParSegment coût unitaire réel, reporté dans
 *        {@code notification.cout_centimes}. La marge du modèle économique se
 *        mesure sur cette colonne, pas sur une hypothèse de tableur.
 */
@ConfigurationProperties("attenso.sms")
public record ProprietesSms(String expediteur, BigDecimal coutCentimesParSegment) {
}
