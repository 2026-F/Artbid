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

	public void updateName(String name) {
		if (name != null && !name.isBlank()) {
			this.name = name.trim();
		}
	}
}
