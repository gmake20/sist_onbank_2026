package org.doit.onbank.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 직원(/employee/**)·관리자(/admin/**) 로그인과 영역 분리 확인
// @Transactional : 테스트 계정은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
// 직원 소속 지점이 필요하므로 공용 DB에 branch 시드 데이터(data-team1-mysql.sql)가 있어야 한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackOfficeLoginTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private AdminRepository adminRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String staffNo = "S" + suffix;	// 사번은 10자 이하
	private final String managerNo = "M" + suffix;
	private final String adminId = "testadmin" + suffix;
	private final String rawPassword = "Test1234!";

	@BeforeEach
	void saveAccounts() {
		Long branchId = jdbcTemplate.queryForObject(
				"SELECT MIN(branch_id) FROM branch WHERE branch_type = 'BRANCH'", Long.class);

		employeeRepository.saveAndFlush(newEmployee(staffNo, EmployeeRole.STAFF, branchId));
		employeeRepository.saveAndFlush(newEmployee(managerNo, EmployeeRole.MANAGER, branchId));
		adminRepository.saveAndFlush(Admin.builder()
				.loginId(adminId)
				.password(passwordEncoder.encode(rawPassword))
				.name("관리자테스트")
				.role(AdminRole.SUPER_ADMIN)
				.tempPassword(false)
				.build());
	}

	private Employee newEmployee(String employeeNo, EmployeeRole role, Long branchId) {
		return Employee.builder()
				.employeeNo(employeeNo)
				.password(passwordEncoder.encode(rawPassword))
				.name("직원테스트")
				.email(employeeNo + "@onbank.test")
				.branchId(branchId)
				.position("대리")
				.role(role)
				.hiredAt(LocalDate.of(2020, 1, 1))
				.tempPassword(false)
				.build();
	}

	private MockHttpSession loginEmployee(String employeeNo) throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/employee/login")
						.userParameter("employeeNo").user(employeeNo).password(rawPassword))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	private MockHttpSession loginAdmin() throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/admin/login")
						.userParameter("loginId").user(adminId).password(rawPassword))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	// ---------- 직원 로그인 ----------

	@Test
	void employeeLoginSuccess() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(staffNo).password(rawPassword))
				.andExpect(authenticated().withUsername(staffNo).withRoles("STAFF"))
				.andExpect(redirectedUrl("/employee"));
	}

	@Test
	void employeeLoginWrongPassword() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(staffNo).password("wrong"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?error"));
	}

	@Test
	void employeeHomeForStaffAndManager() throws Exception {
		mockMvc.perform(get("/employee").session(loginEmployee(staffNo))).andExpect(status().isOk());
		// MANAGER는 RoleHierarchy로 STAFF 화면도 사용
		mockMvc.perform(get("/employee").session(loginEmployee(managerNo))).andExpect(status().isOk());
	}

	@Test
	void employeeHomeRequiresLogin() throws Exception {
		mockMvc.perform(get("/employee"))
				.andExpect(redirectedUrl("/employee/login"));
	}

	// ---------- 관리자 로그인 ----------

	@Test
	void adminLoginSuccess() throws Exception {
		mockMvc.perform(formLogin("/admin/login").userParameter("loginId").user(adminId).password(rawPassword))
				.andExpect(authenticated().withUsername(adminId).withRoles("SUPER_ADMIN"))
				.andExpect(redirectedUrl("/admin"));
	}

	@Test
	void adminHomeAfterLogin() throws Exception {
		mockMvc.perform(get("/admin").session(loginAdmin())).andExpect(status().isOk());
	}

	// ---------- 영역 분리 ----------

	// 관리자 아이디로 직원 로그인 화면에서 로그인할 수 없다 (각 화면은 자기 테이블만 조회)
	@Test
	void adminAccountCannotLoginAsEmployee() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(adminId).password(rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?error"));
	}

	// 직원으로 로그인한 세션으로 관리자 영역에 들어갈 수 없다
	@Test
	void employeeSessionCannotAccessAdmin() throws Exception {
		mockMvc.perform(get("/admin").session(loginEmployee(managerNo)))
				.andExpect(redirectedUrl("/admin/login"));
	}

	// 관리자로 로그인한 세션으로 직원 영역에 들어갈 수 없다
	@Test
	void adminSessionCannotAccessEmployee() throws Exception {
		mockMvc.perform(get("/employee").session(loginAdmin()))
				.andExpect(redirectedUrl("/employee/login"));
	}

	// ---------- CSRF 토큰 만료 ----------

	// 로그인 화면을 띄워 둔 채 서버가 재시작되면 토큰이 맞지 않는다 → 403 대신 로그인 화면으로 (?expired)
	@Test
	void expiredCsrfTokenRedirectsToLoginPage() throws Exception {
		mockMvc.perform(post("/employee/login").with(csrf().useInvalidToken())
						.param("employeeNo", staffNo).param("password", rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?expired"));
		mockMvc.perform(post("/admin/login").with(csrf().useInvalidToken())
						.param("loginId", adminId).param("password", rawPassword))
				.andExpect(redirectedUrl("/admin/login?expired"));
		mockMvc.perform(post("/login").with(csrf().useInvalidToken())
						.param("loginId", "user1").param("password", rawPassword))
				.andExpect(redirectedUrl("/login?expired"));
	}

	// 직원으로 로그인한 세션은 고객 영역에서 로그인 상태가 아니다
	@Test
	void employeeSessionIsNotCustomerLogin() throws Exception {
		mockMvc.perform(get("/mybank").session(loginEmployee(staffNo)))
				.andExpect(redirectedUrl("/login"));
	}
}
