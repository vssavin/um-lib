package io.github.vssavin.umlib.controller;

import io.github.vssavin.umlib.config.LocaleConfig;
import io.github.vssavin.umlib.config.UmConfig;
import io.github.vssavin.umlib.helper.MvcHelper;
import io.github.vssavin.umlib.language.UmLanguage;
import io.github.vssavin.umlib.service.SecureService;
import io.github.vssavin.umlib.utils.UmUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Set;

/**
 * @author vssavin on 18.12.2021
 */
@Controller
public class AuthController {
    private static final String PAGE_LOGIN = UmConfig.LOGIN_URL.replaceAll("/", "");
    private static final String PAGE_LOGOUT = UmConfig.LOGOUT_URL.replaceAll("/", "");

    private static final String PERFORM_LOGOUT = "/perform-logout";

    private final Set<String> PAGE_LOGIN_PARAMS;
    private final Set<String> PAGE_LOGOUT_PARAMS;

    private final UmLanguage language;
    private final SecureService secureService;
    private final UmConfig umConfig;

    public AuthController(LocaleConfig.LocaleSpringMessageSource loginMessageSource,
                          LocaleConfig.LocaleSpringMessageSource logoutMessageSource,
                          UmLanguage language, UmUtil umUtil, UmConfig umConfig) {
        this.language = language;
        this.secureService = umUtil.getAuthService();
        this.umConfig = umConfig;
        PAGE_LOGIN_PARAMS = loginMessageSource.getKeys();
        PAGE_LOGOUT_PARAMS = logoutMessageSource.getKeys();
    }

    @GetMapping(value = {"/", UmConfig.LOGIN_URL, UmConfig.LOGIN_URL + ".html"})
    public ModelAndView getLogin(@RequestParam(required = false) final Boolean error,
                                 @RequestParam(required = false) final String lang) {
        ModelAndView modelAndView = null;
        if (error != null) {
            if (error) {
                modelAndView = new ModelAndView(PAGE_LOGIN);
            }
        }

        else {
            modelAndView = new ModelAndView(PAGE_LOGIN);
        }

        MvcHelper.addObjectsToModelAndView(modelAndView, PAGE_LOGIN_PARAMS, language,
                secureService.getEncryptMethodNameForView(), lang);

        if (modelAndView != null) {
            modelAndView.addObject("registrationAllowed", umConfig.getRegistrationAllowed());
        }

        return modelAndView;
    }

    @GetMapping(value = {UmConfig.LOGOUT_URL, UmConfig.LOGOUT_URL + ".html"})
    public ModelAndView getLogout(@RequestParam(required = false) final String lang) {
        ModelAndView modelAndView = new ModelAndView(PAGE_LOGOUT);
        MvcHelper.addObjectsToModelAndView(modelAndView, PAGE_LOGOUT_PARAMS, language,
                secureService.getEncryptMethodNameForView(), lang);
        return modelAndView;
    }

    @PostMapping(value = PERFORM_LOGOUT)
    public ModelAndView logout(@RequestParam(required = false) final String lang) {
        String language = lang != null ? "?lang=" + lang : "";
        return new ModelAndView("redirect:" + UmConfig.LOGIN_URL + language);
    }
}
