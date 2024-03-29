package io.github.vssavin.umlib.config;

import io.github.vssavin.umlib.security.spring.BannedIpFilter;
import io.github.vssavin.umlib.service.impl.CustomOAuth2UserService;
import io.github.vssavin.umlib.utils.AuthorizedUrlPermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.sql.DataSource;
import java.util.List;

/**
 * Created by vssavin on 17.05.2022.
 */
public class DefaultSecurityConfig extends WebSecurityConfigurerAdapter {

    public static final String LOGIN_URL = UmConfig.LOGIN_URL;
    public static final String LOGIN_PROCESSING_URL = UmConfig.LOGIN_PROCESSING_URL;
    public static final String LOGOUT_URL = UmConfig.LOGOUT_URL;

    public static String successUrl = "/index.html";
    public static String adminSuccessUrl = "/um/admin";

    private final DataSource dataSource;
    private final AuthenticationSuccessHandler authSuccessHandler;
    private final AuthenticationFailureHandler authFailureHandler;
    private final AuthenticationProvider authProvider;
    private final LogoutHandler logoutHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final LogoutSuccessHandler logoutSuccessHandler;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2Config oAuth2Config;

    @Autowired
    public DefaultSecurityConfig(UmConfig umConfig, DataSource dataSource,
                                 AuthenticationSuccessHandler authSuccessHandler,
                                 AuthenticationFailureHandler authFailureHandler, AuthenticationProvider authProvider,
                                 LogoutHandler logoutHandler, CustomOAuth2UserService customOAuth2UserService,
                                 LogoutSuccessHandler logoutSuccessHandler, PasswordEncoder passwordEncoder,
                                 OAuth2Config oAuth2Config) {
        this.dataSource = dataSource;
        this.authSuccessHandler = authSuccessHandler;
        this.authFailureHandler = authFailureHandler;
        this.authProvider = authProvider;
        this.logoutHandler = logoutHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.logoutSuccessHandler = logoutSuccessHandler;
        this.passwordEncoder = passwordEncoder;
        this.oAuth2Config = oAuth2Config;
        UmConfig.adminSuccessUrl = adminSuccessUrl;
        UmConfig.successUrl = successUrl;
        umConfig.updateAuthorizedPermissions();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(authProvider);
        auth.jdbcAuthentication()
                .dataSource(dataSource)
                .passwordEncoder(passwordEncoder)
                .usersByUsernameQuery(
                        "select login, password, 'true' from users " +
                                "where login=?")
                .authoritiesByUsernameQuery(
                        "select login, authority from users " +
                                "where login=?");
    }

    @Bean
    public JdbcUserDetailsManager jdbcUserDetailsManager() {
        JdbcUserDetailsManager jdbcUserDetailsManager = new JdbcUserDetailsManager();
        jdbcUserDetailsManager.setDataSource(dataSource);
        return jdbcUserDetailsManager;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {

        http.addFilterBefore(
                new BannedIpFilter(), BasicAuthenticationFilter.class);

        http.sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS);

        List<AuthorizedUrlPermission> urlPermissions = UmConfig.getAuthorizedUrlPermissions();

        urlPermissions.add(new AuthorizedUrlPermission("/games/**", new String[]{"ADMIN", "USER"}));
        urlPermissions.add(new AuthorizedUrlPermission("/oauth/**", new String[]{}));
        urlPermissions.add(new AuthorizedUrlPermission("/login/oauth2/code/google", new String[]{}));

        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry =
                registerUrls(http, urlPermissions);

        registry.and()
                .formLogin().failureHandler(authFailureHandler)
                .successHandler(authSuccessHandler)
                .loginPage(LOGIN_URL)
                .loginProcessingUrl(LOGIN_PROCESSING_URL)
                .usernameParameter("username")
                .passwordParameter("password")
                .and()
                .logout()
                .permitAll()
                .logoutUrl(LOGOUT_URL)
                .addLogoutHandler(logoutHandler)
                .logoutSuccessHandler(logoutSuccessHandler)
                .deleteCookies("JSESSIONID");

        if (!"".equals(oAuth2Config.getGoogleClientId())) {
            registry.and()
                    .oauth2Login()
                    .successHandler(authSuccessHandler)
                    .failureHandler(authFailureHandler)
                    .loginPage(LOGIN_URL)
                    .userInfoEndpoint()
                    .userService(customOAuth2UserService);
        }
    }

    public static void setSuccessUrl(String successUrl) {
        DefaultSecurityConfig.successUrl = successUrl;
    }

    public static void setAdminSuccessUrl(String adminSuccessUrl) {
        DefaultSecurityConfig.adminSuccessUrl = adminSuccessUrl;
    }

    private ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registerUrls(
            HttpSecurity http, List<AuthorizedUrlPermission> urlPermissions) throws Exception {

        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry =
                http.authorizeRequests();

        for(AuthorizedUrlPermission urlPermission : urlPermissions) {
            String[] roles = urlPermission.getRoles();
            ExpressionUrlAuthorizationConfigurer<HttpSecurity>.AuthorizedUrl authorizedUrl =
                    registry.antMatchers(urlPermission.getUrl());

            if (roles != null && roles.length == 0) {
                registry = authorizedUrl.permitAll();
            } else if (roles != null) {
                registry = authorizedUrl.hasAnyRole(urlPermission.getRoles());
            }
        }

        return registry;
    }
}
