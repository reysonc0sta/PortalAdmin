package com.mycompany.myapp.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

public record WhatsappSendRequestDTO(@NotEmpty List<String> numbers, @NotBlank @Size(max = 4096) String text) implements Serializable {}
