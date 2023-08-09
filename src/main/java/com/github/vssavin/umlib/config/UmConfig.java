package com.github.vssavin.umlib.config;

import com.github.vssavin.jcrypt.DefaultStringSafety;
import com.github.vssavin.jcrypt.StringSafety;
import com.github.vssavin.jcrypt.osplatform.OSPlatformCrypt;
import com.github.vssavin.umlib.security.SecureService;
import com.github.vssavin.umlib.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Base project configuration class.
 *
 * @author vssavin on 15.05.2022
 */
@Configuration
@PropertySource(value = "file:./conf.properties", encoding = "UTF-8")
public class UmConfig extends StorableConfig {
    @IgnoreField public static final String LOGIN_URL = "/login";
    @IgnoreField public static final String LOGIN_PROCESSING_URL = "/perform-login";
    @IgnoreField public static final String LOGOUT_URL = "/logout";
    @IgnoreField public static final String PERFORM_LOGOUT_URL = "/perform-logout";
    @IgnoreField public static final String REGISTRATION_URL = "/um/users/registration";
    @IgnoreField public static final String PERFORM_REGISTER_URL = "/um/users/perform-register";

    @IgnoreField private final String adminSuccessUrl;
    @IgnoreField private final String successUrl;

    @IgnoreField private static final String NAME_PREFIX = "application";
    @IgnoreField private static final String CONFIG_FILE = "conf.properties";

    private static final Logger log = LoggerFactory.getLogger(UmConfig.class);
    private String[] applicationArgs;
    private final ApplicationContext context;
    private final SecureService defaultSecureService;
    private final OSPlatformCrypt encryptPropertiesPasswordService;
    private final UmConfigurer umConfigurer;
    private SecureService authService;

    @Value("${" + NAME_PREFIX + ".url}")
    private String applicationUrl;

    @Value("${um.registration.allowed:true}")
    private boolean registrationAllowed;

    @Value("${um.login.title:}")
    private String loginTitle;

    private final List<AuthorizedUrlPermission> authorizedUrlPermissions = new ArrayList<>();

    private boolean permissionsUpdated = false;

    private final StringSafety stringSafety = new DefaultStringSafety();

    public UmConfig(ApplicationContext context, SecureService secureService, UmConfigurer umConfigurer,
                    @Qualifier("applicationSecureService") OSPlatformCrypt applicationSecureService) {
        super.setConfigFile(CONFIG_FILE);
        super.setNamePrefix(NAME_PREFIX);
        this.context = context;
        this.defaultSecureService = secureService;
        this.authService = defaultSecureService;
        this.umConfigurer = umConfigurer;
        this.encryptPropertiesPasswordService = applicationSecureService;

        initDefaultPermissions();
        this.authorizedUrlPermissions.addAll(umConfigurer.getPermissions());

        if (applicationArgs == null || applicationArgs.length == 0) {
            String[] args = getAppArgsFromContext(context);
            if (args.length > 0) {
                applicationArgs = args;
            }
        }

        adminSuccessUrl = umConfigurer.getAdminSuccessUrl();
        successUrl = umConfigurer.getSuccessUrl();

        initSecureService("");
        processArgs(applicationArgs);
        log.debug("Using auth service: {}", authService);
    }

    public SecureService getAuthService() {
        return authService;
    }

    public List<AuthorizedUrlPermission> getAuthorizedUrlPermissions() {
        return authorizedUrlPermissions;
    }

    public String getAdminSuccessUrl() {
        return adminSuccessUrl;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
        this.applicationUrl = applicationUrl;
    }

    public boolean isRegistrationAllowed() {
        return registrationAllowed;
    }

    public String getLoginTitle() {
        return loginTitle;
    }

    public Pattern getPasswordPattern() {
        return umConfigurer.getPasswordPattern();
    }

    public String getPasswordDoesntMatchPatternMessage() {
        return umConfigurer.getPasswordDoesntMatchPatternMessage();
    }

    public void updateAuthorizedPermissions() {
        if (!permissionsUpdated && !registrationAllowed) {
            int registrationIndex = -1;
            int performRegisterIndex = -1;

            for (int i = 0; i < authorizedUrlPermissions.size(); i++) {
                AuthorizedUrlPermission authorizedUrlPermission = authorizedUrlPermissions.get(i);
                if (authorizedUrlPermission.getUrl().equals(REGISTRATION_URL)) {
                    registrationIndex = i;
                } else if (authorizedUrlPermission.getUrl().equals(PERFORM_REGISTER_URL)) {
                    performRegisterIndex = i;
                }
            }

            if (registrationIndex != -1) {
                authorizedUrlPermissions.set(registrationIndex,
                        new AuthorizedUrlPermission(REGISTRATION_URL, new String[]{Role.getStringRole(Role.ROLE_ADMIN)}));
            }

            if (performRegisterIndex != -1) {
                authorizedUrlPermissions.set(performRegisterIndex,
                        new AuthorizedUrlPermission(PERFORM_REGISTER_URL, new String[]{Role.getStringRole(Role.ROLE_ADMIN)}));
            }

            permissionsUpdated = true;
        }
    }

    private String[] getAppArgsFromContext(ApplicationContext context) {
        try {
            Object appArgsBean = context.getBean("springApplicationArguments");
            Method sourceArgesMethod = appArgsBean.getClass().getMethod("getSourceArgs");
            String[] args = (String[]) sourceArgesMethod.invoke(appArgsBean);
            if (args != null && args.length > 0) {
                return args;
            }
        } catch (NoSuchBeanDefinitionException ignore) { //ignore
        } catch (NoSuchMethodException e) {
            log.error("Method \"getSourceArgs\" not found!", e);
        } catch (InvocationTargetException | IllegalAccessException e) {
            log.error("Method invocation error", e);
        }
        return new String[]{};
    }

    private void initSecureService(String secureServiceName) {
        if (context == null || defaultSecureService == null) {
            throw new IllegalStateException("Not initialized application context or default secure service!");
        }

        if (secureServiceName != null && !secureServiceName.isEmpty()) {
            authService = getSecureServiceByName(secureServiceName);
        } else {
            String authServiceName = System.getProperty("authService");
            if (authServiceName != null && !authServiceName.isEmpty()) {
                authService = getSecureServiceByName(authServiceName);
            } else {
                authService = umConfigurer.getSecureService();
            }
        }
    }

    private SecureService getSecureServiceByName(String serviceName) {
        SecureService secureService = null;
        boolean beanFound = true;
        try {
            secureService = (SecureService) context.getBean(serviceName + "SecureService");
        } catch (NoSuchBeanDefinitionException ignore) {
            try {
                secureService = (SecureService) context.getBean(serviceName.toUpperCase() + "SecureService");
            } catch (NoSuchBeanDefinitionException e) {
                beanFound = false;
            }
        }
        if (!beanFound) {
            throw new NoSuchSecureServiceException(String.format("Service with name %s not found!", serviceName));
        }
        return secureService;
    }

    private void processArgs(String[] args) {
        if (args != null && args.length > 0) {
            String argsString = Arrays.toString(args);
            log.debug("Application started with arguments: {}", argsString);
            Map<String, String> mappedArgs = getMappedArgs(args);
            String password = mappedArgs.get("ep");
            if (password != null) {
                String encrypted = encryptPropertiesPasswordService.encrypt(password, "");
                stringSafety.clearString(password);
                log.debug("Encryption for password [{}] : {}", password, encrypted);
                stringSafety.clearString(password);
            }
            String authServiceName = mappedArgs.get("authService");
            if (authServiceName != null) {
                initSecureService(authServiceName);
            }
        } else {
            log.warn("Unknown application arguments!");
        }
    }

    private void initDefaultPermissions() {
        authorizedUrlPermissions.add(new AuthorizedUrlPermission("/js/**", Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission("/css/**", Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(
                new AuthorizedUrlPermission("/um/users/passwordRecovery", Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(
                new AuthorizedUrlPermission("/um/users/perform-password-recovery", Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission(REGISTRATION_URL, Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission(PERFORM_REGISTER_URL, Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(
                new AuthorizedUrlPermission("/um/users/confirmUser", Permission.ANY_USER.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission("/um/admin**", Permission.ADMIN_ONLY.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission("/um/admin/**", Permission.ADMIN_ONLY.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission("/um/users/**", Permission.USER_ADMIN.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission(LOGOUT_URL, Permission.USER_ADMIN.getRoles()));
        authorizedUrlPermissions.add(new AuthorizedUrlPermission(PERFORM_LOGOUT_URL, Permission.USER_ADMIN.getRoles()));
    }

    private static Map<String, String> getMappedArgs(String[] args) {
        Map<String, String> resultMap = new HashMap<>();
        if (args.length > 0) {
            for (String arg : args) {
                String[] params = arg.replace("--", "").split("=");
                if (params.length > 0) {
                    String value = params.length > 1 ? params[1] : "";
                    resultMap.put(params[0], value);
                }
            }
        }
        return resultMap;
    }

    private static class NoSuchSecureServiceException extends RuntimeException {

        NoSuchSecureServiceException(String message) {
            super(message);
        }
    }
}
