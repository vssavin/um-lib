package com.github.vssavin.umlib.domain.security.auth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.stereotype.Service;

/**
 * Service bean with DefaultOAuth2UserService implementation of o2Auth.
 *
 * @author vssavin on 07.09.2022
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

}