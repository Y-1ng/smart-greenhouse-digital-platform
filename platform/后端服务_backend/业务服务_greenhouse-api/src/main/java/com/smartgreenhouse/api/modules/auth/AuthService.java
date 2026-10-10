package com.smartgreenhouse.api.modules.auth;

import com.smartgreenhouse.common.error.BusinessException;
import com.smartgreenhouse.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AuthMapper mapper,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional(readOnly = true)
    public LoginView login(AuthController.LoginRequest request) {
        AuthMapper.UserRow user = mapper.findByUsername(request.username());
        if (user == null
                || user.status() == null
                || user.status() != 1
                || !passwordEncoder.matches(
                        request.password(),
                        user.passwordHash()
                )) {
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        JwtTokenService.TokenResult token =
                jwtTokenService.create(user);
        return new LoginView(
                token.token(),
                "Bearer",
                jwtTokenService.expiresInSeconds(),
                new LoginView.UserView(
                        user.userId(),
                        user.username(),
                        user.displayName(),
                        user.roleCode()
                )
        );
    }
}
