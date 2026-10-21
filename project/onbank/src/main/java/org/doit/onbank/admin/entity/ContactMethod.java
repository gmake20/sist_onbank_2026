package org.doit.onbank.admin.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// fds_handling_history.contact_method 에 저장하는 값 (name()) + 화면 이름
// 칸은 String(VARCHAR 20)이라 값을 늘려도 DB 변경 없음. 2026-10-22 와이어프레임대로 두 개로 시작
@Getter
@RequiredArgsConstructor
public enum ContactMethod {
    PHONE("전화"),
    OTHER("기타");

    private final String label;
}
