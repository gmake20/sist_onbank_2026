package org.doit.onbank.member.service;

import java.time.LocalDateTime;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.dto.MyInfo;
import org.doit.onbank.member.dto.ProfileEditForm;
import org.doit.onbank.member.dto.SignupForm;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 회원가입, 마이페이지 회원정보 조회·수정 (탈퇴는 MemberWithdrawService)
// 가입 즉시 정상(ACTIVE) + 실명확인 미확인(NONE). 직원 승인 없음 (README 5-2장)
@Service
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	// 형식 검증(SignupForm)과 비밀번호 확인 일치는 컨트롤러에서 끝난 상태로 호출된다
	@Transactional
	public Long signup(SignupForm form) {
		if (memberRepository.existsByLoginId(form.getLoginId())) {
			throw new BusinessException("이미 사용 중인 아이디입니다.");
		}
		if (memberRepository.existsByPhone(form.getPhone())) {
			throw new BusinessException("이미 가입된 휴대폰 번호입니다.");
		}
		if (memberRepository.existsByEmail(form.getEmail())) {
			throw new BusinessException("이미 가입된 이메일입니다.");
		}

		Member member = Member.builder()
				.loginId(form.getLoginId())
				.password(passwordEncoder.encode(form.getPassword()))
				.name(form.getName())
				.email(form.getEmail())
				.phone(form.getPhone())
				.birthDate(form.getBirthDate())
				.termsAgreedAt(LocalDateTime.now())
				.marketingAgree(form.isMarketingAgreed())
				.build();

		// 위 중복 검사 직후 같은 값으로 동시에 가입한 경우 DB UNIQUE 제약에서 걸린다
		try {
			return memberRepository.save(member).getMemberId();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException("이미 가입된 정보가 있습니다. 입력값을 확인해 주세요.");
		}
	}

	// 마이페이지 : 로그인한 본인 정보 조회
	@Transactional(readOnly = true)
	public MyInfo getMyInfo(Long memberId) {
		return memberRepository.findById(memberId)
				.map(MyInfo::from)
				.orElseThrow(() -> new BusinessException("회원 정보를 찾을 수 없습니다."));
	}

	// 마이페이지 : 회원정보 수정 (휴대폰, 이메일, 마케팅 수신 동의)
	// 형식 검증(ProfileEditForm)과 바뀐 값의 휴대폰·이메일 인증 확인은 컨트롤러에서 끝난 상태로 호출된다
	@Transactional
	public void updateProfile(Long memberId, ProfileEditForm form) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException("회원 정보를 찾을 수 없습니다."));
		if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
			throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
		}

		String email = form.getEmail().trim();
		// 바뀐 값만 중복 검사 (본인 값은 당연히 이미 있으므로)
		if (isEmailChanged(member.getEmail(), email) && memberRepository.existsByEmail(email)) {
			throw new BusinessException("이미 가입된 이메일입니다.");
		}
		if (!form.getPhone().equals(member.getPhone()) && memberRepository.existsByPhone(form.getPhone())) {
			throw new BusinessException("이미 가입된 휴대폰 번호입니다.");
		}

		member.changeContact(email, form.getPhone(), form.isMarketingAgreed());
		// 위 중복 검사 직후 다른 회원이 같은 값으로 바꾼 경우 DB UNIQUE 제약에서 걸린다
		try {
			memberRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException("이미 가입된 정보가 있습니다. 입력값을 확인해 주세요.");
		}
	}

	// 이메일은 대소문자를 구분하지 않는다 (인증 세션 키, DB 비교 모두)
	public static boolean isEmailChanged(String current, String input) {
		return current == null || input == null || !current.trim().equalsIgnoreCase(input.trim());
	}
}
