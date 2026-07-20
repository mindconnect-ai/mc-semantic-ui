package ai.mindconnect.sui.demo.shop;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Switches the {@code sui-theme} cookie. Two modes:
 *
 * <ul>
 *   <li><strong>SET</strong>: when {@code theme} query/form param is present
 *       and recognised, store it verbatim. Used by the header dropdown
 *       ({@code <select submitOnChange>}) which sends the chosen value.</li>
 *   <li><strong>CYCLE</strong>: when {@code theme} is absent, advance through
 *       light → dark → sbb → light. Kept so a single cycle-button still
 *       works for apps that don't render the dropdown.</li>
 * </ul>
 *
 * <p>POST + 303 redirect back to the {@code Referer} so the browser performs
 * a fresh GET in the newly selected theme — same PRG pattern as
 * {@link ModeToggleController}.
 */
@RestController
@RequestMapping("/admin/toggle-theme")
public class ThemeToggleController {

    @PostMapping
    public ResponseEntity<Void> toggle(@RequestParam(name = "theme", required = false) String requested,
                                        HttpServletRequest request,
                                        HttpServletResponse response) {
        String current = SuiThemeFilter.resolveTheme(request);
        String target = sanitise(requested);
        String next = target != null ? target : nextTheme(current);

        Cookie cookie = new Cookie(SuiThemeFilter.COOKIE_NAME, next);
        cookie.setPath("/");
        // 30 days — purely a UX preference, no security implication.
        cookie.setMaxAge(60 * 60 * 24 * 30);
        response.addCookie(cookie);

        String back = request.getHeader(HttpHeaders.REFERER);
        URI dest = URI.create(back != null && !back.isBlank() ? back : "/");
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(dest).build();
    }

    /** Returns the parameter unchanged if it's a known theme id; null otherwise. */
    private static String sanitise(String requested) {
        if (requested == null) return null;
        return switch (requested) {
            case SuiThemeFilter.THEME_LIGHT,
                 SuiThemeFilter.THEME_DARK,
                 SuiThemeFilter.THEME_SBB -> requested;
            default -> null;
        };
    }

    /** light → dark → sbb → light. */
    public static String nextTheme(String current) {
        return switch (current) {
            case SuiThemeFilter.THEME_LIGHT -> SuiThemeFilter.THEME_DARK;
            case SuiThemeFilter.THEME_DARK  -> SuiThemeFilter.THEME_SBB;
            case SuiThemeFilter.THEME_SBB   -> SuiThemeFilter.THEME_LIGHT;
            default                          -> SuiThemeFilter.THEME_LIGHT;
        };
    }

    /** Human-readable label for the chip ("Light", "Dark", "SBB"). */
    public static String label(String theme) {
        return switch (theme) {
            case SuiThemeFilter.THEME_DARK -> "Dark";
            case SuiThemeFilter.THEME_SBB  -> "SBB";
            default                         -> "Light";
        };
    }
}
