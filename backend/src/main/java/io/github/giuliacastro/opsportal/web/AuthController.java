package io.github.giuliacastro.opsportal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public record LoginRequest(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Size(max = 200) String password) {}

    public record MeResponse(String username, List<String> roles) {

        static MeResponse of(Authentication auth) {
            return new MeResponse(
                    auth.getName(),
                    auth.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            // Spring Security 7 also grants factor authorities (FACTOR_PASSWORD).
                            .filter(authority -> authority.startsWith("ROLE_"))
                            .map(role -> role.substring("ROLE_".length()))
                            .sorted()
                            .toList());
        }
    }

    public record CsrfResponse(String headerName) {}

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;

    public AuthController(
            AuthenticationManager authenticationManager, SecurityContextRepository contextRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
    }

    /** Touching the token makes Spring Security write the XSRF-TOKEN cookie for the SPA. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        token.getToken();
        return new CsrfResponse(token.getHeaderName());
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        contextRepository.saveContext(context, request, response);
        log.info("auth.login.succeeded user={}", auth.getName());
        return MeResponse.of(auth);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        return MeResponse.of(auth);
    }
}
