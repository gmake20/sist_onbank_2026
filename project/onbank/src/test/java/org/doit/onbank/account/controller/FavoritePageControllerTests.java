package org.doit.onbank.account.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountFavorite;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.service.AccountFavoriteService;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.security.MemberDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 즐겨찾기 관리 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(FavoritePageController.class)
@Import(SecurityConfig.class)
class FavoritePageControllerTests {

	private static final Long MEMBER_ID = 100L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountFavoriteService accountFavoriteService;

	@MockitoBean
	private AccountService accountService;

	// OnbankApplication 의 @EnableJpaAuditing 이 JPA 설정을 찾으므로 가짜로 채운다 (이 테스트는 JPA 를 띄우지 않음)
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// SecurityConfig 의 로그인 설정 3개(회원·관리자·직원 FilterChain)가 요구하는 Bean (로그인은 user(me)로 대신하므로 가짜)
	@MockitoBean
	private MemberDetailsService memberDetailsService;

	@MockitoBean
	private AdminDetailsService adminDetailsService;

	@MockitoBean
	private EmployeeDetailsService employeeDetailsService;

	private LoginMember me;

	@BeforeEach
	void setUp() {
		Member member = Member.builder()
				.loginId("tester")
				.password("{noop}dummy")
				.name("테스트")
				.birthDate(LocalDate.of(2000, 1, 1))
				.build();
		ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
		me = new LoginMember(member);

		Account account = Account.builder()
				.memberId(MEMBER_ID)
				.accountNumber("1002-000-000001")
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", 1L);
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account));
	}

	private AccountFavorite favorite(String alias) {
		AccountFavorite favorite = AccountFavorite.builder()
				.accountId(1L)
				.recipientAccountNumber("1002-111-222222")
				.recipientAccountName("김*수")
				.favoriteAlias(alias)
				.favoriteCreatedAt(LocalDateTime.of(2026, 10, 16, 10, 0))
				.sortOrder(1)
				.build();
		ReflectionTestUtils.setField(favorite, "accountFavoriteId", 5L);
		return favorite;
	}

	@Test
	void listRequiresLogin() throws Exception {
		mockMvc.perform(get("/mybank/favorites"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void listShowsFavorites() throws Exception {
		given(accountFavoriteService.getMyFavorites(MEMBER_ID)).willReturn(List.of(favorite("엄마")));

		mockMvc.perform(get("/mybank/favorites").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("엄마"),
						containsString("ONBANK 1002-111-222222"),
						containsString("김*수"),
						containsString("1 / 20개"),
						containsString("from=1"),
						containsString("to=1002-111-222222"))));
	}

	@Test
	void listShowsEmptyMessage() throws Exception {
		given(accountFavoriteService.getMyFavorites(MEMBER_ID)).willReturn(List.of());

		mockMvc.perform(get("/mybank/favorites").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록된 즐겨찾기가 없습니다.")));
	}

	@Test
	void addRedirectsWithMessage() throws Exception {
		mockMvc.perform(post("/mybank/favorites").with(user(me)).with(csrf())
						.param("accountId", "1")
						.param("recipientAccountNumber", "1002-111-222222")
						.param("favoriteAlias", "엄마"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/favorites"))
				.andExpect(flash().attribute("message", "즐겨찾기에 등록했습니다."));
		verify(accountFavoriteService).add(MEMBER_ID, 1L, "1002-111-222222", "엄마", null);
	}

	@Test
	void addRejectedShowsMessage() throws Exception {
		given(accountFavoriteService.add(MEMBER_ID, 1L, "1002-111-222222", null, null))
				.willThrow(new IllegalArgumentException("이미 즐겨찾기에 등록된 계좌입니다."));

		mockMvc.perform(post("/mybank/favorites").with(user(me)).with(csrf())
						.param("accountId", "1")
						.param("recipientAccountNumber", "1002-111-222222"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("이미 즐겨찾기에 등록된 계좌입니다.")));
	}

	@Test
	void addInputCheckedBeforeService() throws Exception {
		mockMvc.perform(post("/mybank/favorites").with(user(me)).with(csrf())
						.param("accountId", "1")
						.param("recipientAccountNumber", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("입금계좌번호를 입력하세요.")));
		verify(accountFavoriteService, never()).add(anyLong(), anyLong(), anyString(), any(), any());
	}

	@Test
	void deleteRedirectsWithMessage() throws Exception {
		mockMvc.perform(post("/mybank/favorites/5/delete").with(user(me)).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/favorites"))
				.andExpect(flash().attribute("message", "즐겨찾기를 삭제했습니다."));
		verify(accountFavoriteService).delete(MEMBER_ID, 5L);
	}

	@Test
	void deleteOthersFavoriteShowsError() throws Exception {
		willThrow(new NoSuchElementException("즐겨찾기가 없습니다.")).given(accountFavoriteService).delete(MEMBER_ID, 9L);

		mockMvc.perform(post("/mybank/favorites/9/delete").with(user(me)).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", "즐겨찾기가 없습니다."));
	}

	// ---------- 전계좌조회에서 ☆/★ ----------

	@Test
	void addFromAccountListReturnsToList() throws Exception {
		mockMvc.perform(post("/mybank/favorites").with(user(me)).with(csrf())
						.param("accountId", "1")
						.param("recipientAccountNumber", "1002-000-000001")
						.param("returnTo", "accounts"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/accounts"));
	}

	// 전계좌조회에서 실패하면 오류 문구를 들고 전계좌조회로 돌아간다
	@Test
	void addFromAccountListFailureReturnsToList() throws Exception {
		given(accountFavoriteService.add(MEMBER_ID, 1L, "1002-000-000001", null, null))
				.willThrow(new IllegalStateException("즐겨찾기는 20개까지 등록할 수 있습니다."));

		mockMvc.perform(post("/mybank/favorites").with(user(me)).with(csrf())
						.param("accountId", "1")
						.param("recipientAccountNumber", "1002-000-000001")
						.param("returnTo", "accounts"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/accounts"))
				.andExpect(flash().attribute("errorMessage", "즐겨찾기는 20개까지 등록할 수 있습니다."));
	}

	// 정해진 값이 아니면 다른 주소로 보내지 않고 관리 화면으로
	@Test
	void unknownReturnToGoesToFavorites() throws Exception {
		mockMvc.perform(post("/mybank/favorites/5/delete").with(user(me)).with(csrf())
						.param("returnTo", "https://example.com"))
				.andExpect(redirectedUrl("/mybank/favorites"));
	}

	// ---------- 수정·순서 이동 ----------

	@Test
	void editFormShowsCurrentValues() throws Exception {
		given(accountFavoriteService.getMyFavorite(MEMBER_ID, 5L)).willReturn(favorite("엄마"));

		mockMvc.perform(get("/mybank/favorites/5/edit").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("즐겨찾기 수정"),
						containsString("ONBANK 1002-111-222222"),
						containsString("value=\"엄마\""))));
	}

	@Test
	void editRedirectsWithMessage() throws Exception {
		mockMvc.perform(post("/mybank/favorites/5/edit").with(user(me)).with(csrf())
						.param("favoriteAlias", "월세")
						.param("memo", "매달 25일"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/favorites"))
				.andExpect(flash().attribute("message", "즐겨찾기를 수정했습니다."));
		verify(accountFavoriteService).update(MEMBER_ID, 5L, "월세", "매달 25일");
	}

	@Test
	void editInputChecked() throws Exception {
		given(accountFavoriteService.getMyFavorite(MEMBER_ID, 5L)).willReturn(favorite("엄마"));

		mockMvc.perform(post("/mybank/favorites/5/edit").with(user(me)).with(csrf())
						.param("favoriteAlias", "가".repeat(21)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("별칭은 20자까지 입력할 수 있습니다.")));
		verify(accountFavoriteService, never()).update(anyLong(), anyLong(), any(), any());
	}

	@Test
	void editOthersFavoriteRedirectsToList() throws Exception {
		given(accountFavoriteService.getMyFavorite(MEMBER_ID, 9L)).willThrow(new NoSuchElementException("즐겨찾기가 없습니다."));

		mockMvc.perform(get("/mybank/favorites/9/edit").with(user(me)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/favorites"))
				.andExpect(flash().attribute("errorMessage", "즐겨찾기가 없습니다."));
	}

	@Test
	void moveUp() throws Exception {
		mockMvc.perform(post("/mybank/favorites/5/move").param("direction", "up").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/favorites"));
		verify(accountFavoriteService).move(MEMBER_ID, 5L, true);
	}

	// 목록에서 맨 위는 ▲, 맨 아래는 ▼ 버튼이 없다
	@Test
	void listHidesMoveButtonsAtEnds() throws Exception {
		given(accountFavoriteService.getMyFavorites(MEMBER_ID)).willReturn(List.of(favorite("엄마")));

		mockMvc.perform(get("/mybank/favorites").with(user(me)))
				.andExpect(content().string(allOf(
						not(containsString("direction=up")),
						not(containsString("direction=down")),
						containsString("/mybank/favorites/5/edit"))));
	}
}
