package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.FdsHandlingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FdsHandlingHistoryRepository extends JpaRepository<FdsHandlingHistory, Long> {

    // FDS-005 종결 : 이 탐지의 마지막 연락 기록 1건. 연락 결과가 있는 줄만 (단계 변경 줄은 contactResult 가 null)
    // 마지막 = 연락 일시가 가장 늦은 것. 일시가 같으면 나중에 저장한 것
    Optional<FdsHandlingHistory> findFirstByDetectionIdAndContactResultIsNotNullOrderByContactAtDescHistoryIdDesc(Long detectionId);

}
