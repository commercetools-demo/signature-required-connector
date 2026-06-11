package com.commercetools.signature.service;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.type.Type;
import com.commercetools.signature.config.Settings;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Looks up the custom Type id by key via the commercetools API and caches it for the process
 * lifetime. The lookup happens lazily on first need (after {@code postDeploy} has created the
 * Type) and is retried until it succeeds, then cached permanently.
 */
@Component
public class ApiTypeResolver implements TypeResolver {

    private static final Logger log = LoggerFactory.getLogger(ApiTypeResolver.class);

    private final ProjectApiRoot apiRoot;
    private final Settings settings;
    private final AtomicReference<String> cachedId = new AtomicReference<>();

    public ApiTypeResolver(ProjectApiRoot apiRoot, Settings settings) {
        this.apiRoot = apiRoot;
        this.settings = settings;
    }

    @Override
    public Optional<String> resolveTypeId() {
        String id = cachedId.get();
        if (id != null) {
            return Optional.of(id);
        }
        try {
            Type type = apiRoot.types()
                    .withKey(settings.customTypeKey())
                    .get()
                    .executeBlocking()
                    .getBody();
            cachedId.set(type.getId());
            return Optional.of(type.getId());
        } catch (Exception e) {
            // Type not found yet (e.g. postDeploy still running) or a transient lookup error.
            // The caller decides what to do; under fail-closed an unresolved Type blocks a
            // narcotic cart rather than letting it through unflagged.
            log.warn("Could not resolve custom Type by key '{}': {}", settings.customTypeKey(), e.getMessage());
            return Optional.empty();
        }
    }
}
