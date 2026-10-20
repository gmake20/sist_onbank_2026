package org.doit.onbank.common.security;

// 임시 비밀번호로 로그인할 수 있는 로그인 정보 (고객 LoginMember, 직원·관리자 BackOfficeUser)
// true면 로그인 직후 비밀번호 변경 화면으로 보내고(LoginSuccessHandler), 바꾸기 전에는 다른 화면을 막는다(TempPasswordFilter)
public interface TempPasswordUser {

	// 임시 비밀번호(temp_pw_yn = 'Y')면 true
	boolean isTempPassword();
}
