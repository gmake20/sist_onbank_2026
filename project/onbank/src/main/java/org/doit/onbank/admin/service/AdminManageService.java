package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.repository.AdminSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// 계정 관리, 계정 생성도 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminManageService {

    private final AdminSearchRepository adminSearchRepository;

    // 검색어가 비어있으면 null로 -> 쿼리에서 조건 빠짐
    public List<Admin> searchAdmins(String keyword, AdminRole role, AdminStatus status) {
        String kw = (keyword == null || keyword.isBlank() ) ? null : keyword.trim();
        return adminSearchRepository.search(kw, role, status);
    }

}
