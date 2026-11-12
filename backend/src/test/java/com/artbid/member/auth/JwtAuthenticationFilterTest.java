package com.artbid.member.auth;

import com.artbid.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider provider = mock(JwtTokenProvider.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(provider);

    @Test
    void 유효한_토큰이면_요청당_한_번만_해석해서_회원ID와_역할을_넘긴다() throws Exception {
        when(provider.parse("good-token")).thenReturn(new JwtTokenProvider.TokenClaims(7L, MemberRole.ADMIN));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer good-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertThat(passed.getUserPrincipal().getName()).isEqualTo("7");
        assertThat(passed.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE)).isEqualTo(MemberRole.ADMIN);
        // 서명 검증은 요청 하나에 한 번만 (예전에는 parseMemberId, parseRole로 두 번 했다)
        verify(provider, times(1)).parse("good-token");
        verify(provider, never()).parseMemberId(anyString());
        verify(provider, never()).parseRole(anyString());
    }

    @Test
    void 역할이_없는_토큰이면_회원ID만_넘기고_역할_속성은_넣지_않는다() throws Exception {
        when(provider.parse("legacy-token")).thenReturn(new JwtTokenProvider.TokenClaims(5L, null));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer legacy-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertThat(passed.getUserPrincipal().getName()).isEqualTo("5");
        assertThat(passed.getAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE)).isNull();
    }

    @Test
    void 유효하지_않은_토큰이면_로그인하지_않은_요청으로_그대로_넘긴다() throws Exception {
        when(provider.parse("bad-token")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(((HttpServletRequest) chain.getRequest()).getUserPrincipal()).isNull();
    }

    @Test
    void Authorization_헤더가_없으면_토큰을_해석하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(provider, never()).parse(anyString());
    }
}
