package br.com.jorgeacetozi.ebookChat.configuration;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When app.redirect-to-frontend is true, redirects GET requests for Thymeleaf UI paths
 * to the Next.js frontend (app.frontend-url) so the backend can serve only REST/WebSocket.
 */
@Component
@Order(-100)
public class RedirectToFrontendFilter extends OncePerRequestFilter {

    private static final List<String> REDIRECT_PATHS = Arrays.asList(
            "/", "/login", "/new-account", "/chat", "/approvals", "/analytics"
    );

    private final FrontendRedirectProperties properties;

    public RedirectToFrontendFilter(FrontendRedirectProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.isRedirectToFrontend() || !"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (path == null) path = "";
        // Never redirect API or WS requests; only HTML pages
        if (path.startsWith("/api/") || path.startsWith("/ws")) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean redirect = REDIRECT_PATHS.contains(path) || path.startsWith("/chatroom/");
        if (!redirect) {
            filterChain.doFilter(request, response);
            return;
        }
        String base = properties.getFrontendUrl();
        if (base == null || base.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        base = base.replaceAll("/$", "");
        String target = path.equals("/") ? base : base + path;
        response.sendRedirect(target);
    }
}
