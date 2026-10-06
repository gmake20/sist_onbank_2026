package org.doit.onbank.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    @GetMapping("/")
    public String showMain() {
        return "index"; // templates/index.html 파일을 찾아 화면에 출력함
    }
}