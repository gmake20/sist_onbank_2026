package org.doit.onbank.admin.service;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.MemberListRow;
import org.doit.onbank.admin.repository.MemberSearchRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficeUser;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

// MEM-001 회원 목록·검색 (S-05), MEM-003 이용 제한·해제, MEM-004 로그인 잠금 해제
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminMemberService {

    private static final int PAGE_SIZE = 20;

    // 와이어프레임 S-05 상태 글자
    public static final Map<MemberStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            MemberStatus.ACTIVE, "정상",
            MemberStatus.LOCKED, "로그인 잠금",
            MemberStatus.SUSPENDED, "이용 제한",
            MemberStatus.WITHDRAWN, "탈퇴"));

    private final MemberSearchRepository memberSearchRepository;
    private final MemberRepository memberRepository;
    private final AdminLogService adminLogService;

    // type(loginId / name / phone)에 해당하는 칸에만 검색어를 넣고 나머지는 null
    // 기간은 from 00:00 이상 ~ to 다음 날 00:00 미만. 비우면 조건 없음
    public Page<MemberListRow> search(String type, String keyword, MemberStatus status,
                                      LocalDate from, LocalDate to, int page) {
        String kw = blankToNull(keyword);
        String loginId = null;
        String name = null;
        String phone = null;
        if (kw != null) {
            switch (type) {
                case "name" -> name = kw;
                case "phone" -> phone = kw.replaceAll("[^0-9]", "");   // DB는 숫자만 저장
                default -> loginId = kw;
            }
        }
        LocalDateTime fromAt = (from == null) ? null : from.atStartOfDay();
        LocalDateTime toAt = (to == null) ? null : to.plusDays(1).atStartOfDay();

        return memberSearchRepository.search(loginId, name, phone, status, fromAt, toAt,
                        PageRequest.of(Math.max(page, 0), PAGE_SIZE))
                .map(this::toRow);
    }

    // 로그인 잠금 해제 : LOCKED → ACTIVE (실패 횟수 0)
    @Transactional
    public void unlockLogin(BackOfficeUser me, Long memberId, String reason, String requestIp) {
        changeStatus(me, memberId, "MEMBER_UNLOCK", reason, requestIp,
                EnumSet.of(MemberStatus.LOCKED), Member::unlockLogin);
    }

    // 이용 제한 : ACTIVE·LOCKED → SUSPENDED
    // 잠긴 회원도 제한할 수 있게 둔다. 잠금이 풀려도 다시 열리면 안 되는 경우 (관리자 LOCKED → 비활성화와 같은 이유)
    @Transactional
    public void restrict(BackOfficeUser me, Long memberId, String reason, String requestIp) {
        changeStatus(me, memberId, "MEMBER_RESTRICT", reason, requestIp,
                EnumSet.of(MemberStatus.ACTIVE, MemberStatus.LOCKED), Member::restrict);
    }

    // 이용 제한 해제 : SUSPENDED → ACTIVE (실패 횟수 0)
    @Transactional
    public void unrestrict(BackOfficeUser me, Long memberId, String reason, String requestIp) {
        changeStatus(me, memberId, "MEMBER_UNRESTRICT", reason, requestIp,
                EnumSet.of(MemberStatus.SUSPENDED), Member::unrestrict);
    }

    // 상태 변경 + admin_log 기록을 한 트랜잭션으로 (EmployeeManageService.changeStatus와 같은 방식)
    private void changeStatus(BackOfficeUser me, Long memberId, String actionType, String reason, String requestIp,
                              Set<MemberStatus> allowed, Consumer<Member> action) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException("없는 회원입니다."));
        if (!allowed.contains(member.getStatus())) {         // 다른 화면에서 먼저 바꾼 경우
            throw new BusinessException("회원 상태가 이미 바뀌었습니다. 목록을 다시 확인하세요.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {   // admin_log.reason 200자
            throw new BusinessException("사유를 200자 이내로 입력하세요.");
        }
        MemberStatus before = member.getStatus();
        action.accept(member);
        adminLogService.record(me, actionType, "MEMBER", String.valueOf(memberId),
                before.name(), member.getStatus().name(), reason.trim(), requestIp);
    }

    private MemberListRow toRow(Member m) {
        return new MemberListRow(m.getMemberId(), m.getLoginId(), m.getName(),
                maskPhone(m.getPhone()), m.getStatus(), m.getCreatedAt());
    }

    // 01012345678 → 010-****-5678. 탈퇴 회원은 NULL → null (화면에서 '–')
    private String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        if (phone.length() < 10) {      // 휴대폰 길이가 아니면 전부 가림
            return "*".repeat(phone.length());
        }
        return phone.substring(0, 3) + "-" + "*".repeat(phone.length() - 7)
                + "-" + phone.substring(phone.length() - 4);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
