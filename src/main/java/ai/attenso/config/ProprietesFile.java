package ai.attenso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("attenso.file")
public record ProprietesFile(long periodeTick, int ticketsMaxParNumero) {
}
