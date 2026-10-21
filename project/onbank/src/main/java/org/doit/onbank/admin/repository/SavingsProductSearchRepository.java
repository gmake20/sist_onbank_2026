package org.doit.onbank.admin.repository;

import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.entity.SavingsType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// PRD-001 상품 목록 (S-10). 대상은 팀원3 savings_product (예적금)
// 팀원3 SavingsProductRepository 는 고치지 않고 조회만 따로 둔다 (LoanSearchRepository 와 같음)
public interface SavingsProductSearchRepository extends Repository<SavingsProduct, Long> {

    String FROM_WHERE = """
              FROM SavingsProduct p
              WHERE (:name IS NULL OR p.name LIKE concat('%', :name, '%'))
              AND (:type IS NULL OR p.type = :type)
              AND (:active IS NULL OR p.active = :active)
              """;

    // 등록일 칸이 없어서 상품 ID 최신순
    @Query(value = "SELECT p " + FROM_WHERE + " ORDER BY p.id DESC",
            countQuery = "SELECT count(p) " + FROM_WHERE)
    Page<SavingsProduct> search(@Param("name") String name,
                                @Param("type") SavingsType type,
                                @Param("active") Boolean active,
                                Pageable pageable);

    // 같은 이름 검사는 판매중 상품끼리만 (판매중지된 옛 상품과 같은 이름은 허용)
    boolean existsByNameAndActiveTrue(String name);
}
