package org.doit.onbank.admin.dto;

import java.time.LocalDate;

// DSH-002 거래량 추이 막대 1개. 거래가 없는 날도 0 으로 들어 있다
// heightPercent : 기간 중 건수가 가장 많은 날 = 100. 화면에서 막대 높이(style height)로 쓴다
public record TrendBar(LocalDate day, long count, long amount, int heightPercent) {
}
