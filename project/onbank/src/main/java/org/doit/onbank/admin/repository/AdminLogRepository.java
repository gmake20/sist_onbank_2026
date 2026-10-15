package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.entity.AdminLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminLogRepository extends JpaRepository<AdminLog, Long> {

}
