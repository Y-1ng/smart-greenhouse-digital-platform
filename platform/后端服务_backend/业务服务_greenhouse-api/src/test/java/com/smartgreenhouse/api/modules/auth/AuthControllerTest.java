package com.smartgreenhouse.api.modules.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void logsInAndAccessesProtectedApiWithBearerToken() throws Exception {
        MvcResult login = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.displayName")
                        .value("基地管理员"))
                .andExpect(jsonPath("$.data.user.roleCode")
                        .value("ADMIN"))
                .andReturn();

        JsonNode body = objectMapper.readTree(
                login.getResponse().getContentAsString()
        );
        String accessToken = body.path("data").path("accessToken").asText();

        mockMvc.perform(
                        get("/api/greenhouses")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].greenhouseCode")
                        .value("GH-001"));
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "wrong-password"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void doesNotTriggerBrowserBasicAuthDialog() throws Exception {
        mockMvc.perform(get("/api/greenhouses"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void ignoresCachedBasicHeaderDuringLogin() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Basic aW52YWxpZDpvbGQtcGFzc3dvcmQ="
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "admin",
                                          "password": "123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void enforcesRoleBasedAccess() throws Exception {
        MvcResult login = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "operator",
                                          "password": "123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(
                login.getResponse().getContentAsString()
        ).path("data").path("accessToken").asText();

        mockMvc.perform(
                        get("/api/dashboard/government")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + accessToken
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("40300"));
    }
}
