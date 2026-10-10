package com.farmlink.config;

import com.farmlink.entity.Role;
import com.farmlink.entity.User;
import com.farmlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    private final OidcUserService oidcDelegate = new OidcUserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = delegate.loadUser(userRequest);
        User user = upsertFarmLinkUser(oauth2User);

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("farmlinkUserId", user.getId());

        return new DefaultOAuth2User(authoritiesFor(user), attributes, "email");
    }

    public OidcUser loadOidcUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = oidcDelegate.loadUser(userRequest);
        User user = upsertFarmLinkUser(oidcUser);
        return new DefaultOidcUser(
                authoritiesFor(user),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "email");
    }

    private User upsertFarmLinkUser(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Google did not return an e-mail address", null));
        }
        String name = oauth2User.getAttribute("name");
        return userService.upsertGoogleUser(name == null || name.isBlank() ? email : name, email);
    }

    private static List<GrantedAuthority> authoritiesFor(User user) {
        Role role = user.getRole();
        return List.of(new SimpleGrantedAuthority("ROLE_" + (role == null ? "PENDING" : role.name())));
    }
}
