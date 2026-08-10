package com.lilac.planner.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("jpa-test")
@TestPropertySource(properties = "planner.auth.enabled=false")
@DisplayName("Sticker catalog API - HTTP integration")
class StickerControllerIT {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("GET /api/v1/stickers returns all stickers with code, emoji, and name")
    void stickers_returnsFullCatalog() throws Exception {
        mvc.perform(get("/api/v1/stickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].code").isNotEmpty())
                .andExpect(jsonPath("$[0].emoji").isNotEmpty())
                .andExpect(jsonPath("$[0].name").isNotEmpty());
    }

    @Test
    @DisplayName("sticker catalog includes 'kitty' as one of the entries")
    void stickers_containsKnownEntry() throws Exception {
        mvc.perform(get("/api/v1/stickers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='kitty')].name").value("Sweet Kitty"));
    }
}
