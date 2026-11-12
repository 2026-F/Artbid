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
            // 요청당 한 번만 서명을 검증하고, 회원 ID와 역할을 같이 꺼낸다.
            // (예전에는 parseMemberId와 parseRole을 따로 불러서 검증을 두 번 했다)
            JwtTokenProvider.TokenClaims claims = jwtTokenProvider.parse(token);
            if (claims != null) {
                if (claims.role() != null) {
                    request.setAttribute(ROLE_ATTRIBUTE, claims.role());
                }
                filterChain.doFilter(new PrincipalRequestWrapper(request, claims.memberId()), response);
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