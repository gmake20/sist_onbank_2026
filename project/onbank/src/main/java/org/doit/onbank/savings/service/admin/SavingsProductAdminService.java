package org.doit.onbank.savings.service.admin;

import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.savings.dto.SavingsProductCreateForm;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.repository.SavingsProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsProductAdminService {
	
    private final SavingsProductRepository products;

    @Transactional
    public Long register(SavingsProductCreateForm form) {
    	
        if (form == null || form.getName() == null || form.getName().isBlank()
                || form.getName().strip().length() > 120) {
            throw new IllegalArgumentException("상품명은 1~120자로 입력하세요.");
        }
        
        if (form.getType() == null) {
            throw new IllegalArgumentException("상품 유형을 선택하세요.");
        }
        
        BigDecimal rate = form.getAnnualRate();
        if (rate == null || rate.signum() < 0
                || rate.compareTo(new BigDecimal("100")) > 0
                || rate.scale() > 4) {
            throw new IllegalArgumentException("연이율은 0~100%, 소수점 넷째 자리까지 입력하세요.");
        }
        
        Integer months = form.getTermMonths();
        if (months == null || months < 1 || months > 120) {
            throw new IllegalArgumentException("가입 기간은 1~120개월로 입력하세요.");
        }
        
        Long min = form.getMinAmount();
        Long max = form.getMaxAmount();
        
        if (min == null || max == null || min < 1 || max < min) {
            throw new IllegalArgumentException("금액 범위를 확인하세요. 최대 금액은 최소 금액 이상이어야 합니다.");
        }
        
        SavingsProduct product = new SavingsProduct(
                form.getName().strip(), form.getType(), rate,
                months, min, max);
        
        return products.save(product).getId();
    }
}
