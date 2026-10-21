package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.FdsDetection;
import org.doit.onbank.admin.entity.FdsStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface FdsDetectionRepository extends JpaRepository<FdsDetection, Long> {

    // D-66 : 같은 계좌의 처리 중 탐지. 가장 먼저 생긴 것 = 첫 건 (담당자 확인용)
    Optional<FdsDetection> findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(Long accountId,
                                                                               Collection<FdsStatus> statuses);

    // D-66 : "N번째" 표시용
    long countByAccountIdAndStatusIn(Long accountId, Collection<FdsStatus> statuses);
}
