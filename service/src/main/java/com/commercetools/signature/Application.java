package com.commercetools.signature;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Connect {@code service} application.
 *
 * <p>This is the long-running HTTP service registered as a commercetools cart
 * <a href="https://docs.commercetools.com/api/projects/api-extensions">API Extension</a>.
 * The one-shot lifecycle mains ({@code connector.PostDeploy} / {@code connector.PreUndeploy})
 * do not boot Spring — they are invoked directly by Maven from {@code connect.yaml}.
 */
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
