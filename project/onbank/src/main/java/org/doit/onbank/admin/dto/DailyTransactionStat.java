package org.doit.onbank.admin.dto;

import java.time.LocalDate;

// DSH-002 거래량 추이 : DB 에서 날짜별로 묶은 한 줄 (DashboardRepository.dailyTransactions)
// 쿼리의 AS 이름과 getter 이름이 짝 (day → getDay). 거래가 없는 날은 행이 없다 → 서비스에서 0 으로 채움
public interface DailyTransactionStat {
    LocalDate getDay();     // 거래일
    Long getCount();        // 건수
    Long getAmount();       // 금액 합계
}
