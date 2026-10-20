package org.doit.onbank.admin.service;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.MemberListRow;
import org.doit.onbank.admin.repository.MemberSearchRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

// MEM-001 회원 목록·검색 (S-05)
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
