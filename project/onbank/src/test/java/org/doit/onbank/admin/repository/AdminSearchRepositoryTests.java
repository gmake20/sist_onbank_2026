package org.doit.onbank.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

// S-19 검색 쿼리 테스트 : 공용 DB의 admin 테이블을 읽기만 한다 (SELECT만, 저장·삭제 없음)
// 공용 데이터는 바뀔 수 있으므로 건수가 아니라 "결과가 조건에 맞는지·정렬이 맞는지"로 검사한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AdminSearchRepositoryTests {
    @Autowired
    private AdminSearchRepository adminSearchRepository;

    @Test
    void returnsAllNewestFirstWhenNoCondition() {
        List<Admin> result = adminSearchRepository.search(null, null, null);

        assertThat(result).isNotEmpty();    // 최고 관리자 시드가 있어야 함 (D-14)
        assertThat(result).isSortedAccordingTo(Comparator.comparing(Admin::getCreatedAt).reversed());
    }

    @Test
    void filtersByRole() {
        List<Admin> result = adminSearchRepository.search(null, AdminRole.OPERATOR, null);

        assertThat(result).allMatch(a -> a.getRole() == AdminRole.OPERATOR);
    }

    @Test
    void filtersByStatus() {
        List<Admin> result = adminSearchRepository.search(null, null, AdminStatus.ACTIVE);

        assertThat(result).allMatch(a -> a.getStatus() == AdminStatus.ACTIVE);
    }

    @Test
    void keywordMatchesLoginIdOrNamePartially() {
        // 있는 계정의 아이디 앞 3글자로 검색 → 그 계정이 나오고, 나온 계정은 전부 아이디나 이름에 검색어가 있다
        Admin any = adminSearchRepository.search(null, null, null).get(0);
        String keyword = any.getLoginId().substring(0, Math.min(3, any.getLoginId().length()));

        List<Admin> result = adminSearchRepository.search(keyword, null, null);

        assertThat(result).extracting(Admin::getLoginId).contains(any.getLoginId());
        assertThat(result).allMatch(a -> a.getLoginId().contains(keyword) || a.getName().contains(keyword));
    }

    @Test
    void returnsEmptyForUnknownKeyword() {
        List<Admin> result = adminSearchRepository.search("nobody-" + System.nanoTime(), null, null);

        assertThat(result).isEmpty();
    }
}
