package org.doit.onbank.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.otp.entity.OtpStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import jakarta.persistence.EntityManager;

// FDS 판정용 쿼리 11개가 공용 DB에서 실행되는지 (SELECT만, 저장·삭제 없음)
// 문법·테이블·컬럼 이름·결과 타입만 본다. 거르는 조건(예약·본인·대출)이 맞는지는 확인 안 됨 (데이터를 넣어 봐야 함)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class FdsSearchRepositoryTests {

    private static final Long NO_ID = -1L;    // 없는 번호 → 데이터와 상관없이 결과가 정해진다
    private static final LocalDateTime FROM = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Autowired
    private FdsSearchRepository fdsSearchRepository;

    @Autowired
    private FdsDetectionRepository fdsDetectionRepository;

    @Autowired
    private EntityManager entityManager;

    // 1. 없는 회원·계좌로 11개 모두 실행 → 오류 없이 빈 값
    @Test
    void allQueriesRunWithNoMatch() {
        assertThat(fdsSearchRepository.findOtpReactivatedAt(NO_ID, OtpStatus.ACTIVE, OtpStatus.LOST)).isNull();
        assertThat(fdsSearchRepository.findBirthDate(NO_ID)).isNull();
        assertThat(fdsSearchRepository.findMemberIdByAccountNumber("no-such-account")).isNull();
        assertThat(fdsSearchRepository.findTransferOutId(NO_ID, "no-such-sqno")).isNull();
        assertThat(fdsSearchRepository.sumOutflow(NO_ID, FROM, TO)).isZero();
        assertThat(fdsSearchRepository.maxOutflow(NO_ID, FROM, TO, NO_ID)).isNull();
        assertThat(fdsSearchRepository.countOutflow(NO_ID, FROM, TO)).isZero();
        assertThat(fdsSearchRepository.countPastTransfersTo(NO_ID, "no-such-account", "no-such-sqno")).isZero();
        assertThat(fdsSearchRepository.maxIncomingFromOthers(NO_ID, NO_ID, FROM, TO)).isNull();
        assertThat(fdsSearchRepository.findPreviousTransactionAt(NO_ID, NO_ID)).isNull();

        // FdsDetectionRepository 메서드 이름으로 만든 쿼리 (D-66)
        List<FdsStatus> inProgress = List.of(FdsStatus.UNCHECKED, FdsStatus.ASSIGNED, FdsStatus.CONTACTING);
        assertThat(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(NO_ID, inProgress)).isEmpty();
        assertThat(fdsDetectionRepository.countByAccountIdAndStatusIn(NO_ID, inProgress)).isZero();
    }

    // 2. 실제 즉시이체 1건으로 실행 → 값이 있을 때 타입 변환(SUM→long, MAX→Long 등)이 되는지
    // 공용 데이터에 즉시이체가 없으면 건너뜀
    @Test
    void queriesReturnValuesForRealTransfer() {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT a.member_id, th.account_id, th.transaction_sqno, tr.deposit_account_number
                FROM transaction_history th
                JOIN account a ON a.account_id = th.account_id
                JOIN account_transfer tr ON tr.transaction_sqno = th.transaction_sqno
                WHERE th.transaction_type = 'TRANSFER_OUT' AND tr.transfer_type = 'IMMEDIATE'
                ORDER BY th.transaction_history_id DESC
                LIMIT 1
                """).getResultList();
        assumeTrue(!rows.isEmpty(), "공용 DB에 즉시이체 기록 없음");

        Object[] row = (Object[]) rows.get(0);
        Long memberId = ((Number) row[0]).longValue();
        Long accountId = ((Number) row[1]).longValue();
        String sqno = (String) row[2];
        String toAccountNumber = (String) row[3];

        Long historyId = fdsSearchRepository.findTransferOutId(accountId, sqno);
        assertThat(historyId).isNotNull();
        assertThat(fdsSearchRepository.findBirthDate(memberId)).isNotNull();
        assertThat(fdsSearchRepository.findMemberIdByAccountNumber(toAccountNumber)).isNotNull();

        // 같은 범위면 합계 ≥ 최대 ≥ 1원, 건수 ≥ 1 (본인 계좌 이체였으면 셋 다 0·null 일 수 있음)
        long sum = fdsSearchRepository.sumOutflow(memberId, FROM, TO);
        Long max = fdsSearchRepository.maxOutflow(memberId, FROM, TO, NO_ID);
        long count = fdsSearchRepository.countOutflow(memberId, FROM, TO);
        if (count > 0) {
            assertThat(max).isNotNull().isPositive();
            assertThat(sum).isGreaterThanOrEqualTo(max);
        }

        // 이번 건을 빼고 센 값이 0 이상 (실행만 확인)
        assertThat(fdsSearchRepository.countPastTransfersTo(memberId, toAccountNumber, sqno)).isNotNegative();
        assertThat(fdsSearchRepository.findPreviousTransactionAt(accountId, historyId)).satisfiesAnyOf(
                at -> assertThat(at).isNull(),
                at -> assertThat(at).isBefore(TO));
        fdsSearchRepository.maxIncomingFromOthers(accountId, memberId, FROM, TO);   // 실행만 확인
        fdsSearchRepository.findOtpReactivatedAt(memberId, OtpStatus.ACTIVE, OtpStatus.LOST);
    }
}
