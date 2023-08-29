package com.github.vssavin.umlib.domain.security.spring;

import com.github.vssavin.umlib.config.UmConfig;
import com.github.vssavin.umlib.domain.event.EventService;
import com.github.vssavin.umlib.domain.event.EventType;
import com.github.vssavin.umlib.domain.user.UserService;
import com.github.vssavin.umlib.domain.user.Role;
import com.github.vssavin.umlib.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

/**
 * An {@link org.springframework.security.web.authentication.AuthenticationSuccessHandler} implementation
 * that attempts to authenticate the user using o2Auth or user/password mechanism.
 *
 * @author vssavin on 22.12.21
 */
@Component
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final EventService eventService;
    private final UmConfig umConfig;

    public CustomAuthenticationSuccessHandler(UserService userService, EventService eventService, UmConfig umConfig) {
        this.userService = userService;
        this.eventService = eventService;
        this.umConfig = umConfig;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String successUrl = umConfig.getSuccessUrl();
        User user = null;
        try {
            OAuth2User oAuth2User = (DefaultOAuth2User) authentication.getPrincipal();
            user = userService.processOAuthPostLogin(oAuth2User);
            if (user.getAuthority().equals(Role.ROLE_ADMIN.name())) {
                successUrl = umConfig.getAdminSuccessUrl();
            }
        } catch (ClassCastException e) {
            //ignore, it's ok
        }

        if (user == null) {
            user = userService.getUserByLogin(authentication.getPrincipal().toString());
            if (user != null) {
                if (user.getAuthority().equals(Role.ROLE_ADMIN.name())) {
                    successUrl = umConfig.getAdminSuccessUrl();
                }
                if (user.getExpirationDate().before(new Date())) {
                    userService.deleteUser(user);
                    successUrl = UmConfig.LOGIN_URL + "?error=true";
                }
            }
        }

        if (user != null) {
            eventService.createEvent(user, EventType.LOGGED_IN,
                    String.format("User %s logged in using IP: %s", user.getLogin(), request.getRemoteAddr()));
        }

        String lang = request.getParameter("lang");
        String delimiter = "?";
        if (successUrl.contains("?")) {
            delimiter = "&";
        }
        if (lang != null) {
            lang = delimiter + "lang=" + lang;
        } else {
            lang = "";
        }

        response.sendRedirect(successUrl + lang);
    }
}