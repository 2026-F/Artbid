package com.artbid.member.auth;

import com.artbid.member.domain.MemberRole;
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

    // 토큰에서 뽑아낸 역할(MemberRole)을 request attribute로 꽂아둘 때 쓰는 키.
    // Principal.getName()은 계속 memberId만 반환해야 하는 기존 컨벤션(Payment/Streaming 컨트롤러 주석 참고)을
    // 깨지 않으려고, role은 별도 attribute로 넘긴다.
    public static final String ROLE_ATTRIBUTE = "memberRole";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length());
            Long memberId = jwtTokenProvider.parseMemberId(token);
            if (memberId != null) {
                MemberRole role = jwtTokenProvider.parseRole(token);
                if (role != null) {
                    request.setAttribute(ROLE_ATTRIBUTE, role);
                }
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