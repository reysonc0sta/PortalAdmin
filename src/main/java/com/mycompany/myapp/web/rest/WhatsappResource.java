package com.mycompany.myapp.web.rest;

import com.mycompany.myapp.security.SecurityUtils;
import com.mycompany.myapp.service.WhatsappService;
import com.mycompany.myapp.service.dto.WhatsappSendRequestDTO;
import com.mycompany.myapp.service.dto.WhatsappSendResultDTO;
import com.mycompany.myapp.service.dto.WhatsappStatusDTO;
import com.mycompany.myapp.web.rest.errors.WhatsappException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for sending WhatsApp messages from the current user's own number.
 */
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappResource {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappResource.class);

    private final WhatsappService whatsappService;

    public WhatsappResource(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    /**
     * {@code GET  /whatsapp/status} : connection state of the current user's WhatsApp.
     */
    @GetMapping("/status")
    public WhatsappStatusDTO getStatus() {
        return whatsappService.getStatus(currentInstance());
    }

    /**
     * {@code POST  /whatsapp/connect} : prepare the current user's instance and return the QR Code to pair it.
     *
     * @param force drop the current session and generate a new QR Code.
     */
    @PostMapping("/connect")
    public WhatsappStatusDTO connect(@RequestParam(defaultValue = "false") boolean force) {
        String instance = currentInstance();
        LOG.debug("REST request to connect WhatsApp instance {} (force={})", instance, force);
        return whatsappService.connect(instance, force);
    }

    /**
     * {@code POST  /whatsapp/send} : send the same text message to one or more numbers.
     */
    @PostMapping("/send")
    public WhatsappSendResultDTO send(@Valid @RequestBody WhatsappSendRequestDTO request) {
        String instance = currentInstance();
        LOG.debug("REST request to send WhatsApp message from {} to {} numbers", instance, request.numbers().size());
        return whatsappService.sendText(instance, request.numbers(), request.text());
    }

    private String currentInstance() {
        return SecurityUtils.getCurrentUserId()
            .map(whatsappService::instanceName)
            .orElseThrow(() -> new WhatsappException(HttpStatus.UNAUTHORIZED, "noUser", "Usuário não identificado."));
    }
}
