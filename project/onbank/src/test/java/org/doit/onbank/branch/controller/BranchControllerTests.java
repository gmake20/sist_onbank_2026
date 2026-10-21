package org.doit.onbank.branch.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.branch.dto.BranchView;
import org.doit.onbank.branch.entity.BranchServiceType;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.service.BranchService;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 지점/ATM 찾기 화면·API 테스트 : 비회원(로그인 없이)으로 접근한다. Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest({ BranchPageController.class, BranchApiController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = "onbank.kakao.js-key=test-kakao-key")
class BranchControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BranchService branchService;

	// OnbankApplication 의 @EnableJpaAuditing 이 JPA 설정을 찾으므로 가짜로 채운다 (이 테스트는 JPA 를 띄우지 않음)
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// SecurityConfig 의 로그인 설정 3개(회원·관리자·직원 FilterChain)가 요구하는 Bean
	@MockitoBean
	private MemberDetailsService memberDetailsService;

	@MockitoBean
	private AdminDetailsService adminDetailsService;

	@MockitoBean
	private EmployeeDetailsService employeeDetailsService;

	@MockitoBean
	private MemberLoginAttemptRecorder memberLoginAttemptRecorder;

	@MockitoBean
	private AdminLoginAttemptRecorder adminLoginAttemptRecorder;

	@MockitoBean
	private EmployeeLoginAttemptRecorder employeeLoginAttemptRecorder;

	// 화면 : 카카오맵 SDK 를 설정한 키로 불러오고, 업무 선택 목록을 보여 준다
	@Test
	void findPage() throws Exception {
		mockMvc.perform(get("/branches"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("sdk.js?appkey=test-kakao-key&amp;autoload=false")))
				.andExpect(content().string(containsString("<option value=\"ACCOUNT_OPENING\">계좌개설</option>")));
	}

	@Test
	void searchApi() throws Exception {
		given(branchService.search(BranchType.ATM, "강남", true, BranchServiceType.DEPOSIT)).willReturn(List.of(
				new BranchView(7L, BranchType.ATM, "ATM", "모의은행 강남역 ATM", "서울특별시 강남구", 37.4981, 127.0279,
						null, "24시간", true, List.of("입금", "출금"))));

		mockMvc.perform(get("/api/branches")
						.param("type", "ATM").param("keyword", "강남").param("open24h", "true").param("service", "DEPOSIT"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].branchId").value(7))
				.andExpect(jsonPath("$[0].name").value("모의은행 강남역 ATM"))
				.andExpect(jsonPath("$[0].open24h").value(true))
				.andExpect(jsonPath("$[0].services[1]").value("출금"));
	}

	// 조건 없이 부르면 전체 (open24h 는 기본 false)
	@Test
	void searchApiWithoutCondition() throws Exception {
		given(branchService.search(null, null, false, null)).willReturn(List.of());

		mockMvc.perform(get("/api/branches"))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void invalidTypeIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/branches").param("type", "SHOP"))
				.andExpect(status().isBadRequest());
	}
}
