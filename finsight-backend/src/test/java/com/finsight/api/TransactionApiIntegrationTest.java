package com.finsight.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.api.dto.request.RegisterRequest;
import com.finsight.api.dto.response.AuthResponse;
import com.finsight.domain.entity.Category;
import com.finsight.repository.CategoryRepository;
import com.finsight.service.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionApiIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String ownerToken;
    private String otherToken;
    private Category category;

    @BeforeEach
    void prepareUsersAndCategory() {
        ownerToken = register("owner-" + UUID.randomUUID() + "@example.com").accessToken();
        otherToken = register("other-" + UUID.randomUUID() + "@example.com").accessToken();
        category = categoryRepository.findAll().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("V2 category seed migration did not run"));
    }

    @Test
    void manualTransactionCanBeCreatedEditedReviewedIncludedAndDeleted() throws Exception {
        String created = mockMvc.perform(post("/api/v1/transactions")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(category.getId(), "Coffee")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source", is("MANUAL")))
            .andExpect(jsonPath("$.reviewStatus", is("PENDING")))
            .andExpect(jsonPath("$.includedInReports", is(false)))
            .andExpect(jsonPath("$.category.id", is(category.getId().toString())))
            .andReturn().getResponse().getContentAsString();
        JsonNode createdJson = objectMapper.readTree(created);
        String transactionId = createdJson.get("id").asText();

        mockMvc.perform(patch("/api/v1/transactions/{id}/inclusion", transactionId)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"included\":true}"))
            .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/transactions/{id}/review", transactionId)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewStatus", is("APPROVED")));

        mockMvc.perform(patch("/api/v1/transactions/{id}/inclusion", transactionId)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"included\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.includedInReports", is(true)));

        mockMvc.perform(put("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(category.getId(), "Updated description")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description", is("Updated description")))
            .andExpect(jsonPath("$.source", is("MANUAL")));

        mockMvc.perform(get("/api/v1/transactions")
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()", is(1)));

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void usersCannotReadUpdateOrDeleteAnotherUsersTransaction() throws Exception {
        String created = mockMvc.perform(post("/api/v1/transactions")
                .header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(category.getId(), "Owner transaction")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String transactionId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(otherToken)))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(category.getId(), "Attempted change")))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/transactions/{id}", transactionId)
                .header("Authorization", bearer(otherToken)))
            .andExpect(status().isNotFound());
    }

    @Test
    void categoriesAreRetrievedFromSeededDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                .header("Authorization", bearer(ownerToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").isNotEmpty());
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "Password123", "Test User"));
    }

    private String transactionJson(UUID categoryId, String description) {
        return "{\"amount\":\"12.50\",\"currency\":\"USD\",\"transactionDate\":\"2026-11-25\","
            + "\"description\":\"" + description + "\",\"type\":\"DEBIT\","
            + "\"categoryId\":\"" + categoryId + "\"}";
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}