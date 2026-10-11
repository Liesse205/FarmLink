package com.farmlink.controller;

import com.farmlink.dto.MeResponse;
import com.farmlink.dto.PhoneUpdateRequest;
import com.farmlink.dto.RoleSelectRequest;
import com.farmlink.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;

    @GetMapping("/me")
    public MeResponse me(Principal principal) {
        return MeResponse.from(userService.getCurrentUser(principal));
    }

    /**
     * Stores the caller's own optional phone number (blank or null clears it).
     * The phone number is only ever returned on /api/me and, for the counterpart of
     * an accepted purchase request, on /api/requests/{id}/contact.
     */
    @PutMapping("/me/phone")
    public MeResponse updatePhone(@Valid @RequestBody PhoneUpdateRequest request, Principal principal) {
        return MeResponse.from(userService.updatePhone(principal, request.phone()));
    }

    @PostMapping("/me/role")
    public MeResponse selectRole(@Valid @RequestBody RoleSelectRequest request,
                                 Principal principal,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        var user = userService.selectRole(principal, request.role());
        refreshSessionAuthorities(user.getRole(), httpRequest, httpResponse);
        return MeResponse.from(user);
    }

    private void refreshSessionAuthorities(com.farmlink.entity.Role role,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication current = context.getAuthentication();
        if (current == null) {
            return;
        }
        List<GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        UsernamePasswordAuthenticationToken refreshed =
                new UsernamePasswordAuthenticationToken(current.getPrincipal(), current.getCredentials(), authorities);
        refreshed.setDetails(current.getDetails());
        context.setAuthentication(refreshed);

        securityContextRepository.saveContext(context, request, response);
    }
}
