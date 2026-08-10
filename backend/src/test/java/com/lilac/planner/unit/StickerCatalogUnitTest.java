package com.lilac.planner.unit;

import com.lilac.planner.service.Sticker;
import com.lilac.planner.service.StickerCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StickerCatalog - animal sticker catalog")
class StickerCatalogUnitTest {

    private final StickerCatalog catalog = new StickerCatalog();

    @Test
    @DisplayName("catalog is non-empty and has multiple cute animals")
    void catalogIsPopulated() {
        assertThat(catalog.all()).hasSizeGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("every sticker has a unique code, a non-blank emoji, and a non-blank name")
    void stickersAreWellFormed() {
        Set<String> codes = catalog.all().stream().map(Sticker::code).collect(Collectors.toSet());
        assertThat(codes).hasSameSizeAs(catalog.all());
        assertThat(catalog.all()).allSatisfy(s -> {
            assertThat(s.code()).isNotBlank();
            assertThat(s.emoji()).isNotBlank();
            assertThat(s.name()).isNotBlank();
        });
    }

    @Test
    @DisplayName("pickFor is deterministic for the same seed")
    void pickForIsDeterministic() {
        assertThat(catalog.pickFor(42)).isEqualTo(catalog.pickFor(42));
        assertThat(catalog.pickFor(-7)).isEqualTo(catalog.pickFor(-7));
    }

    @Test
    @DisplayName("pickFor wraps around the catalog for any seed (including negatives)")
    void pickForWrapsAround() {
        for (long seed = -50; seed <= 50; seed++) {
            assertThat(catalog.pickFor(seed)).isNotNull();
        }
    }

    @Test
    @DisplayName("byCode returns the matching sticker, or null for unknown codes")
    void byCode() {
        Sticker first = catalog.all().get(0);
        assertThat(catalog.byCode(first.code())).isEqualTo(first);
        assertThat(catalog.byCode("does-not-exist")).isNull();
    }
}
