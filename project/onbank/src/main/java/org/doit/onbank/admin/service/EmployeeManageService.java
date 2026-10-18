package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.repository.EmployeeSearchRepository;
import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.repository.BranchRepository;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 직원 계정 관리 (ACT-005). 목록 조회 + 다음 단계에서 생성 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class EmployeeManageService {

    private final EmployeeSearchRepository employeeSearchRepository;
    private final BranchRepository branchRepository;

    // 검색어가 비어 있으면 null
    public List<Employee> searchEmployees(String keyword, Long branchId, EmployeeRole role, EmployeeStatus status) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return employeeSearchRepository.search(kw, branchId, role, status);
    }

    // 목록의 "소속 지점"칸 Employee.branchId는 Long 이라 이름을 따로 찾아야 한다.
    public Map<Long, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(Branch::getBranchId, Branch::getName));
    }

    // 검색 조건 select용 (사용 중인 지점만)
    public List<Branch> selectBranches() {
        return branchRepository.findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType.BRANCH);
    }



}
