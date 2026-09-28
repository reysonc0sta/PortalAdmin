package com.mycompany.myapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Portal Admin.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    private final Whatsapp whatsapp = new Whatsapp();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public Whatsapp getWhatsapp() {
        return whatsapp;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    public static class Whatsapp {

        private String evolutionUrl = "http://localhost:8085";

        private String evolutionApiKey;

        private String instancePrefix = "portaladmin_";

        private int maxNumbersPerSend = 100;

        // Pausa entre destinatários para reduzir o risco de bloqueio do número pelo WhatsApp.
        private long delayBetweenMessagesMs = 1500;

        public String getEvolutionUrl() {
            return evolutionUrl;
        }

        public void setEvolutionUrl(String evolutionUrl) {
            this.evolutionUrl = evolutionUrl;
        }

        public String getEvolutionApiKey() {
            return evolutionApiKey;
        }

        public void setEvolutionApiKey(String evolutionApiKey) {
            this.evolutionApiKey = evolutionApiKey;
        }

        public String getInstancePrefix() {
            return instancePrefix;
        }

        public void setInstancePrefix(String instancePrefix) {
            this.instancePrefix = instancePrefix;
        }

        public int getMaxNumbersPerSend() {
            return maxNumbersPerSend;
        }

        public void setMaxNumbersPerSend(int maxNumbersPerSend) {
            this.maxNumbersPerSend = maxNumbersPerSend;
        }

        public long getDelayBetweenMessagesMs() {
            return delayBetweenMessagesMs;
        }

        public void setDelayBetweenMessagesMs(long delayBetweenMessagesMs) {
            this.delayBetweenMessagesMs = delayBetweenMessagesMs;
        }
    }

    // jhipster-needle-application-properties-property-class
}
