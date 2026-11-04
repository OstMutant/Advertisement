package org.ost.restapi.api.patch;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit tests for {@link MergePatchSupport}. */
class MergePatchSupportTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final MergePatchSupport support = new MergePatchSupport(Validation.buildDefaultValidatorFactory().getValidator());
    private final Sample current = new Sample("title", 5L, Set.of(1L, 2L));

    record Sample(@NotBlank String title, Long cityId, @Size(max = 2) Set<Long> ids) {
    }

    private Sample patch(String json) {
        return support.apply(current, MAPPER.readTree(json), Sample.class);
    }

    @Test
    void absentField_keepsValue() {
        assertThat(patch("{\"title\":\"new\"}").cityId()).isEqualTo(5L);
    }

    @Test
    void nullOnNullableField_clearsIt() {
        assertThat(patch("{\"cityId\":null}").cityId()).isNull();
    }

    @Test
    void nullOnRequiredField_failsValidationWithThatKey() {
        assertThatThrownBy(() -> patch("{\"title\":null}"))
                .isInstanceOfSatisfying(PatchValidationException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("title"));
    }

    @Test
    void value_replaces() {
        assertThat(patch("{\"title\":\"new\"}").title()).isEqualTo("new");
    }

    @Test
    void array_isReplacedWhole() {
        assertThat(patch("{\"ids\":[9]}").ids()).containsExactly(9L);
    }

    @Test
    void emptyPatch_returnsEqualRecord() {
        assertThat(patch("{}")).isEqualTo(current);
    }

    @Test
    void nonObjectPatch_isRejected() {
        JsonNode array = MAPPER.readTree("[1]");
        assertThatThrownBy(() -> support.apply(current, array, Sample.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownMember_isRejectedNamingIt() {
        assertThatThrownBy(() -> patch("{\"cityTaxonID\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cityTaxonID");
    }

    @Test
    void wrongType_isRejected() {
        assertThatThrownBy(() -> patch("{\"cityId\":\"x\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sizeExceeded_failsValidation() {
        assertThatThrownBy(() -> patch("{\"ids\":[1,2,3]}"))
                .isInstanceOfSatisfying(PatchValidationException.class,
                        ex -> assertThat(ex.getFieldErrors()).containsKey("ids"));
    }
}
