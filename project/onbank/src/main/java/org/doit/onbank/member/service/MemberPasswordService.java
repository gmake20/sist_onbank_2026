package org.doit.onbank.member.service;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.mail.EmailSender;
import org.doit.onbank.common.security.PasswordChangeForm;
import org.doit.onbank.common.security.TempPasswordGenerator;
import org.doit.onbank.member.dto.PasswordFindForm;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 고객 비밀번호 찾기(임시 비밀번호 메일 발송)와 비밀번호 변경
// 임시 비밀번호는 로그에 남기지 않는다
@Service
@RequiredArgsConstructor
@Transactional
public class MemberPasswordService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final TempPasswordGenerator tempPasswordGenerator;
	private final EmailSender emailSender;

	// 아이디·이름·생년월일·이메일이 모두 일치하는 정상(ACTIVE)·잠김(LOCKED) 회원에게 임시 비밀번호를 메일로 보낸다
	// 일치하는 회원이 없어도 조용히 끝낸다 → 화면은 같은 문구로 안내 (가입 여부를 알려주지 않음)
	// 메일 발송에 실패하면 MailException → 롤백 (비밀번호가 바뀌지 않음)
	public void sendTempPassword(PasswordFindForm form) {
		Member member = memberRepository.findByLoginId(form.getLoginId().trim())
				.filter(m -> m.getStatus() == MemberStatus.ACTIVE || m.getStatus() == MemberStatus.LOCKED)
				.filter(m -> m.getName().equals(form.getName().trim()))
				.filter(m -> m.getBirthDate().equals(form.getBirthDate()))
				.filter(m -> m.getEmail() != null && m.getEmail().equalsIgnoreCase(form.getEmail().trim()))
				.orElse(null);
		if (member == null) {
			return;
		}

		String tempPassword = tempPasswordGenerator.generate();
		member.issueTempPassword(passwordEncoder.encode(tempPassword));
		memberRepository.flush();	// DB 반영이 실패하면 메일을 보내기 전에 멈춘다

		emailSender.send(member.getEmail(), "[ONBANK] 임시 비밀번호 안내", """
				%s님, 요청하신 임시 비밀번호를 안내해 드립니다.

				임시 비밀번호 : %s

				로그인하면 바로 새 비밀번호로 변경해야 이용할 수 있습니다.
				본인이 요청하지 않았다면 고객센터로 연락해 주세요.
				""".formatted(member.getName(), tempPassword));
	}

	// 본인 비밀번호 변경 (임시 비밀번호 → 새 비밀번호, 또는 일반 변경)
	// 형식 검사(@Valid)와 새 비밀번호 확인 일치는 컨트롤러에서 먼저 확인한다
	public void changePassword(Long memberId, PasswordChangeForm form) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException("회원 정보를 찾을 수 없습니다."));
		if (!passwordEncoder.matches(form.getCurrentPassword(), member.getPassword())) {
			throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
		}
		if (passwordEncoder.matches(form.getNewPassword(), member.getPassword())) {
			throw new BusinessException("현재 비밀번호와 다른 비밀번호를 입력하세요.");
		}
		member.changePassword(passwordEncoder.encode(form.getNewPassword()));
	}
}
