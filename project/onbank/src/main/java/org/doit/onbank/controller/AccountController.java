package org.doit.onbank.controller;

	import org.springframework.stereotype.Controller;
	import org.springframework.web.bind.annotation.GetMapping;

	@Controller
	public class AccountController {

	    // 사용자가 웹 브라우저에서 /account/list 주소로 접속(GET 요청)하면 이 메서드가 실행됨
	    @GetMapping("/account/list")
	    public String accountList() {
	        
	        // src/main/resources/templates/ 폴더 아래에 있는
	        // account/list.html 파일을 찾아서 화면에 렌더링하라는 의미입니다. (확장자 .html은 생략)
	        return "account/list"; 
	    }
	}

