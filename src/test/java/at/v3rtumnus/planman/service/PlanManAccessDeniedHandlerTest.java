package at.v3rtumnus.planman.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.*;

class PlanManAccessDeniedHandlerTest {

    private final PlanManAccessDeniedHandler handler = new PlanManAccessDeniedHandler();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void handle_withAuthenticatedUser_redirectsToLogin() throws IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pass", List.of()));

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getRequestURI()).thenReturn("/admin/secret");
        when(request.getContextPath()).thenReturn("");

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(response).sendRedirect("/login");
    }

    @Test
    void handle_withNoAuthentication_redirectsToLogin() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getContextPath()).thenReturn("");

        handler.handle(request, response, new AccessDeniedException("denied"));

        verify(response).sendRedirect("/login");
    }
}
