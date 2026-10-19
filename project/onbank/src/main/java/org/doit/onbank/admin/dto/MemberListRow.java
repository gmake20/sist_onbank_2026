package org.doit.onbank.admin.dto;

import org.doit.onbank.member.entity.MemberStatus;

import java.time.LocalDateTime;

public record MemberListRow(Long memberId, String loginId,String name, String maskedPhone,
                            MemberStatus status, LocalDateTime createdAt) {
}

