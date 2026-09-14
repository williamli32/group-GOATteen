package com.goatteen.trading.auth.security;

import com.goatteen.trading.user.User;
import com.goatteen.trading.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {


    private final JwtService jwtService;
    private final UserRepository userRepository;


    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    )
            throws ServletException, IOException {


        String header =
                request.getHeader("Authorization");


        if (header == null ||
                !header.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }


        String token =
                header.substring(7);


        if (!jwtService.isTokenValid(token)) {

            filterChain.doFilter(request, response);
            return;
        }


        Long userId =
                jwtService.extractUserId(token);


        User user =
                userRepository.findWithRolesById(userId)
                        .orElse(null);


        if (user != null &&
                SecurityContextHolder
                        .getContext()
                        .getAuthentication() == null) {


            List<SimpleGrantedAuthority> authorities =
                    user.getRoles()
                            .stream()
                            .map(role ->
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + role.getName()
                                    )
                            )
                            .toList();


            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            authorities
                    );


            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        }


        filterChain.doFilter(request, response);
    }
}