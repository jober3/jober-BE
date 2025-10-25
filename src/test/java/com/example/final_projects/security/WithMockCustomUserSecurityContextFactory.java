package com.example.final_projects.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WithMockCustomUserSecurityContextFactory implements WithSecurityContextFactory<WithMockCustomUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser customUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        List<String> roleNames = Arrays.stream(customUser.roles())
                .map(role -> "ROLE_" + role)
                .collect(Collectors.toList());

        CustomUserPrincipal principal = new CustomUserPrincipal(
                customUser.id(),
                customUser.email(),
                "",
                roleNames
        );

        List<GrantedAuthority> authorities = roleNames.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                authorities
        );

        context.setAuthentication(authentication);
        return context;
    }
}
