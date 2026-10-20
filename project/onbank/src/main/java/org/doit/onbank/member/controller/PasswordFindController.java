package org.doit.onbank.member.controller;

import org.doit.onbank.member.dto.PasswordFindForm;
import org.doit.onbank.member.service.MemberPasswordService;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 비밀번호 찾기 (화면 5번) : 가입 정보 확인 → 가입한 이메일로 임시 비밀번호 발송
// 임시 비밀번호로 로그인하면 비밀번호 변경 화면(/mybank/profile/password)으로 이동한다
@Slf4j
@Controller
@RequiredArgsConstructor
public class PasswordFindController {

	private final MemberPasswordService memberPasswordService;

	@GetMapping("/password/find")
	public String findForm(Model model) {
		model.addAttribute("form", new PasswordFindForm());
		return "auth/password-find";
	}

	// 일치하는 회원이 있든 없든 같은 문구로 로그인 화면에 안내한다 (가입 여부를 알려주지 않음)
	@PostMapping("/password/find")
	public String find(@Valid @ModelAttribute("form") PasswordFindForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return "auth/password-find";
		}
		try {
			memberPasswordService.sendTempPassword(form);
		} catch (MailException e) {
			log.error("임시 비밀번호 메일 발송 실패", e);
			bindingResult.reject("mail", "메일 발송 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
			return "auth/password-find";
		}
		redirectAttributes.addFlashAttribute("msg",
				"입력하신 정보와 일치하는 회원이 있으면 가입한 이메일로 임시 비밀번호를 보냈습니다.");
		return "redirect:/login";
	}
}
