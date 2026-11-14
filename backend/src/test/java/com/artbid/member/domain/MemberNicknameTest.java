package com.artbid.member.domain;

import com.artbid.member.dto.MemberResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberNicknameTest {

	private Member member() {
		return Member.signUp("member@example.com", "hashed", "김실명", MemberRole.BIDDER);
	}

	@Test
	void 가입_직후에는_닉네임이_없다() {
		Member member = member();

		assertThat(member.hasNickname()).isFalse();
		assertThat(MemberResponse.from(member).nickname()).isNull();
	}

	@Test
	void 닉네임은_앞뒤_공백을_지우고_저장한다() {
		Member member = member();

		member.updateNickname("  그림좋아 ");

		assertThat(member.getNickname()).isEqualTo("그림좋아");
		assertThat(member.hasNickname()).isTrue();
	}

	@Test
	void 빈_값이나_null로는_기존_닉네임을_지우지_않는다() {
		Member member = member();
		member.updateNickname("그림좋아");

		member.updateNickname(null);
		member.updateNickname("   ");

		assertThat(member.getNickname()).isEqualTo("그림좋아");
	}

	@Test
	void 닉네임을_바꿔도_이름은_그대로다() {
		Member member = member();

		member.updateNickname("그림좋아");

		assertThat(member.getName()).isEqualTo("김실명");
	}
}
