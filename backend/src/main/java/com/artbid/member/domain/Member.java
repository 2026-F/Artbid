package com.artbid.member.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String password;

	private String name;

	public static final int MAX_NICKNAME_LENGTH = 20;

	// 채팅처럼 다른 사람에게 보이는 곳에 실명 대신 쓰는 이름. 정하기 전에는 null이다. 중복 검사는 하지 않는다.
	@Column(length = MAX_NICKNAME_LENGTH)
	private String nickname;

	@Enumerated(EnumType.STRING)
	private MemberRole role;

	public static Member signUp(String email, String hashedPassword, String name, MemberRole role){

		Member member = new Member();
		member.email = email;
		member.password = hashedPassword;
		member.name = name;
		member.role = role;
		return member;
	}

	public void updateNickname(String nickname) {
		if (nickname != null && !nickname.isBlank()) {
			this.nickname = nickname.trim();
		}
	}

	public boolean hasNickname() {
		return nickname != null && !nickname.isBlank();
	}

	public void updateName(String name) {
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
	}
}
