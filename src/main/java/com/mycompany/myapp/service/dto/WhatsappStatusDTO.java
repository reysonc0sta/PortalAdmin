package com.mycompany.myapp.service.dto;

import java.io.Serializable;

/**
 * Connection state of the current user's WhatsApp instance. {@code qrCode} is a {@code data:image} URI
 * and is only filled while the instance is waiting to be paired.
 */
public record WhatsappStatusDTO(
    String instanceName,
    String state,
    boolean connected,
    String qrCode,
    String pairingCode
) implements Serializable {}
