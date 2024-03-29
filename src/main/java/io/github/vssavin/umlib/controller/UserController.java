package io.github.vssavin.umlib.controller;

import io.github.vssavin.umlib.config.LocaleConfig;
import io.github.vssavin.umlib.config.UmConfig;
import io.github.vssavin.umlib.dto.UserDto;
import io.github.vssavin.umlib.entity.Role;
import io.github.vssavin.umlib.entity.User;
import io.github.vssavin.umlib.exception.EmailNotFoundException;
import io.github.vssavin.umlib.exception.UserExistsException;
import io.github.vssavin.umlib.helper.SecurityHelper;
import io.github.vssavin.umlib.helper.ValidatingHelper;
import io.github.vssavin.umlib.language.UmLanguage;
import io.github.vssavin.umlib.service.EmailService;
import io.github.vssavin.umlib.service.SecureService;
import io.github.vssavin.umlib.service.UserService;
import io.github.vssavin.umlib.utils.UmUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

import static io.github.vssavin.umlib.helper.MvcHelper.*;

/**
 * @author vssavin on 23.12.21
 */
@RestController
@RequestMapping(UserController.USER_CONTROLLER_PATH)
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    static final String USER_CONTROLLER_PATH = "/um/users";
    private static final String PAGE_REGISTRATION = "registration";
    private static final String PAGE_CHANGE_PASSWORD = "changePassword";
    private static final String PAGE_CONFIRM_USER = "confirmUser";
    private static final String PAGE_USER_EDIT = "userEditYourself";
    private static final String PAGE_USER_CONTROL_PANEL = "userControlPanel";
    private static final String PERFORM_REGISTER_MAPPING = "/perform-register";
    private static final String PAGE_RECOVERY_PASSWORD = "passwordRecovery";
    private static final String PERFORM_PASSWORD_RECOVERY = "/perform-password-recovery";

    private static final Set<String> IGNORED_PARAMS = new HashSet<>();

    static {
        IGNORED_PARAMS.add("_csrf");
        IGNORED_PARAMS.add("newPassword");
        IGNORED_PARAMS.add("currentPassword");
    }

    private final Set<String> pageRegistrationParams;
    private final Set<String> pageChangePasswordParams;
    private final Set<String> pageConfirmUserParams;
    private final Set<String> pagePasswordRecoveryParams;
    private final Set<String> pageLoginParams;
    private final Set<String> pageUserEditParams;
    private final Set<String> pageUserControlPanelParams;

    private final UserService userService;
    private final SecureService secureService;
    private final EmailService emailService;
    private final UmConfig mainConfig;
    private final PasswordEncoder passwordEncoder;
    private final UmLanguage language;

    public UserController(UserService userService, UmUtil umUtil, EmailService emailService,
                          UmConfig umConfig, PasswordEncoder passwordEncoder, UmLanguage language,
                          LocaleConfig.LocaleSpringMessageSource loginMessageSource,
                          LocaleConfig.LocaleSpringMessageSource userEditYourselfMessageSource,
                          LocaleConfig.LocaleSpringMessageSource userControlPanelMessageSource,
                          LocaleConfig.LocaleSpringMessageSource changePasswordMessageSource,
                          LocaleConfig.LocaleSpringMessageSource registrationMessageSource,
                          LocaleConfig.LocaleSpringMessageSource confirmUserMessageSource,
                          LocaleConfig.LocaleSpringMessageSource passwordRecoveryMessageSource) {
        this.userService = userService;
        this.secureService = umUtil.getAuthService();
        this.emailService = emailService;
        this.mainConfig = umConfig;
        this.pageUserEditParams = userEditYourselfMessageSource.getKeys();
        this.pageUserControlPanelParams = userControlPanelMessageSource.getKeys();
        this.passwordEncoder = passwordEncoder;
        pageLoginParams = loginMessageSource.getKeys();
        this.language = language;
        pageRegistrationParams = registrationMessageSource.getKeys();
        pageChangePasswordParams = changePasswordMessageSource.getKeys();
        pageConfirmUserParams = confirmUserMessageSource.getKeys();
        pagePasswordRecoveryParams = passwordRecoveryMessageSource.getKeys();
    }


    @GetMapping(value = {"/" + PAGE_REGISTRATION, "/" + PAGE_REGISTRATION + ".html"})
    public ModelAndView registration(HttpServletRequest request, Model model,
                                     @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView;

        if (!mainConfig.getRegistrationAllowed()) {
            return getForbiddenModelAndView(request);
        }

        String authorizedName = SecurityHelper.getAuthorizedUserName(userService);
        if (!authorizedName.isEmpty()) {
            return getForbiddenModelAndView(request);
        }

        modelAndView = new ModelAndView(PAGE_REGISTRATION, model.asMap());
        modelAndView.addObject("userName", authorizedName);

        addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
        return modelAndView;
    }

    @PostMapping(PERFORM_REGISTER_MAPPING)
    public ModelAndView performRegister(HttpServletRequest request, HttpServletResponse response,
                                        @RequestParam final String login,
                                        @RequestParam final String username,
                                        @RequestParam final String email,
                                        @RequestParam final String password,
                                        @RequestParam final String confirmPassword,
                                        @RequestParam(required = false) final String role,
                                        @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView;
        if (!mainConfig.getRegistrationAllowed()) {
            return getForbiddenModelAndView(request);
        }

        String authorizedName = SecurityHelper.getAuthorizedUserName(userService);
        if (!authorizedName.isEmpty()) {
            return getForbiddenModelAndView(request);
        }

        User newUser;
        Role registerRole;
        registerRole = Role.getRole(role);

        if (!userService.accessGrantedForRegistration(registerRole, authorizedName)) {
            modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                    MessageKeys.AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                    secureService.getEncryptMethodNameForView(), lang);

            response.setStatus(500);
            return modelAndView;
        }

        boolean emailSendingFailed = false;
        try {
            if (!secureService.decrypt(password, secureService.getSecureKey(request.getRemoteAddr())).equals(
                    secureService.decrypt(confirmPassword, secureService.getSecureKey(request.getRemoteAddr())))) {
                modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                        MessageKeys.PASSWORDS_MUST_BE_IDENTICAL_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                response.setStatus(400);
                return modelAndView;
            }

            if (!ValidatingHelper.isValidEmail(email)) {
                modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                        MessageKeys.EMAIL_NOT_VALID_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                response.setStatus(400);
                return modelAndView;
            }

            try {
                userService.getUserByEmail(email);
                modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                        MessageKeys.EMAIL_EXISTS_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                response.setStatus(400);
                return modelAndView;
            } catch (EmailNotFoundException ignored) {
            }

            newUser = userService.registerUser(login, username,
                    passwordEncoder.encode(secureService.decrypt(password,
                            secureService.getSecureKey(request.getRemoteAddr()))),
                    email, registerRole);
            String url = String.format("%s%s/%s?login=%s&verificationId=%s&lang=%s", mainConfig.getApplicationUrl(),
                    USER_CONTROLLER_PATH, PAGE_CONFIRM_USER, login, newUser.getVerificationId(), lang);
            try {
                emailService.sendSimpleMessage(email,
                        String.format("User registration at %s", mainConfig.getApplicationUrl()),
                        String.format("Confirm user registration: %s", url));
            } catch (MailException mailException) {
                log.error("Sending email error!", mailException);
                emailSendingFailed = true;
            }

        } catch (UserExistsException e) {
            modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                    MessageKeys.USER_EXISTS_PATTERN.getMessageKey(), lang, username);
            addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            response.setStatus(400);
            return modelAndView;
        } catch (Exception e) {

            modelAndView = getErrorModelAndView(PAGE_REGISTRATION,
                    MessageKeys.CREATE_USER_ERROR_MESSAGE.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            response.setStatus(500);
            return modelAndView;
        }

        modelAndView = getSuccessModelAndView(PAGE_REGISTRATION,
                MessageKeys.USER_CREATED_SUCCESSFULLY_PATTERN.getMessageKey(), lang, newUser.getLogin());
        modelAndView.addObject("emailSendingFailed", emailSendingFailed);

        addObjectsToModelAndView(modelAndView, pageRegistrationParams, language,
                secureService.getEncryptMethodNameForView(), lang);

        return modelAndView;
    }

    @GetMapping(value = {"/" + PAGE_CHANGE_PASSWORD, "/" + PAGE_CHANGE_PASSWORD + ".html"},
            produces = {"application/json; charset=utf-8"})
    public ModelAndView changeUserPassword(HttpServletRequest request, HttpServletResponse response,
                                           @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView = new ModelAndView(PAGE_CHANGE_PASSWORD);
        String authorizedName = "";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            authorizedName = authentication.getName();
        }
        if (!isAuthorizedUser(authorizedName)) {
            modelAndView = getErrorModelAndView(PAGE_CHANGE_PASSWORD,
                    MessageKeys.AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
        }

        addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
        return modelAndView;
    }

    @PatchMapping(PAGE_CHANGE_PASSWORD)
    public ModelAndView performChangeUserPassword(HttpServletRequest request, HttpServletResponse response,
                                                  @RequestParam String currentPassword,
                                                  @RequestParam String newPassword,
                                                  @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView;
        try {
            String authorizedUserName = "";
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                authorizedUserName = authentication.getName();
            }
            if (isAuthorizedUser(authorizedUserName)) {
                if (authorizedUserName.toLowerCase().contains("anonymoususer")) {
                    modelAndView = getErrorModelAndView(PAGE_CHANGE_PASSWORD,
                            MessageKeys.AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
                    addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                            secureService.getEncryptMethodNameForView(), lang);
                    response.setStatus(403);
                    return modelAndView;
                }
                User user = userService.getUserByLogin(authorizedUserName);
                String realNewPassword = secureService.decrypt(newPassword,
                        secureService.getSecureKey(request.getRemoteAddr()));
                String realCurrentPassword = secureService.decrypt(currentPassword,
                        secureService.getSecureKey(request.getRemoteAddr()));
                if (user != null) {
                    if (passwordEncoder.matches(realCurrentPassword, user.getPassword())) {
                        user.setPassword(passwordEncoder.encode(realNewPassword));
                        userService.updateUser(user);
                    } else {
                        modelAndView = getErrorModelAndView(PAGE_CHANGE_PASSWORD,
                                MessageKeys.WRONG_PASSWORD_MESSAGE.getMessageKey(), lang);
                        response.setStatus(500);
                        addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                                secureService.getEncryptMethodNameForView(), lang);
                        return modelAndView;
                    }

                }
            }
            else {
                modelAndView = getErrorModelAndView(PAGE_CHANGE_PASSWORD,
                        MessageKeys.AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
                response.setStatus(500);
                addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                return modelAndView;
            }

        }
        catch (Exception ex) {
            modelAndView = getErrorModelAndView(PAGE_CHANGE_PASSWORD,
                    MessageKeys.REQUEST_PROCESSING_ERROR.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            response.setStatus(500);
            return modelAndView;
        }

        modelAndView = getSuccessModelAndView(PAGE_CHANGE_PASSWORD,
                MessageKeys.PASSWORD_SUCCESSFULLY_CHANGED_MESSAGE.getMessageKey(), lang);
        response.setStatus(200);
        addObjectsToModelAndView(modelAndView, pageChangePasswordParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);

        return modelAndView;
    }

    @GetMapping(value = {"/" + PAGE_CONFIRM_USER, "/" + PAGE_CONFIRM_USER + ".html"})
    public ModelAndView confirmUser(HttpServletRequest request, HttpServletResponse response,
                                           @RequestParam final String login,
                                           @RequestParam(required = false) String verificationId,
                                           @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView;
        boolean isAdminUser = false;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Collection<?> authorities = authentication.getAuthorities();
            for(Object authority : authorities) {
                if (authority.toString().equals("ROLE_ADMIN")) {
                    isAdminUser = true;
                }
            }
        }

        String resultMessage = LocaleConfig.getMessage(PAGE_CONFIRM_USER,
                MessageKeys.CONFIRM_SUCCESS_MESSAGE.getMessageKey(), lang);

        try {
            userService.confirmUser(login, verificationId, isAdminUser);
        } catch (Exception e) {
            resultMessage = LocaleConfig.getMessage(PAGE_CONFIRM_USER,
                    MessageKeys.CONFIRM_FAILED_MESSAGE.getMessageKey(), lang);
        }

        modelAndView = new ModelAndView(PAGE_CONFIRM_USER);
        modelAndView.addObject("confirmMessage", resultMessage);

        addObjectsToModelAndView(modelAndView, pageConfirmUserParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
        return modelAndView;
    }


    @GetMapping(value = {"/" + PAGE_RECOVERY_PASSWORD, "/" + PAGE_RECOVERY_PASSWORD + ".html"})
    public ModelAndView passwordRecovery(HttpServletRequest request,
                                         @RequestParam(required = false, defaultValue = "") final String recoveryId,
                                         @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView = new ModelAndView(PAGE_RECOVERY_PASSWORD);
        boolean successSend = true;
        if (!recoveryId.isEmpty()) {
            try {
                User user = userService.getUserByRecoveryId(recoveryId);
                String newPassword = userService.generateNewUserPassword(recoveryId);
                String message = "Your new password: " + newPassword;
                emailService.sendSimpleMessage(user.getEmail(), "Your new password: ", message);
            } catch (UsernameNotFoundException usernameNotFoundException) {
                log.error("User not found! ", usernameNotFoundException);
                modelAndView.addObject("userNotFound", true);
                successSend = false;
            } catch (MailException mailException) {
                log.error("Failed to send an email!" , mailException);
                modelAndView.addObject("failedSend", true);
                successSend = false;
            }
            modelAndView.addObject("successSend", successSend);
        }

        addObjectsToModelAndView(modelAndView, pagePasswordRecoveryParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
        return modelAndView;
    }


    @PostMapping(PERFORM_PASSWORD_RECOVERY)
    public ModelAndView performPasswordRecovery(HttpServletRequest request,
                                                @RequestParam String loginOrEmail,
                                                @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView = new ModelAndView("redirect:" + PAGE_RECOVERY_PASSWORD);
        boolean successSend = true;
        try {
            Map<String, User> map = userService.getUserRecoveryId(loginOrEmail);
            Optional<String> optionalRecoveryId = map.keySet().stream().findFirst();

            if (optionalRecoveryId.isPresent()) {
                User user = map.get(optionalRecoveryId.get());
                String message = mainConfig.getApplicationUrl() + USER_CONTROLLER_PATH + "/" +
                        PAGE_RECOVERY_PASSWORD + "?recoveryId=" + optionalRecoveryId.get();
                emailService.sendSimpleMessage(user.getEmail(), "Password recovery", message);
            }
        } catch (UsernameNotFoundException usernameNotFoundException) {
            log.error("User not found: " + loginOrEmail + "! ", usernameNotFoundException);
            modelAndView.addObject("userNotFound", true);
            successSend = false;
        } catch (MailException mailException) {
            log.error("Failed to send an email!" , mailException);
            modelAndView.addObject("failedSend", true);
            successSend = false;
        }

        modelAndView.addObject("successSend", successSend);

        addObjectsToModelAndView(modelAndView, pagePasswordRecoveryParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
        return modelAndView;
    }

    @GetMapping(value = {"/" + PAGE_USER_EDIT + "/{login}", "/" + PAGE_USER_EDIT + ".html" + "/{login}"})
    public ModelAndView userEdit(HttpServletRequest request, HttpServletResponse response,
                                 @PathVariable String login,
                                 @RequestParam(required = false) final boolean success,
                                 @RequestParam(required = false) final String successMsg,
                                 @RequestParam(required = false) final boolean error,
                                 @RequestParam(required = false) final String errorMsg,
                                 @RequestParam(required = false) final String lang) {

        ModelAndView modelAndView = new ModelAndView(PAGE_USER_EDIT);
        User user;
        try {
            user = userService.getUserByLogin(login);

            if (!SecurityHelper.getAuthorizedUserLogin().equals(user.getLogin())) {
                modelAndView = getErrorModelAndView(UmConfig.LOGIN_URL,
                        MessageKeys.ADMIN_AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageLoginParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                response.setStatus(403);
                addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
                return modelAndView;
            }
        } catch (Exception e) {
            log.error("User update error! ", e);
            modelAndView = getErrorModelAndView(UmConfig.LOGIN_URL,
                    MessageKeys.USER_EDIT_ERROR_MESSAGE.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageUserEditParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            return modelAndView;
        }

        modelAndView.addObject("user", user);

        addObjectsToModelAndView(modelAndView, pageUserEditParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);

        if (successMsg != null) {
            modelAndView.addObject("success", success);
            modelAndView.addObject("successMsg", successMsg);
        }

        if (errorMsg != null) {
            modelAndView.addObject("error", error);
            modelAndView.addObject("errorMsg", errorMsg);
        }

        return modelAndView;
    }

    @PatchMapping
    public ModelAndView performUserEdit(HttpServletRequest request, HttpServletResponse response,
                                        @ModelAttribute UserDto userDto,
                                        @RequestParam(required = false) final String lang) {

        ModelAndView modelAndView = new ModelAndView(PAGE_USER_EDIT);
        User newUser;
        try {
            User userFromDatabase = userService.getUserById(userDto.getId());

            if (!SecurityHelper.getAuthorizedUserLogin().equals(userFromDatabase.getLogin())) {
                modelAndView = getErrorModelAndView(UmConfig.LOGIN_URL,
                        MessageKeys.ADMIN_AUTHENTICATION_REQUIRED_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageLoginParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                response.setStatus(403);
                addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);
                modelAndView.setViewName(modelAndView.getViewName() + "/" + userDto.getLogin());
                return modelAndView;
            }

            if (!ValidatingHelper.isValidEmail(userDto.getEmail())) {
                modelAndView = getErrorModelAndView(PAGE_USER_EDIT,
                        MessageKeys.EMAIL_NOT_VALID_MESSAGE.getMessageKey(), lang);
                addObjectsToModelAndView(modelAndView, pageUserEditParams, language,
                        secureService.getEncryptMethodNameForView(), lang);
                modelAndView.setViewName(modelAndView.getViewName() + "/" + userFromDatabase.getLogin());
                response.setStatus(400);
                return modelAndView;
            }

            newUser = User.builder().id(userFromDatabase.getId()).login(userFromDatabase.getLogin())
                    .name(userDto.getName()).password(userFromDatabase.getPassword())
                    .email(userDto.getEmail()).authority(userFromDatabase.getAuthority())
                    .expirationDate(userFromDatabase.getExpirationDate())
                    .verificationId(userFromDatabase.getVerificationId())
                    .build();
            newUser = userService.updateUser(newUser);
            modelAndView.addObject("user", newUser);
            modelAndView.addObject("success", true);
            String successMsg = LocaleConfig
                    .getMessage("userEdit", MessageKeys.USER_EDIT_SUCCESS_MESSAGE.getMessageKey(), lang);
            modelAndView.addObject("successMsg", successMsg);
        } catch (Exception e) {
            log.error("User update error! ", e);
            modelAndView = getErrorModelAndView(UmConfig.LOGIN_URL,
                    MessageKeys.USER_EDIT_ERROR_MESSAGE.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageUserEditParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            return modelAndView;
        }

        modelAndView = new ModelAndView("redirect:" + USER_CONTROLLER_PATH +
                "/" +  PAGE_USER_EDIT + "/" + newUser.getLogin());

        addObjectsToModelAndView(modelAndView, PAGE_USER_EDIT, pageUserEditParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);

        String successMsg = LocaleConfig.getMessage(PAGE_USER_EDIT,
                MessageKeys.USER_EDIT_SUCCESS_MESSAGE.getMessageKey(), lang);
        modelAndView.addObject("success", true);
        modelAndView.addObject("successMsg", successMsg);
        return modelAndView;
    }

    @GetMapping(value = {"/" + PAGE_USER_CONTROL_PANEL, "/" + PAGE_USER_CONTROL_PANEL + ".html"})
    public ModelAndView userControlPanel(HttpServletRequest request, HttpServletResponse response,
                                 @RequestParam(required = false) final boolean success,
                                 @RequestParam(required = false) final String successMsg,
                                 @RequestParam(required = false) final boolean error,
                                 @RequestParam(required = false) final String errorMsg,
                                 @RequestParam(required = false) final String lang) {

        ModelAndView modelAndView = new ModelAndView(PAGE_USER_CONTROL_PANEL);
        User user;
        try {
            String login = SecurityHelper.getAuthorizedUserLogin();
            user = userService.getUserByLogin(login);
        } catch (Exception e) {
            log.error("User update error! ", e);
            modelAndView = getErrorModelAndView(UmConfig.LOGIN_URL,
                    MessageKeys.USER_EDIT_ERROR_MESSAGE.getMessageKey(), lang);
            addObjectsToModelAndView(modelAndView, pageUserControlPanelParams, language,
                    secureService.getEncryptMethodNameForView(), lang);
            return modelAndView;
        }

        modelAndView.addObject("user", user);

        addObjectsToModelAndView(modelAndView, pageUserControlPanelParams, language,
                secureService.getEncryptMethodNameForView(), lang);
        addObjectsToModelAndView(modelAndView, request.getParameterMap(), IGNORED_PARAMS);

        if (successMsg != null) {
            modelAndView.addObject("success", success);
            modelAndView.addObject("successMsg", successMsg);
        }

        if (errorMsg != null) {
            modelAndView.addObject("error", error);
            modelAndView.addObject("errorMsg", errorMsg);
        }

        return modelAndView;
    }

    private ModelAndView getForbiddenModelAndView(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null) referer = UmConfig.successUrl;
        ModelAndView modelAndView = new ModelAndView("redirect:" + referer);
        modelAndView.setStatus(HttpStatus.FORBIDDEN);
        return modelAndView;
    }

    private boolean isAuthorizedUser(String userName) {
        return (userName != null && !userName.isEmpty());
    }

}
