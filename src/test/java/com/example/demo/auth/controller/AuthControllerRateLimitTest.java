package com.example.demo.auth.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.demo.auth.dto.LoginRequest;
import com.example.demo.auth.dto.LoginResponse;
import com.example.demo.auth.exception.InvalidCredentialsException;
import com.example.demo.auth.ratelimit.LoginRateLimitExceededException;
import com.example.demo.auth.ratelimit.LoginRateLimitService;
import com.example.demo.auth.service.AuthService;

class AuthControllerRateLimitTest {

    private AuthService authService;
    private LoginRateLimitService rateLimitService;
    private AuthController controller;
    private LoginRequest loginRequest;
    private MockHttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        rateLimitService = new LoginRateLimitService(5, 900, 900);
        controller = new AuthController(authService, rateLimitService);

        loginRequest = new LoginRequest();
        loginRequest.setTenantPublicId("tenant-test");
        loginRequest.setEmail("admin@example.test");
        loginRequest.setPassword("invalid-password");

        servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("203.0.113.20");
    }

    @Test
    void fifthInvalidLoginBlocksBeforeAnotherAuthenticationAttempt() {
        when(authService.login(loginRequest)).thenThrow(new InvalidCredentialsException());

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> controller.login(loginRequest, servletRequest));
        }

        assertThrows(LoginRateLimitExceededException.class,
                () -> controller.login(loginRequest, servletRequest));
        assertThrows(LoginRateLimitExceededException.class,
                () -> controller.login(loginRequest, servletRequest));

        verify(authService, times(5)).login(loginRequest);
    }

    @Test
    void successfulLoginClearsTheClientFailureCounter() {
        LoginResponse response = LoginResponse.builder().token("test-token").build();
        when(authService.login(loginRequest))
                .thenThrow(
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException())
                .thenReturn(response)
                .thenThrow(
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException(),
                        new InvalidCredentialsException());

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> controller.login(loginRequest, servletRequest));
        }

        assertDoesNotThrow(() -> controller.login(loginRequest, servletRequest));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> controller.login(loginRequest, servletRequest));
        }
    }
}
