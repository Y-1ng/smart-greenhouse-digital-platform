package com.smartgreenhouse.api.modules.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class CertificateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void verifiesValidCertificateSignature() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        String canonical = canonical(
                "CERT-2026-0916-0031",
                2001,
                "ORG-001",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
                "2026-09-16T09:25:00"
        );
        String signature = sign(canonical, keyPair);

        mockMvc.perform(
                        post("/api/certificates/verify")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        request(signature, keyPair)
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.certificateNo")
                        .value("CERT-2026-0916-0031"))
                .andExpect(jsonPath("$.data.message").value("验签通过"));
    }

    @Test
    void rejectsTamperedCertificateSignature() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair();
        String signature = sign("different-content", keyPair);

        mockMvc.perform(
                        post("/api/certificates/verify")
                                .with(httpBasic("admin", "123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        request(signature, keyPair)
                                ))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.message").value("签名无效"));
    }

    private Map<String, Object> request(
            String signature,
            KeyPair keyPair
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestNo", "REQ-" + UUID.randomUUID());
        request.put("certificateNo", "CERT-2026-0916-0031");
        request.put("batchId", 2001);
        request.put("institutionId", "ORG-001");
        request.put(
                "contentHash",
                "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
        );
        request.put("issuedAt", "2026-09-16T09:25:00");
        request.put("publicKeyId", "PK-ORG-001");
        request.put(
                "publicKey",
                Base64.getEncoder().encodeToString(
                        keyPair.getPublic().getEncoded()
                )
        );
        request.put("signature", signature);
        return request;
    }

    private String canonical(
            String certificateNo,
            long batchId,
            String institutionId,
            String contentHash,
            String issuedAt
    ) {
        return String.join(
                "\n",
                certificateNo,
                Long.toString(batchId),
                institutionId,
                contentHash,
                issuedAt
        );
    }

    private String sign(String content, KeyPair keyPair) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
