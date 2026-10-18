package org.doit.onbank.admin.repository;

import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeSearchRepository extends Repository<Employee, Long> {

    @Query("""
           SELECT e FROM Employee e
           WHERE (:keyword IS NULL OR e.employeeNo LIKE concat('%', :keyword, '%')
                                    OR e.name LIKE concat('%', :keyword, '%'))
           AND (:branchId IS NULL OR e.branchId = :branchId)
           AND (:role IS NULL OR e.role = :role)
           AND (:status IS NULL OR e.status = :status)
           ORDER BY e.employeeNo ASC
           """)
    List<Employee> search(@Param("keyword") String keyword,
                          @Param("branchId") Long branchId,
                          @Param("role") EmployeeRole role,
                          @Param("status") EmployeeStatus status);

}
