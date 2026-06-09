package com.nmcrate.key.tests;

import com.nmcrate.key.NMKey;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NMKeyJavaApiTest {
    @Test
    void exposesDefaultApiUrlToJava() {
        assertEquals("https://www.nmcrate.com/api/nmkey/v1", NMKey.DEFAULT_API_URL);
    }

    @Test
    void doesNotExposeCustomApiEndpointOverloadsToJava() {
        boolean exposesCustomEndpoint = Arrays.stream(NMKey.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("check") || method.getName().equals("release"))
                .anyMatch(method -> method.getParameterCount() == 3 && method.getParameterTypes()[2] == String.class);

        assertFalse(exposesCustomEndpoint);
    }

    @Test
    void exposesRequestDtoToJava() {
        NMKey.KeyRequest request = new NMKey.KeyRequest(
                "plugin-id",
                "license-key",
                "fingerprint",
                "nonce"
        );

        assertEquals("plugin-id", request.getPluginId());
        assertEquals("license-key", request.getKey());
        assertEquals("fingerprint", request.getFingerprint());
        assertEquals("nonce", request.getNonce());
    }

    @Test
    void exposesResponseDtoToJava() {
        NMKey.KeyResponse response = new NMKey.KeyResponse("valid", "2026-06-08T00:00:00Z", "signature");

        assertEquals("valid", response.getStatus());
        assertEquals("2026-06-08T00:00:00Z", response.getIssuedAt());
        assertEquals("signature", response.getSignature());
    }

    @Test
    void clearCacheIsJavaCallable() {
        NMKey.clearCache();
    }
}
