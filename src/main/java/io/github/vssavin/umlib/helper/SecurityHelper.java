package io.github.vssavin.umlib.helper;

import io.github.vssavin.umlib.entity.Role;
import io.github.vssavin.umlib.entity.User;
import io.github.vssavin.umlib.exception.EmailNotFoundException;
import io.github.vssavin.umlib.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Created by vssavin on 02.08.2022.
 */
public class SecurityHelper {

    private SecurityHelper() {}

    public static String getAuthorizedUserName(UserService userService) {
        String authorizedUserName = getAuthorizedUserLogin();
        if (!authorizedUserName.isEmpty() && userService != null) {
            try {
                authorizedUserName = userService.getUserByLogin(authorizedUserName).getName();
            } catch (UsernameNotFoundException e) {
                authorizedUserName = "";
            }
            if (authorizedUserName.isEmpty()) {
                try {
                    authorizedUserName = userService.getUserByEmail(authorizedUserName).getName();
                } catch (EmailNotFoundException ignore) {
                    //ignore, it's ok
                }
            }
        }
        return authorizedUserName;
    }

    public static String getAuthorizedUserLogin() {
        String authorizedUserName = "";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            if (authentication instanceof OAuth2AuthenticationToken) {
                OAuth2User oAuth2User = ((OAuth2AuthenticationToken)authentication).getPrincipal();
                authorizedUserName = oAuth2User.getAttribute("email");
            } else {
                authorizedUserName = authentication.getName();
            }
        }
        return authorizedUserName;
    }

    public static boolean isAuthorizedAdmin(UserService userService) {
        String login = getAuthorizedUserLogin();
        boolean isAdminUser = false;
        User user = null;
        if (!login.isEmpty() && userService != null) {
            try {
                user = userService.getUserByLogin(login);
            } catch (UsernameNotFoundException ignored) {
            }

            if (user == null) {
                try {
                    user = userService.getUserByEmail(login);
                } catch (EmailNotFoundException ignored) {
                    //ignore, it's ok
                }
            }

            if (user != null) {
                String authority = user.getAuthority();
                Role role = Role.getRole(authority);
                if (role.equals(Role.ROLE_ADMIN)) isAdminUser = true;
            }

        }
        return isAdminUser;
    }

}
