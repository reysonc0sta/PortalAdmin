package com.mycompany.myapp.service;

import com.mycompany.myapp.config.ApplicationProperties;
import com.mycompany.myapp.service.dto.WhatsappSendResultDTO;
import com.mycompany.myapp.service.dto.WhatsappStatusDTO;
import com.mycompany.myapp.web.rest.errors.WhatsappException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends WhatsApp messages through the Evolution API. Each portal user owns a separate Evolution
 * instance, so every login pairs and sends from its own phone number.
 */
@Service
public class WhatsappService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappService.class);

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private static final Set<String> CONNECTED_STATES = Set.of("open", "connected");

    private final ApplicationProperties.Whatsapp properties;

    private final RestClient restClient;

    public WhatsappService(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getWhatsapp();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
            .baseUrl(stripTrailingSlash(properties.getEvolutionUrl()))
            .requestFactory(requestFactory)
            .defaultHeader("apikey", Optional.ofNullable(properties.getEvolutionApiKey()).orElse(""))
            .build();
    }

    public String instanceName(Long userId) {
        return properties.getInstancePrefix() + "user_" + userId;
    }

    public WhatsappStatusDTO getStatus(String instanceName) {
        String state = fetchState(instanceName);
        return new WhatsappStatusDTO(instanceName, state, isConnected(state), null, null);
    }

    /**
     * Creates the instance when needed and returns the current QR Code. With {@code forceNew} the
     * previous session is dropped, which is how a user switches to another phone number.
     */
    public WhatsappStatusDTO connect(String instanceName, boolean forceNew) {
        String state = fetchState(instanceName);
        if (isConnected(state) && !forceNew) {
            return new WhatsappStatusDTO(instanceName, state, true, null, null);
        }

        Map<String, Object> pairing = null;
        if (forceNew) {
            deleteInstance(instanceName);
            pairing = createInstance(instanceName);
        } else if (!instanceExists(instanceName)) {
            pairing = createInstance(instanceName);
        }

        String qrCode = extractQrCode(pairing);
        if (qrCode == null) {
            pairing = waitForQrCode(instanceName);
            qrCode = extractQrCode(pairing);
        }

        state = fetchState(instanceName);
        if (isConnected(state)) {
            return new WhatsappStatusDTO(instanceName, state, true, null, null);
        }
        return new WhatsappStatusDTO(instanceName, state, false, qrCode, extractPairingCode(pairing));
    }

    public WhatsappSendResultDTO sendText(String instanceName, List<String> rawNumbers, String text) {
        Map<String, String> numbers = normalizeNumbers(rawNumbers);
        if (numbers.isEmpty()) {
            throw new WhatsappException(HttpStatus.BAD_REQUEST, "noNumbers", "Informe pelo menos um número de telefone.");
        }
        if (numbers.size() > properties.getMaxNumbersPerSend()) {
            throw new WhatsappException(
                HttpStatus.BAD_REQUEST,
                "tooManyNumbers",
                "Envie para no máximo " + properties.getMaxNumbersPerSend() + " números por vez."
            );
        }
        if (!isConnected(fetchState(instanceName))) {
            throw new WhatsappException(
                HttpStatus.CONFLICT,
                "disconnected",
                "WhatsApp desconectado. Conecte o aparelho pelo QR Code antes de enviar mensagens."
            );
        }

        List<WhatsappSendResultDTO.Item> results = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> entry : numbers.entrySet()) {
            String input = entry.getKey();
            String number = entry.getValue();
            if (number == null) {
                results.add(new WhatsappSendResultDTO.Item(input, null, false, "Número inválido"));
                continue;
            }
            if (index++ > 0) {
                pause(properties.getDelayBetweenMessagesMs());
            }
            results.add(sendOne(instanceName, input, number, text));
        }

        int sent = (int) results.stream().filter(WhatsappSendResultDTO.Item::success).count();
        return new WhatsappSendResultDTO(results.size(), sent, results.size() - sent, results);
    }

    private WhatsappSendResultDTO.Item sendOne(String instanceName, String input, String number, String text) {
        try {
            restClient
                .post()
                .uri("/message/sendText/{instance}", instanceName)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("number", number, "text", text, "delay", 1200))
                .retrieve()
                .toBodilessEntity();
            return new WhatsappSendResultDTO.Item(input, number, true, null);
        } catch (RestClientResponseException e) {
            LOG.warn("Evolution sendText to {} failed with HTTP {}: {}", number, e.getStatusCode(), e.getResponseBodyAsString());
            return new WhatsappSendResultDTO.Item(input, number, false, describeSendError(e));
        } catch (ResourceAccessException e) {
            LOG.warn("Evolution sendText to {} failed: {}", number, e.getMessage());
            return new WhatsappSendResultDTO.Item(input, number, false, "Evolution API indisponível");
        }
    }

    private static String describeSendError(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body.contains("\"exists\":false")) {
            return "Número não possui WhatsApp";
        }
        return "Falha no envio (HTTP " + e.getStatusCode().value() + ")";
    }

    /**
     * Keeps the order typed by the user, drops duplicates and maps each input to the digits-only number
     * expected by Evolution ({@code null} when invalid). Brazilian numbers without country code get {@code 55}.
     */
    static Map<String, String> normalizeNumbers(List<String> rawNumbers) {
        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (String raw : Optional.ofNullable(rawNumbers).orElse(List.of())) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String input = raw.trim();
            String digits = input.replaceAll("\\D", "");
            if (digits.length() == 10 || digits.length() == 11) {
                digits = "55" + digits;
            }
            boolean valid = digits.length() >= 12 && digits.length() <= 15;
            if (valid && !seen.add(digits)) {
                continue;
            }
            normalized.putIfAbsent(input, valid ? digits : null);
        }
        return normalized;
    }

    private String fetchState(String instanceName) {
        try {
            Map<String, Object> response = restClient
                .get()
                .uri("/instance/connectionState/{instance}", instanceName)
                .retrieve()
                .body(MAP_TYPE);
            Object instance = response == null ? null : response.get("instance");
            Object state = instance instanceof Map<?, ?> map ? map.get("state") : response == null ? null : response.get("state");
            return state == null ? "close" : state.toString().toLowerCase();
        } catch (RestClientResponseException e) {
            return "close";
        } catch (ResourceAccessException e) {
            throw evolutionUnavailable(e);
        }
    }

    private boolean instanceExists(String instanceName) {
        try {
            List<Map<String, Object>> instances = restClient
                .get()
                .uri(uri -> uri.path("/instance/fetchInstances").queryParam("instanceName", instanceName).build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
            return instances != null && !instances.isEmpty();
        } catch (RestClientResponseException e) {
            return false;
        } catch (ResourceAccessException e) {
            throw evolutionUnavailable(e);
        }
    }

    private Map<String, Object> createInstance(String instanceName) {
        try {
            return restClient
                .post()
                .uri("/instance/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("instanceName", instanceName, "qrcode", true, "integration", "WHATSAPP-BAILEYS"))
                .retrieve()
                .body(MAP_TYPE);
        } catch (RestClientResponseException e) {
            // 403 means the instance already exists, which is fine: the QR Code is fetched afterwards.
            if (e.getStatusCode().value() == 403) {
                return null;
            }
            LOG.warn("Evolution instance/create failed with HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new WhatsappException(HttpStatus.BAD_GATEWAY, "createFailed", "Falha ao criar a instância do WhatsApp na Evolution API.");
        } catch (ResourceAccessException e) {
            throw evolutionUnavailable(e);
        }
    }

    private void deleteInstance(String instanceName) {
        for (String path : List.of("/instance/logout/{instance}", "/instance/delete/{instance}")) {
            try {
                restClient.delete().uri(path, instanceName).retrieve().toBodilessEntity();
            } catch (RestClientResponseException e) {
                LOG.debug("Evolution {} for {} returned HTTP {}", path, instanceName, e.getStatusCode());
            } catch (ResourceAccessException e) {
                throw evolutionUnavailable(e);
            }
        }
        for (int attempt = 0; attempt < 10 && instanceExists(instanceName); attempt++) {
            pause(400);
        }
    }

    private Map<String, Object> waitForQrCode(String instanceName) {
        Map<String, Object> last = null;
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                last = restClient.get().uri("/instance/connect/{instance}", instanceName).retrieve().body(MAP_TYPE);
                if (extractQrCode(last) != null || isConnected(fetchState(instanceName))) {
                    return last;
                }
            } catch (RestClientResponseException e) {
                LOG.debug("Evolution instance/connect for {} returned HTTP {}", instanceName, e.getStatusCode());
            } catch (ResourceAccessException e) {
                throw evolutionUnavailable(e);
            }
            pause(1500);
        }
        return last;
    }

    private static String extractQrCode(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object nested = payload.get("qrcode");
        Object base64 = nested instanceof Map<?, ?> map ? map.get("base64") : payload.get("base64");
        if (!(base64 instanceof String value) || value.isBlank()) {
            return null;
        }
        return value.startsWith("data:image") ? value : "data:image/png;base64," + value;
    }

    private static String extractPairingCode(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object nested = payload.get("qrcode");
        Object code = nested instanceof Map<?, ?> map ? map.get("pairingCode") : payload.get("pairingCode");
        return code instanceof String value && !value.isBlank() ? value : null;
    }

    private static boolean isConnected(String state) {
        return CONNECTED_STATES.contains(state);
    }

    private static WhatsappException evolutionUnavailable(ResourceAccessException e) {
        LOG.warn("Evolution API unreachable: {}", e.getMessage());
        return new WhatsappException(
            HttpStatus.BAD_GATEWAY,
            "evolutionUnavailable",
            "Não foi possível falar com a Evolution API. Verifique se o serviço está no ar."
        );
    }

    private static void pause(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
