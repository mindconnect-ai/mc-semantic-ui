package ai.mindconnect.sui.demo.stateful;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** {@code GET /mode?mode=spa|ssr} sets the cookie and sends the browser back. {@code GET /} goes to the products. */
@RestController
public class ModeToggleController {

    @GetMapping("/")
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/products")).build();
    }

    @GetMapping("/mode")
    public ResponseEntity<Void> mode(@RequestParam("mode") String mode, HttpServletRequest request,
                                     HttpServletResponse response) {
        boolean spa = SuiModeFilter.VALUE_SPA.equals(mode);
        Cookie cookie = new Cookie(SuiModeFilter.COOKIE_NAME, spa ? SuiModeFilter.VALUE_SPA : "");
        cookie.setPath("/");
        cookie.setMaxAge(spa ? 60 * 60 * 24 * 30 : 0);
        response.addCookie(cookie);
        String back = request.getHeader(HttpHeaders.REFERER);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create(back != null && !back.isBlank() ? back : "/products")).build();
    }
}
