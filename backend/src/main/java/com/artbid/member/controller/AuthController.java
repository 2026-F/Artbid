package com.artbid.member.controller;

import com.artbid.member.domain.Member;
import com.artbid.member.dto.LoginRequest;
import com.artbid.member.dto.LoginResponse;
import com.artbid.member.dto.MemberResponse;
import com.artbid.member.dto.SignUpRequest;
import com.artbid.member.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/signup")
    public ResponseEntity<MemberResponse> signUp(@Valid @RequestBody SignUpRequest request){
        Member member = authService.signUp(request.email(),request.password(),  request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(MemberResponse.from(member));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(){
        return ResponseEntity.noContent().build();
    }


}
