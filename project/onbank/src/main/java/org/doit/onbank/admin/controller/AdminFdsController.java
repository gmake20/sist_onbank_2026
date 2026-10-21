package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.FdsContactForm;
import org.doit.onbank.admin.dto.FdsDetailView;
import org.doit.onbank.admin.entity.ContactMethod;
import org.doit.onbank.admin.entity.ContactResult;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminFdsService;
import org.doit.onbank.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// S-15 이상거래 탐지 목록 · S-16 상세 (FDS-002), 배정·연락 시작·연락 기록 (FDS-004~006)
// 역할은 사이드바·화면목록 5장과 같게. 직원(STAFF·MANAGER) 조회는 /employee 쪽에서 따로 (D-02)
@Controller
@RequestMapping("/admin/fds")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminFdsController {

    private static final int DEFAULT_DAYS = 30;     // 기간을 안 고르면 최근 30일 (계좌 상태 이력과 같음)

    // 상세 → "탐지 목록"으로 돌아갈 때 그대로 붙여 줄 검색 조건 (AdminAccountController SEARCH_KEYS 와 같은 생각)
    private static final List<String> SEARCH_KEYS = List.of("status", "from", "to", "accountNumber", "mine", "page");

    private final AdminFdsService adminFdsService;

    // 처음 들어오면 처리 중만. "전체"는 빈 값 대신 ALL 을 보낸다
    // (빈 값이면 defaultValue 가 다시 적용돼 처리 중으로 바뀜. 서비스는 모르는 값을 전체로 본다)
    @GetMapping
    public String list(@AuthenticationPrincipal AdminPrincipal me,
                       @RequestParam(name = "status", defaultValue = AdminFdsService.FILTER_OPEN) String status,
                       @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "accountNumber", required = false) String accountNumber,
                       @RequestParam(name = "mine", defaultValue = "false") boolean mine,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS);
        }

        if (from.isAfter(to)) {
            model.addAttribute("searchError", "시작일이 종료일보다 늦습니다.");
            model.addAttribute("detections", Page.empty());
        } else {
            model.addAttribute("detections", adminFdsService.search(from, to, status, accountNumber,
                    mine ? me.getAdminId() : null, page));
        }
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("mine", mine);
        model.addAttribute("statusLabels", AdminFdsService.STATUS_LABELS);
        model.addAttribute("ruleLabels", AdminFdsService.RULE_LABELS);
        model.addAttribute("txTypeLabels", AdminFdsService.TX_TYPE_LABELS);

        return "backoffice/admin/fds/detections";
    }

    // S-16 탐지 상세 1단계 (조회만). 목록에서 오면 검색 조건을 받아 "탐지 목록" 링크에 다시 붙인다
    // 토스트로 오면 조건 없음 → 기본 목록 (처리 중 · 최근 30일)
    // 없는 번호면 목록으로 돌려보내며 안내 (AdminAccountController.history 와 같음)
    @GetMapping("/{detectionId}")
    public String detail(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("detectionId") Long detectionId,
                         @RequestParam Map<String, String> params,
                         Model model, RedirectAttributes rttr) {
        try {
            fillDetail(me, detectionId, params, model);
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/fds";
        }
        model.addAttribute("contactForm", newContactForm());
        return "backoffice/admin/fds/detection";
    }

    // FDS-004 내가 맡기. 같은 계좌 미확인 건도 함께 (AdminFdsService.assign). 처리 후 같은 상세로 (PRG)
    @PostMapping("/{detectionId}/assign")
    public String assign(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("detectionId") Long detectionId,
                         @RequestParam Map<String, String> params, HttpServletRequest request,
                         RedirectAttributes rttr) {
        try {
            int count = adminFdsService.assign(me, detectionId, request.getRemoteAddr());
            rttr.addFlashAttribute("message", count > 1
                    ? "탐지 #" + detectionId + "을 맡았습니다. 같은 계좌의 미확인 탐지 " + (count - 1) + "건도 함께 맡았습니다."
                    : "탐지 #" + detectionId + "을 맡았습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + withSearch(UriComponentsBuilder.fromPath("/admin/fds/" + detectionId), searchParams(params));
    }

    // FDS-005 고객 연락 시작. 같은 계좌의 내 담당 건도 함께 (AdminFdsService.startContact). 처리 후 같은 상세로 (PRG)
    @PostMapping("/{detectionId}/contact-start")
    public String startContact(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("detectionId") Long detectionId,
                               @RequestParam Map<String, String> params, HttpServletRequest request,
                               RedirectAttributes rttr) {
        try {
            int count = adminFdsService.startContact(me, detectionId, request.getRemoteAddr());
            rttr.addFlashAttribute("message", count > 1
                    ? "고객 연락을 시작했습니다. 같은 계좌의 내 담당 탐지 " + (count - 1) + "건도 함께 \"고객 연락 중\"이 됐습니다."
                    : "고객 연락을 시작했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + withSearch(UriComponentsBuilder.fromPath("/admin/fds/" + detectionId), searchParams(params));
    }

    // FDS-006 연락 결과 기록. 입력 오류면 같은 화면을 다시 그린다 (입력값·오류 문구 유지, AdminManageController 와 같은 방식)
    @PostMapping("/{detectionId}/contacts")
    public String recordContact(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("detectionId") Long detectionId,
                                @Valid @ModelAttribute("contactForm") FdsContactForm form, BindingResult bindingResult,
                                @RequestParam Map<String, String> params, Model model, RedirectAttributes rttr) {
        if (bindingResult.hasErrors()) {
            try {
                fillDetail(me, detectionId, params, model);
            } catch (BusinessException e) {
                rttr.addFlashAttribute("error", e.getMessage());
                return "redirect:/admin/fds";
            }
            return "backoffice/admin/fds/detection";
        }
        try {
            int count = adminFdsService.recordContact(me, detectionId, form);
            rttr.addFlashAttribute("message", count > 1
                    ? "연락 결과를 저장했습니다. 같은 계좌의 내 담당 탐지 " + count + "건에 함께 기록했습니다."
                    : "연락 결과를 저장했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + withSearch(UriComponentsBuilder.fromPath("/admin/fds/" + detectionId), searchParams(params));
    }

    // 상세 화면에 넣는 값. "내 담당" = 로그인한 관리자가 담당자 (버튼·연락 입력은 담당자에게만)
    private void fillDetail(AdminPrincipal me, Long detectionId, Map<String, String> params, Model model) {
        FdsDetailView view = adminFdsService.detail(detectionId);
        model.addAttribute("view", view);
        model.addAttribute("mine", me.getAdminId().equals(view.row().getAssignedAdminId()));

        Map<String, String> search = searchParams(params);
        model.addAttribute("searchParams", search);     // 처리 form 의 hidden 칸 → 처리 후에도 "탐지 목록" 링크 유지
        model.addAttribute("backUrl", withSearch(UriComponentsBuilder.fromPath("/admin/fds"), search));
        model.addAttribute("statusLabels", AdminFdsService.STATUS_LABELS);
        model.addAttribute("txTypeLabels", AdminFdsService.TX_TYPE_LABELS);
        model.addAttribute("contactMethods", ContactMethod.values());
        model.addAttribute("contactResults", ContactResult.values());
        model.addAttribute("contactMethodLabels", AdminFdsService.CONTACT_METHOD_LABELS);
        model.addAttribute("contactResultLabels", AdminFdsService.CONTACT_RESULT_LABELS);
    }

    // 연락 기록 입력 기본값 : 일시 = 지금(분까지, datetime-local 형식에 맞게), 방법 = 전화
    private FdsContactForm newContactForm() {
        FdsContactForm form = new FdsContactForm();
        form.setContactAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        form.setContactMethod(ContactMethod.PHONE);
        return form;
    }

    // 정해 둔 이름(SEARCH_KEYS)만, 빈 값은 빼고 (AdminAccountController.backToList 와 같은 생각)
    private Map<String, String> searchParams(Map<String, String> params) {
        Map<String, String> search = new LinkedHashMap<>();
        for (String key : SEARCH_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                search.put(key, value);
            }
        }
        return search;
    }

    private String withSearch(UriComponentsBuilder builder, Map<String, String> search) {
        search.forEach(builder::queryParam);
        return builder.encode().build().toUriString();
    }
}
