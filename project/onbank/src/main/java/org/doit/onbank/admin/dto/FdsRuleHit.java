package org.doit.onbank.admin.dto;

import org.doit.onbank.admin.entity.FdsRule;

// 걸린 규칙 1개. detail JSON 의 {r, s, b} 한 칸이 된다 (FDS-탐지규칙.md 4장)
// basis 는 값만 15자 이내 (예: "1,200만")
public record FdsRuleHit(FdsRule rule, int score, String basis) {
}
