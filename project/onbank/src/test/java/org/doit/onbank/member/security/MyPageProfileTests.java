package org.doit.onbank.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.common.mail.EmailSender;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

// 마이페이지 회원정보 수정·회원 탈퇴 흐름 확인
// EmailSender는 가짜로 바꿔 실제 메일을 보내지 않고, 보낸 본문에서 인증번호를 꺼내 쓴다
// 휴대폰 인증은 끄고(번호는 형식·중복만 확인) 이메일 인증은 켠 상태로 실행한다
// @Transactional : 테스트 회원·계좌는 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"onbank.phone-verification.enabled=false",
		"onbank.email-verification.enabled=true" })
class MyPageProfileTests {

	private static final String PASSWORD = "Test1234!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private EmailSender emailSender;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String loginId = "mypage" + suffix;
	private final String email = loginId + "@onbank.test";
	private final String phone = "010" + String.format("%08d", Long.parseLong(suffix));
	private final MockHttpSession session = new MockHttpSession();

	private Member member;

	@BeforeEach
	void saveMember() {
		member = memberRepository.saveAndFlush(Member.builder()
				.loginId(loginId)
				.password(passwordEncoder.encode(PASSWORD))
				.name("마이페이지")
				.email(email)
				.phone(phone)
				.birthDate(LocalDate.of(2000, 1, 1))
				.termsAgreedAt(LocalDateTime.now())
				.build());
	}

	// ---------- 회원정보 수정 ----------

	@Test
	void editFormShowsCurrentValues() throws Exception {
		mockMvc.perform(get("/mybank/profile/edit").with(user(new LoginMember(member))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"" + email + "\"")))
				.andExpect(content().string(containsString("value=\"" + phone + "\"")));
	}

	// 이메일을 그대로 두면 인증 없이 휴대폰·마케팅 동의만 바뀐다
	@Test
	void editPhoneAndMarketingWithoutEmailChange() throws Exception {
		String newPhone = otherPhone();

		mockMvc.perform(edit(email, newPhone, true, PASSWORD))
				.andExpect(redirectedUrl("/mybank/profile"))
				.andExpect(flash().attribute("message", "회원정보가 수정되었습니다."));

		Member saved = reload();
		assertThat(saved.getPhone()).isEqualTo(newPhone);
		assertThat(saved.isMarketingAgree()).isTrue();
		assertThat(saved.getEmail()).isEqualTo(email);
	}

	@Test
	void wrongCurrentPasswordIsRejected() throws Exception {
		mockMvc.perform(edit(email, phone, true, "Wrong1234!"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasErrors("form"))
				.andExpect(content().string(containsString("현재 비밀번호가 올바르지 않습니다.")));

		assertThat(reload().isMarketingAgree()).isFalse();
	}

	// 바꾼 이메일은 인증해야 저장된다
	@Test
	void changedEmailNeedsVerification() throws Exception {
		mockMvc.perform(edit("new" + email, phone, false, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("form", "email", "notVerified"));

		assertThat(reload().getEmail()).isEqualTo(email);
	}

	@Test
	void verifiedEmailIsSaved() throws Exception {
		String newEmail = "new" + email;
		mockMvc.perform(post("/api/email/send").session(session).with(user(new LoginMember(member))).with(csrf())
						.param("email", newEmail))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/email/verify").session(session).with(user(new LoginMember(member))).with(csrf())
						.param("email", newEmail).param("code", sentCode(newEmail)))
				.andExpect(status().isOk());

		mockMvc.perform(edit(newEmail, phone, false, PASSWORD))
				.andExpect(redirectedUrl("/mybank/profile"));

		assertThat(reload().getEmail()).isEqualTo(newEmail);
	}

	// 다른 회원이 쓰는 휴대폰 번호로는 바꿀 수 없다 (휴대폰 인증을 끈 상태라 중복 검사로 막힘)
	@Test
	void duplicatePhoneIsRejected() throws Exception {
		Member other = memberRepository.saveAndFlush(Member.builder()
				.loginId("other" + suffix)
				.password(passwordEncoder.encode(PASSWORD))
				.name("다른회원")
				.email("other" + email)
				.phone(otherPhone())
				.birthDate(LocalDate.of(2000, 1, 1))
				.termsAgreedAt(LocalDateTime.now())
				.build());

		mockMvc.perform(edit(email, other.getPhone(), false, PASSWORD))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("이미 가입된 휴대폰 번호입니다.")));

		assertThat(reload().getPhone()).isEqualTo(phone);
	}

	// ---------- 회원 탈퇴 ----------

	// 탈퇴 : 잔액 0원 계좌 해지, 연락처 삭제, 로그아웃 → 다시 로그인 불가
	@Test
	void withdrawClosesEmptyAccountAndBlocksLogin() throws Exception {
		Account account = saveAccount(0L);

		mockMvc.perform(post("/mybank/profile/withdraw").with(user(new LoginMember(member))).with(csrf())
						.param("password", PASSWORD).param("agreed", "true"))
				.andExpect(redirectedUrl("/login?withdrawn"));

		Member saved = reload();
		assertThat(saved.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
		assertThat(saved.getWithdrawnAt()).isNotNull();
		assertThat(saved.getEmail()).isNull();
		assertThat(saved.getPhone()).isNull();
		assertThat(accountRepository.findById(account.getAccountId()).orElseThrow().getAccountStatus())
				.isEqualTo(AccountStatus.CLOSED);

		mockMvc.perform(formLogin("/login").user("loginId", loginId).password("password", PASSWORD))
				.andExpect(redirectedUrl("/login?disabled"));
	}

	// 잔액이 남은 계좌가 있으면 화면에서 미리 안내하고, 요청해도 탈퇴되지 않는다
	@Test
	void withdrawBlockedWhenBalanceRemains() throws Exception {
		saveAccount(10_000L);

		mockMvc.perform(get("/mybank/profile/withdraw").with(user(new LoginMember(member))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("잔액이 남아 있는 계좌가 1개 있습니다.")));

		mockMvc.perform(post("/mybank/profile/withdraw").with(user(new LoginMember(member))).with(csrf())
						.param("password", PASSWORD).param("agreed", "true"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("errorMessage"));

		assertThat(reload().getStatus()).isEqualTo(MemberStatus.ACTIVE);
	}

	@Test
	void withdrawNeedsPasswordAndAgreement() throws Exception {
		mockMvc.perform(post("/mybank/profile/withdraw").with(user(new LoginMember(member))).with(csrf())
						.param("password", PASSWORD))
				.andExpect(model().attribute("errorMessage", "탈퇴 안내 사항에 동의해 주세요."));
		mockMvc.perform(post("/mybank/profile/withdraw").with(user(new LoginMember(member))).with(csrf())
						.param("password", "Wrong1234!").param("agreed", "true"))
				.andExpect(model().attribute("errorMessage", "비밀번호가 올바르지 않습니다."));

		assertThat(reload().getStatus()).isEqualTo(MemberStatus.ACTIVE);
	}

	private RequestBuilder edit(String email, String phone, boolean marketing,
			String currentPassword) {
		return post("/mybank/profile/edit").session(session).with(user(new LoginMember(member))).with(csrf())
				.param("email", email)
				.param("phone", phone)
				.param("marketingAgreed", String.valueOf(marketing))
				.param("currentPassword", currentPassword);
	}

	// 형식(010 + 8자리)에 맞으면서 테스트 회원과 다른 번호 : 4번째 자리만 바꾼다
	private String otherPhone() {
		char c = phone.charAt(3) == '9' ? '8' : '9';
		return "010" + c + phone.substring(4);
	}

	private Account saveAccount(long balance) {
		return accountRepository.saveAndFlush(Account.builder()
				.memberId(member.getMemberId())
				.accountNumber("9999-" + suffix)
				.accountType(AccountType.CHECKING)
				.accountStatus(AccountStatus.ACTIVE)
				.balance(balance)
				.todayTransferAmount(0L)
				.build());
	}

	// 요청 처리 중 바뀐 값을 DB에서 다시 읽는다 (1차 캐시 비움)
	private Member reload() {
		entityManager.flush();
		entityManager.clear();
		return memberRepository.findById(member.getMemberId()).orElseThrow();
	}

	private String sentCode(String to) {
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq(to), anyString(), text.capture());
		Matcher matcher = Pattern.compile("인증번호 : (\\d{6})").matcher(text.getValue());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
