package actia.api_gateway.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {

    @GetMapping("/api/user")
    public UserInfoResponse currentUser(@AuthenticationPrincipal OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No active session");
        }

        String username = oidcUser.getPreferredUsername();
        if (username == null || username.isBlank()) {
            username = oidcUser.getName();
        }

        List<String> roles = oidcUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();

        return new UserInfoResponse(username, oidcUser.getEmail(), roles);
    }

    public record UserInfoResponse(String username, String email, List<String> roles) {
    }
}
