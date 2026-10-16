package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminSearchRepository extends Repository<Admin,Long> {

    @Query("""
            SELECT a FROM Admin a
            WHERE (:keyword IS NULL OR  a.loginId LIKE concat('%', :keyword, '%')
                                     OR a.name LIKE concat('%', :keyword, '%'))
            AND (:role IS NULL OR a.role = :role)
            AND (:status IS NULL OR a.status = :status)
            ORDER BY a.createdAt DESC, a.adminId DESC
           """)
    List<Admin> search(@Param("keyword") String keyword,
                       @Param("role") AdminRole role,
                       @Param("status") AdminStatus status);

}
