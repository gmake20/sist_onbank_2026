package org.doit.onbank.admin.repository;

import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// LON-001 대출 목록 (S-12)
public interface LoanSearchRepository extends Repository<LoanApplication, Long> {

    String SEARCH_WHERE = """
              WHERE (:status IS NULL OR a.status = :status)
              AND (:memberLoginId IS NULL OR m.loginId = :memberLoginId)
              AND a.createdAt >= :from AND a.createdAt < :to
              """;


    // 목록에 상품명·회원 아이디를 같이 보여 주므로 fetch join. fetch join은 count에 못 써서 count는 따로 적는다
            @Query(value = "SELECT a FROM LoanApplication a JOIN FETCH a.product JOIN FETCH a.member m "
            + SEARCH_WHERE + " ORDER BY a.createdAt DESC",
            countQuery = "SELECT count(a) FROM LoanApplication a JOIN a.member m " + SEARCH_WHERE)
    Page<LoanApplication> search(@Param("status") LoanApplicationStatus status,
                                 @Param("memberLoginId") String memberLoginId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    // 상단 건수용 : 상태 조건은 빼고 기간·회원 조건만. [상태, 건수]
    @Query("""
             SELECT a.status, count(a) FROM LoanApplication a JOIN a.member m
             WHERE (:memberLoginId IS NULL OR m.loginId = :memberLoginId)
             AND a.createdAt >= :from AND a.createdAt < :to
             GROUP BY a.status
             """)
    List<Object[]> countByStatus(@Param("memberLoginId") String memberLoginId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);
}
