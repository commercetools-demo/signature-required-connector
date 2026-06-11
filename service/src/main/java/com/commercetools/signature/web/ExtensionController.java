package com.commercetools.signature.web;

import com.commercetools.signature.service.ExtensionResult;
import com.commercetools.signature.service.SignatureFlagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cart API Extension endpoint.
 *
 * <ul>
 *   <li>{@code POST /service} — evaluate the cart and return update actions, or a 400 (fail-closed)
 *       when the operation must be blocked.</li>
 *   <li>{@code GET /service/status} — liveness (open, no secrets).</li>
 * </ul>
 *
 * <p>Responses are serialized with the commercetools SDK ObjectMapper so polymorphic update actions
 * carry their {@code action} discriminator.
 */
@RestController
public class ExtensionController {

    private static final Logger log = LoggerFactory.getLogger(ExtensionController.class);

    private final SignatureFlagService service;
    private final ObjectMapper ctpMapper;

    public ExtensionController(SignatureFlagService service, ObjectMapper ctpMapper) {
        this.service = service;
        this.ctpMapper = ctpMapper;
    }

    @PostMapping(path = "/service", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(
            @RequestBody String body,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId)
            throws Exception {
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        try {
            ExtensionResult result = service.process(body);
            if (result instanceof ExtensionResult.Actions a) {
                log.info("Cart evaluated: {} update action(s)", a.actions().size());
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(ctpMapper.writeValueAsString(Map.of("actions", a.actions())));
            }
            ExtensionResult.Reject r = (ExtensionResult.Reject) result;
            log.warn("Cart rejected (fail-closed): {} - {}", r.code(), r.message());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errorBody(r.code(), r.message()));
        } finally {
            MDC.remove("correlationId");
        }
    }

    @GetMapping("/service/status")
    public ResponseEntity<Map<String, String>> status() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    private String errorBody(String code, String message) throws Exception {
        return ctpMapper.writeValueAsString(Map.of("errors", List.of(Map.of("code", code, "message", message))));
    }
}
