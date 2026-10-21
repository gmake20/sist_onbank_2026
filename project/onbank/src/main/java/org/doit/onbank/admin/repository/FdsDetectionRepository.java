package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.FdsDetection;
import org.doit.onbank.admin.entity.FdsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FdsDetectionRepository extends JpaRepository<FdsDetection, Long> {

    // D-66 : 같은 계좌의 처리 중 탐지. 가장 먼저 생긴 것 = 첫 건 (담당자 확인용)
    Optional<FdsDetection> findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(Long accountId,
                                                                               Collection<FdsStatus> statuses);

    // D-66 : "N번째" 표시용
    long countByAccountIdAndStatusIn(Long accountId, Collection<FdsStatus> statuses);

    // FDS-004 배정 : 같은 계좌의 미확인 건을 잠그고 조회 (SELECT ... FOR UPDATE, AccountRepository.findByIdForUpdate 와 같은 방식)
    // 누른 건 하나가 아니라 계좌 단위로, 번호 순으로 잠근다 → 두 관리자가 같은 계좌의 다른 건을 동시에 눌러도 잠그는 순서가 같아 교착 없음
    // 뒤에 온 사람은 앞 사람이 끝날 때까지 기다렸다가, 이미 ASSIGNED 가 된 건은 목록에 안 나온다 → 서비스가 거절
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FdsDetection d where d.accountId = :accountId and d.status = :status order by d.detectionId")
    List<FdsDetection> findByAccountIdAndStatusForUpdate(@Param("accountId") Long accountId,
                                                         @Param("status") FdsStatus status);

    // FDS-005·006 연락 시작·연락 기록 : 같은 계좌에서 "내가 맡은" 특정 단계 건을 잠그고 조회 (사건 단위로 함께 처리)
    // 배정과 같은 이유로 계좌 단위·번호 순 잠금. 다른 담당자 건은 안 나온다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FdsDetection d where d.accountId = :accountId and d.status = :status"
            + " and d.assignedAdminId = :adminId order by d.detectionId")
    List<FdsDetection> findMineByAccountIdAndStatusForUpdate(@Param("accountId") Long accountId,
                                                             @Param("status") FdsStatus status,
                                                             @Param("adminId") Long adminId);
}
