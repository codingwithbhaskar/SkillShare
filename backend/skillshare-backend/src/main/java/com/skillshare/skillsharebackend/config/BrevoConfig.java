package com.skillshare.skillsharebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The {@link RestClient} {@code PasswordResetMailer} uses to call Brevo's
 * Transactional Email API. Split into its own bean (rather than built
 * inline in {@code PasswordResetMailer}'s constructor) so tests can hand
 * that class a mock {@code RestClient} directly instead of a real one.
 */
@Configuration
public class BrevoConfig {

    @Bean
    public RestClient brevoRestClient() {
        // Built via the static factory rather than an injected
        // RestClient.Builder bean - Spring Boot 4's module split doesn't
        // auto-configure one just from spring-boot-starter-webmvc alone
        // (confirmed live: NoSuchBeanDefinitionException for
        // RestClient.Builder otherwise). RestClient itself needs nothing
        // beyond spring-web, which webmvc already brings in.
        //
        // Belt-and-suspenders alongside PasswordResetMailer's @Async: even
        // an HTTPS call can hang if the far end goes silent mid-response,
        // so this still shouldn't block a thread forever.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(10_000);
        return RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .requestFactory(requestFactory)
                .build();
    }
}
