package org.doit.onbank.admin.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// MON-002 주의(30~59점) 보관 : DB에 저장하지 않으므로(D-37) 서버 메모리에 최근 MAX_SIZE 건만 둔다 (D-84)
// 키 = 거래내역 번호, 값 = 점수. 서버를 다시 켜면 비어 있다 → 모니터링 화면의 노란색만 사라짐 (거래 줄은 DB에 있음)
// 이체가 동시에 끝나면 여러 스레드가 같이 쓰므로 메서드마다 synchronized
@Component
public class FdsCautionStore {

    private static final int MAX_SIZE = 100;

    // 넣은 순서를 기억하고, MAX_SIZE 를 넘으면 가장 오래된 것부터 지운다
    private final Map<Long, Integer> scores = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > MAX_SIZE;
        }
    };

    public synchronized void put(Long transactionHistoryId, int score) {
        if (transactionHistoryId != null) {
            scores.put(transactionHistoryId, score);
        }
    }

    // 화면에 보낼 거래 번호 중 주의인 것만 골라 준다 (없으면 빈 Map)
    public synchronized Map<Long, Integer> findScores(Collection<Long> transactionHistoryIds) {
        Map<Long, Integer> found = new HashMap<>();
        for (Long id : transactionHistoryIds) {
            Integer score = scores.get(id);
            if (score != null) {
                found.put(id, score);
            }
        }
        return found;
    }
}
