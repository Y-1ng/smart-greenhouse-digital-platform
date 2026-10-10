package com.smartgreenhouse.api.modules.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expiresInSeconds;

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expires-minutes:120}")
            long expiresInMinutes
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresInSeconds = expiresInMinutes * 60;
    }

    public TokenResult create(AuthMapper.UserRow user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expiresInSeconds;

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.username());
        payload.put("uid", user.userId());
        payload.put("name", user.displayName());
        payload.put("role", user.roleCode());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return new TokenResult(
                unsigned + "." + sign(unsigned),
                expiresAt
        );
    }

    public Claims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw unauthorized();
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(
                    sign(unsigned).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                throw unauthorized();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<>() {
                    }
            );
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw unauthorized();
            }
            return new Claims(
                    String.valueOf(payload.get("sub")),
                    Number.class.cast(payload.get("uid")).longValue(),
                    String.valueOf(payload.get("name")),
                    String.valueOf(payload.get("role")),
                    expiresAt
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized();
        }
    }

    public long expiresInSeconds() {
        return expiresInSeconds;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "登录凭证生成失败"
            );
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
                    );
        } catch (Exception exception) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "登录凭证签名失败"
            );
        }
    }

    private BusinessException unauthorized() {
        return new BusinessException(
                ErrorCode.UNAUTHORIZED,
                "登录凭证无效或已过期"
        );
    }

    public record TokenResult(String token, long expiresAt) {
    }

    public record Claims(
            String username,
            Long userId,
            String displayName,
            String roleCode,
            long expiresAt
    ) {
    }
}
