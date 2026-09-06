package com.skillshare.skillsharebackend.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Phase 6 - builds the {@link RazorpayClient} bean from
 * {@code razorpay.key-id}/{@code razorpay.key-secret} (application-dev.yml,
 * both env-var-backed, same pattern as {@code SKILLSHARE_DB_PASSWORD}).
 *
 * <p>Safe to construct with empty/unset keys: {@code RazorpayClient}'s
 * constructor does no network call and no key-format validation - it
 * only builds the internal API-resource objects. That means the app
 * still boots, and every non-payment endpoint still works, before a real
 * Razorpay test account exists; only an actual call to
 * {@code orders.create}/{@code payments.fetch} fails (with Razorpay's own
 * authentication error) until real Test Mode keys are set.
 */
@Configuration
public class RazorpayConfig {

    @Bean
    public RazorpayClient razorpayClient(
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String keySecret) throws RazorpayException {
        return new RazorpayClient(keyId, keySecret);
    }
}
