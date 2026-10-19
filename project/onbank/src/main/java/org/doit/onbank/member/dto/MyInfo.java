package org.doit.onbank.member.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.VerificationStatus;

// 마이페이지 > 회원정보 조회 화면에 보여 줄 값 (비밀번호 해시, 로그인 실패 횟수 등은 넣지 않는다)
public record MyInfo(String loginId, String name, LocalDate birthDate, String phone, String email,
		VerificationStatus verificationStatus, boolean marketingAgree, LocalDateTime joinedAt) {

	public static MyInfo from(Member member) {
		return new MyInfo(member.getLoginId(), member.getName(), member.getBirthDate(), member.getPhone(),
				member.getEmail(), member.getVerificationStatus(), member.isMarketingAgree(), member.getCreatedAt());
	}

	// 화면 표시용 : 숫자만 저장된 휴대폰 번호에 하이픈을 넣는다 (01012345678 → 010-1234-5678)
	public String formattedPhone() {
		if (phone == null) {
			return "";
		}
		if (phone.length() == 11) {
			return phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
		}
		if (phone.length() == 10) {
			return phone.substring(0, 3) + "-" + phone.substring(3, 6) + "-" + phone.substring(6);
		}
		return phone;
	}
}
