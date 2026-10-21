package org.doit.onbank.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 고객센터 안내 화면 (비회원 공개, DB 없음)
// 공지사항·FAQ·1:1 문의(README 12장)가 만들어지면 이 화면의 "준비 중" 카드를 각 화면 링크로 바꾼다
@Controller
public class CustomerCenterController {

    @GetMapping("/customer")
    public String customerCenter() {
        return "customer";
    }
}
