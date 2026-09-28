package com.mycompany.myapp.web.rest.errors;

import java.io.Serial;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class WhatsappException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public WhatsappException(HttpStatus status, String errorKey, String defaultMessage) {
        super(
            status,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(status.value())
                .withType(ErrorConstants.DEFAULT_TYPE)
                .withTitle(defaultMessage)
                .withDetail(defaultMessage)
                .withProperty("message", "whatsapp.errors." + errorKey)
                .build(),
            null
        );
    }
}
