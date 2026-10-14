package com.artbid.member.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.Principal;


// 헤더가 유효하면 회원 ID를 반환하도록 감싸줌
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith(PREFIX)) {
            Long memberId = jwtTokenProvider.parseMemberId(header.substring(PREFIX.length()));
            if (memberId != null) {
                filterChain.doFilter(new PrincipalRequestWrapper(request, memberId), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }


    private static class PrincipalRequestWrapper extends HttpServletRequestWrapper {
        private final Principal principal;

        PrincipalRequestWrapper(HttpServletRequest request, Long memberId) {
            super(request);
            this.principal = () -> String.valueOf(memberId);
        }

        @Override
        public Principal getUserPrincipal() {
            return principal;
        }
    }
}