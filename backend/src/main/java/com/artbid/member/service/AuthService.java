package com.artbid.member.service;

import com.artbid.member.auth.JwtTokenProvider;
import com.artbid.member.domain.Member;
import com.artbid.member.domain.MemberRole;
import com.artbid.member.dto.LoginResponse;
import com.artbid.member.dto.MemberResponse;
import com.artbid.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public Member signUp(String email, String password,String name){

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다: " + email);
        }

        Member member = Member.signUp(email, passwordEncoder.encode(password), name, MemberRole.BIDDER);
        return memberRepository.save(member);
    }

    @Transactional
    public LoginResponse login(String email, String password){
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다. "));

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return new LoginResponse(jwtTokenProvider.generateToken(member.getId()), member.getId());

    }
}
