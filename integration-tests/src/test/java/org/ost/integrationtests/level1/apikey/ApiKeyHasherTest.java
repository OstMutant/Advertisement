package org.ost.integrationtests.level1.apikey;

import org.junit.jupiter.api.Test;
import org.ost.apikey.security.ApiKeyHasher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for {@link ApiKeyHasher} — no Spring context, no DB.
 */
class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void generate_twoCalls_produceDifferentKeys() {
        assertThat(hasher.generate()).isNotEqualTo(hasher.generate());
    }

    @Test
    void generate_returnsHighEntropyUrlSafeString() {
        String rawKey = hasher.generate();

        assertThat(rawKey).hasSizeGreaterThanOrEqualTo(40);
        assertThat(rawKey).doesNotContain("+", "/", "=");
    }

    @Test
    void hash_sameInput_isDeterministic() {
        String rawKey = hasher.generate();

        assertThat(hasher.hash(rawKey)).isEqualTo(hasher.hash(rawKey));
    }

    @Test
    void hash_differentInputs_produceDifferentHashes() {
        assertThat(hasher.hash(hasher.generate())).isNotEqualTo(hasher.hash(hasher.generate()));
    }

    @Test
    void hash_returnsSha256HexString() {
        String hash = hasher.hash("some-raw-key");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }
}
