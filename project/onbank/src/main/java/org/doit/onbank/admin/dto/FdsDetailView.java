package org.doit.onbank.admin.dto;

import java.util.List;

// FDS-002 탐지 상세(S-16) 화면에 넘기는 값 (AdminFdsService.detail)
// 점수 막대 : 0 ~ scale 을 100% 로. 합계가 100 을 넘으면 scale = 합계 (막대가 넘치지 않게)
public record FdsDetailView(FdsDetailRow row, List<RuleLine> rules, List<FdsHistoryRow> history,
                            int cautionScore, int detectScore, int scale) {

    // 기준선(주의·탐지) 위치 % — 화면에서 style="left:…%"
    public int percentOf(int score) {
        return score * 100 / scale;
    }

    // 규칙 한 줄. percent = 막대 너비 %, shade = 막대·네모 색 단계 1~5 (점수 큰 순)
    public record RuleLine(String code, String label, int score, String basis, int percent, int shade) {
    }
}
