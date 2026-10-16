package org.doit.onbank.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminManageService;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// S-19 계정 목록 화면 테스트 : Controller + 화면 + SecurityConfig만 띄운다 (DB 접속 없음)
// AdminManageService는 가짜 → 검색 조건이 서비스에 맞게 넘어가는지, 화면이 오류 없이 그려지는지 확인

@WebMvcTest(AdminManageController.class)
@Import(SecurityConfig.class)
class AdminManageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminManageService adminManageService;

    // @EnableJpaAuditing 때문에 필요
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // SecurityConfig 필터 체인 3개가 요구하는 Bean (로그인은 user(me)로 대신하므로 가짜)
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

    private AdminPrincipal me;

    @BeforeEach
    void setUp() {
        // 공통 레이아웃이 principal.name, principal.role을 읽으므로 진짜 AdminPrincipal로 로그인
        me = new AdminPrincipal(admin("super01", "이총괄", AdminRole.SUPER_ADMIN));
    }

    @Test
    void redirectsToAdminLoginWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/admin/system/admins"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        verify(adminManageService, never()).searchAdmins(any(), any(), any());
    }

    @Test
    void listsAllWhenNoCondition() throws Exception {
        given(adminManageService.searchAdmins(null, null, null))
                .willReturn(List.of(admin("oper01", "김운영", AdminRole.OPERATOR)));

        mockMvc.perform(get("/admin/system/admins").with(user(me)))
                .andExpect(status().isOk())
                .andExpect(view().name("backoffice/admin/system/admins"))
                .andExpect(model().attributeExists("admins", "roles", "statuses"))
                .andExpect(content().string(containsString("oper01")));
    }

    @Test
    void passesNullStatusWhenStatusIsAll() throws Exception {
        mockMvc.perform(get("/admin/system/admins").with(user(me))
                        .param("role", "OPERATOR").param("status", ""))
                .andExpect(status().isOk());
        verify(adminManageService).searchAdmins(null, AdminRole.OPERATOR, null);
    }

    @Test
    void showsEmptyMessageWhenNoResult() throws Exception {
        given(adminManageService.searchAdmins(any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/admin/system/admins").with(user(me)).param("keyword", "없는이름"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 계정이 없습니다.")));
    }

    @Test
    void returns400ForUnknownRole() throws Exception {
        mockMvc.perform(get("/admin/system/admins").with(user(me)).param("role", "FOO"))
                .andExpect(status().isBadRequest());
    }

    // 화면에 생성일을 그리므로 createdAt을 채운다 (원래 @CreatedDate가 저장할 때 넣는 값)
    private Admin admin(String loginId, String name, AdminRole role) {
        Admin admin = Admin.builder()
                .loginId(loginId).password("{noop}dummy").name(name).role(role).tempPassword(false)
                .build();
        ReflectionTestUtils.setField(admin, "createdAt", LocalDateTime.of(2026, 10, 16, 9, 0));
        return admin;
    }
}
