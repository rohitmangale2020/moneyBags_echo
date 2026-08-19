package com.training.platform.users.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.platform.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Map;
import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void create_validRequest_returnsCreatedUser() throws Exception {
        String request = validCreateRequest("priyansh", "priyansh@example.com");

        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.username").value("priyansh"))
                .andExpect(jsonPath("$.email").value("priyansh@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.profile.firstName").value("Priyansh"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(delete("/api/v1/users/{id}", id)
                        .with(adminJwt(999L)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void selfActionsAreRejected_andNonAdminCannotMutateStatusOrDelete() throws Exception {
        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("target-user", "target@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/v1/users/{id}", id).with(adminJwt(id)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/users/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEACTIVATED\"}")
                        .with(adminJwt(id)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/v1/users/{id}", id)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("userId", 999L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEACTIVATED\"}")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.claim("userId", 999L))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt(long userId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt.claim("userId", userId))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void create_duplicateAndInvalidRequests_returnsConflictAndBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("first-user", "same@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest("second-user", "same@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("User conflict"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void list_paginatesNewestFirst_andSearchesAllSupportedFields() throws Exception {
        for (int index = 0; index < 21; index++) {
            String username = "member-" + index;
            String email = username + "@example.com";
            String firstName = index == 20 ? "FirstTarget" : "First" + index;
            String middleName = index == 20 ? "MiddleTarget" : null;
            String lastName = index == 20 ? "LastTarget" : "Last" + index;
            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateRequest(username, email, firstName, middleName, lastName)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/users").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].username").value("member-20"));

        for (String query : new String[]{"MEMBER-20", "member-20@example.com", "firsttarget", "middletarget", "lasttarget"}) {
            mockMvc.perform(get("/api/v1/users").param("q", query))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].username").value("member-20"));
        }

        mockMvc.perform(get("/api/v1/users").param("q", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private String validCreateRequest(String username, String email) throws Exception {
        return validCreateRequest(username, email, "Priyansh", null, "Pachauri");
    }

    private String validCreateRequest(String username, String email, String firstName, String middleName, String lastName) throws Exception {
        Map<String, Object> profile = new HashMap<>();
        profile.put("firstName", firstName);
        profile.put("lastName", lastName);
        profile.put("phoneNumber", "+919876543210");
        profile.put("countryCode", "IN");
        if (middleName != null) profile.put("middleName", middleName);
        return objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", email,
                "password", "correct-horse-battery-staple",
                "role", "CUSTOMER",
                "profile", profile
        ));
    }
}
