package org.doit.onbank.member.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.ScheduledTransferRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.loan.entity.LoanAccountStatus;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.repository.LoanAccountRepository;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.repository.SavingsAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 마이페이지 > 회원 탈퇴 (화면 29번)
// 남은 금융거래가 있으면 탈퇴할 수 없다 (findBlockers). 고객이 직접 해지할 수 없는 것은 고객센터 안내
// 탈퇴하면 : 잔액 0원 계좌 해지, 대기·중지 중인 예약/정기이체 취소, 회원 상태 WITHDRAWN (Member.withdraw)
// 다른 파트 데이터(계좌·예적금·대출)는 각 파트 Repository로 읽기만 하고, 변경은 각 엔티티의 메서드로 한다
@Service
@RequiredArgsConstructor
public class MemberWithdrawService {

	// 고객이 처리를 끝내야 하는 대출 신청 상태 (거절·취소·만료·실행 완료는 끝난 것)
	private static final Set<LoanApplicationStatus> LOAN_APPLICATION_IN_PROGRESS = EnumSet.of(
			LoanApplicationStatus.PENDING, LoanApplicationStatus.REVIEWING,
			LoanApplicationStatus.ADDITIONAL_INFO, LoanApplicationStatus.APPROVED);

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccountRepository accountRepository;
	private final ScheduledTransferRepository scheduledTransferRepository;
	private final SavingsAccountRepository savingsAccountRepository;
	private final LoanAccountRepository loanAccountRepository;
	private final LoanApplicationRepository loanApplicationRepository;

	// 탈퇴할 수 없는 사유 목록 (비어 있으면 탈퇴 가능). 탈퇴 화면에 미리 보여 준다
	@Transactional(readOnly = true)
	public List<String> findBlockers(Long memberId) {
		List<String> blockers = new ArrayList<>();

		List<Account> accounts = accountRepository.findByMemberId(memberId);
		long withBalance = accounts.stream()
				.filter(a -> a.getAccountStatus() != AccountStatus.CLOSED)
				.filter(a -> a.getBalance() != null && a.getBalance() != 0L)
				.count();
		if (withBalance > 0) {
			blockers.add("잔액이 남아 있는 계좌가 " + withBalance + "개 있습니다. 잔액을 모두 이체·출금해 주세요.");
		}
		if (accounts.stream().anyMatch(a -> a.getAccountStatus() == AccountStatus.FROZEN)) {
			blockers.add("거래정지된 계좌가 있습니다. 고객센터로 문의해 주세요.");
		}
		if (accounts.stream().anyMatch(a -> a.getAccountStatus() == AccountStatus.PENDING)) {
			blockers.add("개설 심사 중인 계좌가 있습니다. 심사가 끝난 뒤 탈퇴해 주세요.");
		}

		long savings = savingsAccountRepository.findByMemberId(memberId).stream()
				.filter(s -> s.getStatus() == SavingsStatus.ACTIVE)
				.count();
		if (savings > 0) {
			blockers.add("가입 중인 예금·적금이 " + savings + "개 있습니다. 해지 또는 만기 후 탈퇴해 주세요.");
		}

		long loans = loanAccountRepository.findByLoanApplication_Member_MemberIdOrderByExecutedAtDesc(memberId).stream()
				.filter(l -> l.getStatus() != LoanAccountStatus.CLOSED)
				.count();
		if (loans > 0) {
			blockers.add("상환 중인 대출이 " + loans + "건 있습니다. 대출을 모두 상환해 주세요.");
		}

		long loanApplications = loanApplicationRepository.findAllByMember_MemberIdOrderByCreatedAtDesc(memberId).stream()
				.filter(a -> LOAN_APPLICATION_IN_PROGRESS.contains(a.getStatus()))
				.count();
		if (loanApplications > 0) {
			blockers.add("진행 중인 대출 신청이 " + loanApplications + "건 있습니다. 신청을 취소하거나 심사가 끝난 뒤 탈퇴해 주세요.");
		}
		return blockers;
	}

	// 비밀번호를 다시 확인하고 탈퇴. 사유가 있으면 첫 번째 사유로 BusinessException
	@Transactional
	public void withdraw(Long memberId, String password) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BusinessException("회원 정보를 찾을 수 없습니다."));
		if (password == null || !passwordEncoder.matches(password, member.getPassword())) {
			throw new BusinessException("비밀번호가 올바르지 않습니다.");
		}
		List<String> blockers = findBlockers(memberId);
		if (!blockers.isEmpty()) {
			throw new BusinessException(blockers.get(0));
		}

		LocalDateTime now = LocalDateTime.now();
		scheduledTransferRepository.findByMemberIdOrderByScheduledTransferIdDesc(memberId).stream()
				.filter(t -> t.getStatus() == ScheduledTransferStatus.WAITING
						|| t.getStatus() == ScheduledTransferStatus.PAUSED)
				.forEach(t -> t.cancel(now));

		// 위에서 잔액·상태를 확인했으므로 남은 계좌는 잔액 0원인 정상 계좌. 그 사이 입금되면 close()가 막는다
		try {
			accountRepository.findByMemberId(memberId).stream()
					.filter(a -> a.getAccountStatus() == AccountStatus.ACTIVE)
					.forEach(Account::close);
		} catch (IllegalStateException e) {
			throw new BusinessException("계좌 잔액이 바뀌었습니다. 다시 확인해 주세요.");
		}

		member.withdraw(now);
	}
}
