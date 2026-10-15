package org.doit.onbank.loan.dto;

import org.doit.onbank.loan.entity.LoanProduct;

public record LoanProductListDTO(
        Long id,
        String category,
        String name,
        String subtitle,
        String target,
        String limit,
        String method,
        String tag
) {
    public static LoanProductListDTO from(LoanProduct p) {
        return new LoanProductListDTO(
                p.getId(),
                p.getCategory(),
                p.getName(),
                p.getSubtitle(),
                p.getTarget(),
                p.getLimit(),
                p.getMethod(),
                p.getTag()
        );
    }
}
