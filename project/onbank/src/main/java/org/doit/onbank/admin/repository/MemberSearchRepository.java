package org.doit.onbank.admin.repository;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// 회원 목록, 검색
public interface MemberSearchRepository extends Repository<Member, Long> {

    String SEARCH_WHERE = """
                           WHERE (:loginId IS NULL OR m.loginId LIKE concat('%', :loginId, '%'))
                           AND (:name IS NULL OR m.name  LIKE concat('%', :name, '%'))
                           AND (:phone IS NULL OR m.phone LIKE concat('%', :phone, '%'))
                           AND (:status IS NULL OR m.status = :status)
                           AND (:from IS NULL OR m.createdAt >= :from)
                           AND (:to IS NULL OR m.createdAt < :to)
                           """;

    @Query(value = "SELECT m FROM Member m " + SEARCH_WHERE + " ORDER BY m.createdAt DESC, m.memberId DESC ",
                    countQuery = "SELECT count(m) FROM Member m " + SEARCH_WHERE)
    Page<Member> search(@Param("loginId") String loginId,
                        @Param("name") String name,
                        @Param("phone") String phone,
                        @Param("status")MemberStatus status,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        Pageable pageable);

}
