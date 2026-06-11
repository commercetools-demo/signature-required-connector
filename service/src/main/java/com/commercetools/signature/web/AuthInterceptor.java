package com.commercetools.signature.web;

import com.commercetools.signature.config.Settings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates inbound API Extension calls. commercetools is configured (in postDeploy) to send
 * {@code Authorization: Bearer <secret>}; any request without the exact secret is rejected with
 * 401 before it reaches the controller. Uses a constant-time comparison to avoid leaking the
 * secret via timing. The {@code /service/status} liveness route is excluded (see WebConfig).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private final byte[] expectedHeader;

    public AuthInterceptor(Settings settings) {
        this.expectedHeader = ("Bearer " + settings.extensionSecret()).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String provided = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (provided != null
                && java.security.MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedHeader)) {
            return true;
        }
        log.warn("Rejected unauthenticated extension call to {}", request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errors\":[{\"code\":\"InvalidCredentials\",\"message\":\"Unauthorized\"}]}");
        return false;
    }
}
