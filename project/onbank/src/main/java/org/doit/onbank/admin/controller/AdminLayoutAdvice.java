package org.doit.onbank.admin.controller;


import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

// 관리자 공통 레이아웃(backoffice/layout/admin.html)에 필요한 값을 관리자 컨트롤러 화면 전부에 넣는다
@ControllerAdvice(basePackages = "org.doit.onbank.admin.controller")
public class AdminLayoutAdvice {

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

}
