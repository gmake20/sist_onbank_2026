package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.AdminLog;
import org.doit.onbank.common.security.ActorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

    // LOG-002 검색 조건. 작업자 아이디는 actor_id에 FK가 없어서 admin / employee를 서브쿼리로 찾는다
    String SEARCH_WHERE = """
            WHERE (:actorType IS NULL OR l.actorType = :actorType)
            AND (:actorLoginId IS NULL
                 OR (l.actorType = org.doit.onbank.common.security.ActorType.ADMIN
                     AND l.actorId IN (SELECT a.adminId FROM Admin a WHERE a.loginId = :actorLoginId))
                 OR (l.actorType = org.doit.onbank.common.security.ActorType.EMPLOYEE
                     AND l.actorId IN (SELECT e.employeeId FROM Employee e WHERE e.employeeNo = :actorLoginId)))
            AND (:actionType IS NULL OR l.actionType = :actionType)
            AND (:targetType IS NULL OR l.targetType = :targetType)
            AND (:targetId IS NULL
                 OR l.targetId = :targetId
                 OR (l.targetType = 'ADMIN'
                     AND l.targetId IN (SELECT CAST(a.adminId AS String) FROM Admin a WHERE a.loginId = :targetId))
                 OR (l.targetType = 'EMPLOYEE'
                     AND l.targetId IN (SELECT CAST(e.employeeId AS String) FROM Employee e WHERE e.employeeNo = :targetId))
                 OR (l.targetType = 'MEMBER'
                     AND l.targetId IN (SELECT CAST(m.memberId AS String) FROM Member m WHERE m.loginId = :targetId)))
            AND l.createdAt >= :from AND l.createdAt < :to
            """;

    // 페이지용 count 쿼리를 자동으로 만들게 두지 않고 같은 조건으로 직접 적는다
    @Query(value = "SELECT l FROM AdminLog l " + SEARCH_WHERE + " ORDER BY l.logId DESC",
            countQuery = "SELECT count(l) FROM AdminLog l " + SEARCH_WHERE)
    Page<AdminLog> search(@Param("actorType") ActorType actorType,
                          @Param("actorLoginId") String actorLoginId,
                          @Param("actionType") String actionType,
                          @Param("targetType") String targetType,
                          @Param("targetId") String targetId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          Pageable pageable);

    // 검색 select 채우기용 : 실제로 기록된 값만
    @Query("SELECT DISTINCT l.actionType FROM AdminLog l ORDER BY l.actionType")
    List<String> findActionTypes();

    @Query("SELECT DISTINCT l.targetType FROM AdminLog l ORDER BY l.targetType")
    List<String> findTargetTypes();



}
