package com.mycompany.myapp.service.dto;

import java.io.Serializable;
import java.util.List;

public record WhatsappSendResultDTO(int total, int sent, int failed, List<Item> results) implements Serializable {
    public record Item(String input, String number, boolean success, String error) implements Serializable {}
}
