package org.doit.onbank.loan.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.service.AccountNumberGenerator;
import org.doit.onbank.account.service.AccountTransferService;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanAccountRepository;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanExecutionService {

	private final LoanRepaymentScheduleService loanRepaymentScheduleService;
	private final AccountTransferService accountTransferService;
	private final LoanApplicationRepository loanApplicationRepository;
	private final EmployeeRepository employeeRepository;
	private final LoanAccountRepository loanAccountRepository;

	private final AccountNumberGenerator generator;

	private final LoanRepaymentScheduleRepository schedules;
	private final LoanService loanService;

	@Transactional(readOnly = true)
	public LoanApplication findApprovedApplication(Long applicationId) {

		LoanApplication application = loanApplicationRepository
				.findById(applicationId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"존재하지 않는 대출 신청입니다."
						));

		validateApprovedApplication(application);

		return application;
	}

	@Transactional
	public void execute(Long applicationId, Long employeeId) {

		// 1. 직원 확인
		Employee employee = employeeRepository.findById(employeeId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"직원 정보를 찾을 수 없습니다."
						));

		if (employee.getStatus() != EmployeeStatus.ACTIVE
				|| employee.getRole() != EmployeeRole.MANAGER) {
			throw new IllegalStateException(
					"대출 실행 권한이 없습니다."
					);
		}

		// 2. 신청서 조회 (비관적 잠금)
		LoanApplication application = loanApplicationRepository
				.findByIdForUpdate(applicationId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"존재하지 않는 대출 신청입니다."
						));

		// 3. 승인 조건 검증
		validateApprovedApplication(application);

		// 4. 수령 계좌 확인
		Account account = application.getDisbursementAccount();

		// 신청 회원의 계좌인지 확인
		if (!account.getMemberId()
				.equals(application.getMember().getMemberId())) {
			throw new IllegalStateException(
					"대출 신청자와 수령 계좌 소유자가 다릅니다."
					);
		}

		if (!"CHECKING".equals(account.getAccountType())) {
			throw new IllegalStateException(
					"입출금계좌만 대출금을 수령할 수 있습니다."
					);
		}

		//        if (account.getAccountType() != AccountType.CHECKING) {
		//            throw new IllegalStateException(
		//                "입출금계좌만 대출금을 수령할 수 있습니다."
		//            );
		//        }

		if (account.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new IllegalStateException(
					"정상 계좌가 아닙니다."
					);
		}

		// 여기까지는 실행 조건 검증 단계입니다.
		// 실제 지급은 Loan/Account/거래원장 연동 후 구현해야 합니다.


		String loanAccountNumber = generator.generate(AccountType.LOAN);

		LoanAccount loanAccount = LoanAccount.builder()
				.loanAccountNumber(loanAccountNumber)
				.loanApplication(application)
				.principalAmount(application.getApprovedAmount())
				.interestRate(application.getApprovedInterestRate())
				.repaymentMethod(application.getRepaymentMethod())
				.executedAt(LocalDateTime.now())
				.maturityDate(
						LocalDate.now().plusMonths(
								application.getRequestedMonths()
								)
						)
				.build();

		loanAccountRepository.save(loanAccount);

		// 2. 대출금 지급
		//  Account 잔액 증가 + 거래원장 기록
		accountTransferService.disburseLoan(
				loanAccount, account, application.getApprovedAmount()
				);

		// 3. 대출 상환 스케줄 생성
		// 기존 calculate(application) 결과 저장
		loanRepaymentScheduleService.createSchedule(loanAccount, application
				);

		// 4. 대출 실행 완료 상태 변경
		application.markExecuted();
	}

	private void validateApprovedApplication(
			LoanApplication application) {

		if (application.getStatus() != LoanApplicationStatus.APPROVED) {
			throw new IllegalStateException(
					"승인된 대출만 실행할 수 있습니다."
					);
		}

		if (application.getApprovedAmount() == null
				|| application.getApprovedAmount().signum() <= 0) {
			throw new IllegalStateException(
					"승인 금액이 올바르지 않습니다."
					);
		}

		if (application.getDisbursementAccount() == null) {
			throw new IllegalStateException(
					"대출금 수령 계좌가 지정되지 않았습니다."
					);
		}
	}


	@Transactional(propagation = Propagation.MANDATORY)
	public void createSchedule(
			LoanAccount loanAccount,
			LoanApplication application) {

		// 1. 입력값 검증
		if (loanAccount == null || application == null) {
			throw new IllegalArgumentException(
					"대출 계좌 또는 대출 신청 정보가 없습니다."
					);
		}

		// 2. 대출 계좌 저장 여부 확인
		if (loanAccount.getLoanAccountId() == null) {
			throw new IllegalStateException(
					"대출 계좌가 저장되지 않았습니다."
					);
		}

		// 3. 대출 신청 ID 확인
		if (application.getId() == null
				|| loanAccount.getLoanApplication() == null
				|| !application.getId().equals(
						loanAccount.getLoanApplication().getId())) {
			throw new IllegalStateException(
					"대출 계좌와 신청 정보가 일치하지 않습니다."
					);
		}

		// 4. 승인된 대출인지 확인
		if (application.getStatus()
				!= LoanApplicationStatus.APPROVED) {
			throw new IllegalStateException(
					"승인된 대출만 상환 일정을 생성할 수 있습니다."
					);
		}

		// 5. 상환 스케줄 중복 생성 방지
		if (schedules.existsByLoanAccount_LoanAccountId(
				loanAccount.getLoanAccountId())) {
			throw new IllegalStateException(
					"이미 상환 스케줄이 생성되었습니다."
					);
		}

		// 6. 상환 스케줄 계산
		List<LoanRepaymentSchedule> result =
				loanService.calculate(loanAccount, application);

		// 7. 상환 스케줄 저장
		schedules.saveAll(result);
	}



}