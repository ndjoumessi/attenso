package ai.attenso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base des liens « suivez votre tour » envoyés par SMS. */
@ConfigurationProperties("attenso.lien")
public record ProprietesLien(String base) {
}
