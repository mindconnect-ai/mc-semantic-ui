package ai.mindconnect.sui.demo.shop;

import org.springframework.stereotype.Component;

/**
 * Hard-coded current user for the demo. A real app would pull this from
 * Spring Security's {@link org.springframework.security.core.Authentication}
 * (or whichever identity layer it uses); the demo keeps it static so
 * the focus stays on the SSR rendering, not on auth setup.
 */
@Component
public class DemoUser {

    public String name()        { return "Demo Admin"; }
    public String initials()    { return "DA"; }
    public String email()       { return "demo@example.com"; }
    public String role()        { return "Administrator"; }
    public String profileHref() { return "/admin/profile"; }
}
