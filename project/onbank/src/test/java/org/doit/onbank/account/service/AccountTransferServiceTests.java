package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountTransfer;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.entity.TransferType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransferRepository;
import org.doit.onbank.account.repository.MemberTransferLimit;
import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.repository.MemberRepository;
import org.doit.onbank.otp.dto.OtpDeviceInfo;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// AccountTransferService 단위 테스트 : DB 대신 가짜(Mock) Repository 사용
// 확인하는 것 : 이체 순서(비밀번호 → 잠금), 한도 검사, 잠금 순서, 기록되는 값, 실패 시 아무것도 바뀌지 않는지
// 확인하지 못하는 것 : 실제 잠금(FOR UPDATE), REQUIRES_NEW 커밋 → DB 통합 테스트·화면에서 확인
@ExtendWith(MockitoExtension.class)
class AccountTransferServiceTests {

	private static final Long MY_ID = 100L;
	private static final Long OTHER_ID = 200L;
	private static final String SQNO = "20261015143012-482915";
	private static final String OTP_CODE = "123456";
	private static final long OTP_LINE = AccountTransferService.OTP_REQUIRED_AMOUNT;	// 10만 원

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountTransferRepository accountTransferRepository;

	@Mock
	private TransactionHistoryRepository transactionHistoryRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AccountPasswordVerifier accountPasswordVerifier;

	@Mock
	private TransactionSqnoGenerator transactionSqnoGenerator;

	@Mock
	private OtpService otpService;

	@InjectMocks
	private AccountTransferService service;

	private Account account(Long accountId, Long memberId, String number, AccountStatus status, long balance) {
		Account account = Account.builder()
				.memberId(memberId)
				.accountNumber(number)
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", accountId);	// DB가 넣어 주는 ID를 직접 설정
		return account;
	}

	private Member member(String name) {
		return Member.builder().loginId("id").password("pw").name(name).birthDate(LocalDate.of(2000, 1, 1)).build();
	}

	private MemberTransferLimit limit(Long daily, Long once) {
		return new MemberTransferLimit() {
			@Override
			public Long getDailyTransferLimit() {
				return daily;
			}

			@Override
			public Long getOnceTransferLimit() {
				return once;
			}
		};
	}

	// 내 계좌(from) → 다른 회원 계좌(to) 이체가 성공하는 데 필요한 설정
	private void givenTransferable(Account from, Account to) {
		given(accountRepository.findByAccountNumber(to.getAccountNumber())).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());	// 한도 NULL → 기본값
		given(accountPasswordVerifier.verify(from.getAccountId(), MY_ID, "1234")).willReturn(true);
		given(accountRepository.findByIdForUpdate(from.getAccountId())).willReturn(Optional.of(from));
		given(accountRepository.findByIdForUpdate(to.getAccountId())).willReturn(Optional.of(to));
	}

	// 사용 등록까지 마친 OTP가 있는 회원
	private void givenOtpActive() {
		given(otpService.findCurrent(MY_ID)).willReturn(Optional.of(otpDevice(OtpStatus.ACTIVE)));
	}

	private OtpDeviceInfo otpDevice(OtpStatus status) {
		return new OtpDeviceInfo("0123456789", status, 0, LocalDateTime.now(), LocalDateTime.now());
	}

	private void givenRecordable() {
		given(memberRepository.findById(MY_ID)).willReturn(Optional.of(member("홍길동")));
		given(memberRepository.findById(OTHER_ID)).willReturn(Optional.of(member("김철수")));
		given(transactionSqnoGenerator.generate(any())).willReturn(SQNO);
	}

	// ---------- 성공 ----------

	@Test
	void transferMovesMoneyAndRecordsThreeRows() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 5_000L);
		givenTransferable(from, to);
		givenRecordable();

		TransferReceipt receipt = service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", 30_000L, null, null);

		// 잔액과 오늘 이체 누적
		assertThat(from.getBalance()).isEqualTo(70_000L);
		assertThat(to.getBalance()).isEqualTo(35_000L);
		assertThat(from.getTodayTransferAmount()).isEqualTo(30_000L);
		assertThat(to.getTodayTransferAmount()).isZero();	// 받는 쪽은 누적하지 않음

		// 이체 1행
		ArgumentCaptor<AccountTransfer> transfer = ArgumentCaptor.forClass(AccountTransfer.class);
		verify(accountTransferRepository).save(transfer.capture());
		assertThat(transfer.getValue().getAccountId()).isEqualTo(1L);
		assertThat(transfer.getValue().getWithdrawalAccountNumber()).isEqualTo("TEST-0001");
		assertThat(transfer.getValue().getDepositAccountNumber()).isEqualTo("TEST-0002");
		assertThat(transfer.getValue().getFinancialInstitutionId()).isNull();	// 당행
		assertThat(transfer.getValue().getTransferAmount()).isEqualTo(30_000L);
		assertThat(transfer.getValue().getTransferType()).isEqualTo(TransferType.IMMEDIATE);
		assertThat(transfer.getValue().getTransactionSqno()).isEqualTo(SQNO);

		// 거래내역 2행 : 같은 거래 번호, 통장 표시를 비우면 상대방 이름
		ArgumentCaptor<TransactionHistory> history = ArgumentCaptor.forClass(TransactionHistory.class);
		verify(transactionHistoryRepository, times(2)).save(history.capture());
		List<TransactionHistory> rows = history.getAllValues();
		assertThat(rows.get(0).getAccountId()).isEqualTo(1L);
		assertThat(rows.get(0).getTransactionType()).isEqualTo(TransactionType.TRANSFER_OUT);
		assertThat(rows.get(0).getBalanceAfterTransaction()).isEqualTo(70_000L);
		assertThat(rows.get(0).getTransactionContent()).isEqualTo("김철수");
		assertThat(rows.get(1).getAccountId()).isEqualTo(2L);
		assertThat(rows.get(1).getTransactionType()).isEqualTo(TransactionType.TRANSFER_IN);
		assertThat(rows.get(1).getBalanceAfterTransaction()).isEqualTo(35_000L);
		assertThat(rows.get(1).getTransactionContent()).isEqualTo("홍길동");
		assertThat(rows).allMatch(row -> SQNO.equals(row.getTransactionSqno()));

		// 영수증 : 받는 분 이름은 가림
		assertThat(receipt.recipientName()).isEqualTo("김*수");
		assertThat(receipt.balanceAfter()).isEqualTo(70_000L);
	}

	@Test
	void transferUsesMemoWhenGiven() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();

		TransferReceipt receipt = service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", 10_000L, "월세", "10월 월세");

		assertThat(receipt.senderMemo()).isEqualTo("월세");
		assertThat(receipt.receiverMemo()).isEqualTo("10월 월세");
	}

	// 두 계좌를 항상 ID 오름차순으로 잠근다 (데드락 방지)
	@Test
	void transferLocksAccountsInIdOrder() {
		Account from = account(2L, MY_ID, "TEST-0002", AccountStatus.ACTIVE, 100_000L);
		Account to = account(1L, OTHER_ID, "TEST-0001", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();

		service.transfer(MY_ID, 2L, "1234", null, "TEST-0001", 10_000L, null, null);

		InOrder order = inOrder(accountPasswordVerifier, accountRepository);
		order.verify(accountPasswordVerifier).verify(2L, MY_ID, "1234");	// 비밀번호 확인이 잠금보다 먼저
		order.verify(accountRepository).findByIdForUpdate(1L);
		order.verify(accountRepository).findByIdForUpdate(2L);
	}

	// ---------- 실패 : 잠그기 전에 걸러지는 것 ----------

	@Test
	void transferFailsWhenRecipientNotFound() {
		given(accountRepository.findByAccountNumber("NONE")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "NONE", 10_000L, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("입금계좌를 찾을 수 없습니다");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
	}

	@Test
	void transferFailsWhenRecipientPending() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.PENDING, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", 10_000L, null, null))
				.hasMessageContaining("입금할 수 없는 계좌");
	}

	@Test
	void transferFailsWhenSameAccount() {
		Account mine = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 100_000L);
		given(accountRepository.findByAccountNumber("TEST-0001")).willReturn(Optional.of(mine));

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "TEST-0001", 10_000L, null, null))
				.hasMessageContaining("같은 계좌");
	}

	@Test
	void transferFailsWhenOverOnceLimit() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());	// 한도 NULL → 1회 기본 한도
		long overOnce = AccountTransferService.DEFAULT_ONCE_LIMIT + 1;	// 기본 한도가 바뀌어도 깨지지 않게 상수로 계산

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", overOnce, null, null))
				.hasMessageContaining("1회 이체한도");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
	}

	// 비밀번호가 틀리면 계좌를 잠그지도, 잔액을 바꾸지도 않는다
	@Test
	void transferFailsWhenPasswordWrong() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(accountPasswordVerifier.verify(1L, MY_ID, "0000")).willReturn(false);

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "0000", null, "TEST-0002", 10_000L, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("비밀번호");
		verify(accountRepository, never()).findByIdForUpdate(anyLong());
		verify(transactionHistoryRepository, never()).save(any());
	}

	// ---------- 실패 : 잠근 뒤 걸러지는 것 (아무것도 바뀌지 않아야 함) ----------

	@Test
	void transferFailsWhenOverDailyLimit() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 10_000_000L);
		// 오늘 (1일 기본 한도 - 50만 원)을 이미 이체함 → 남은 한도 50만 원 (기본 한도가 바뀌어도 깨지지 않게 상수로 계산)
		ReflectionTestUtils.setField(from, "todayTransferAmount", AccountTransferService.DEFAULT_DAILY_LIMIT - 500_000L);
		ReflectionTestUtils.setField(from, "lastTransactionAt", java.time.LocalDateTime.now());
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenOtpActive();	// 60만 원 → OTP 필요

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", OTP_CODE, "TEST-0002", 600_000L, null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1일 이체한도")
				.hasMessageContaining("500,000원");
		assertThat(from.getBalance()).isEqualTo(10_000_000L);
		assertThat(to.getBalance()).isZero();
		verify(accountTransferRepository, never()).save(any());
		verify(transactionHistoryRepository, never()).save(any());
	}

	@Test
	void transferFailsWhenBalanceInsufficient() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 5_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", 10_000L, null, null))
				.hasMessageContaining("잔액");
		assertThat(to.getBalance()).isZero();
		verify(transactionHistoryRepository, never()).save(any());
	}

	// ---------- 한도 조회 ----------

	@Test
	void limitUsesDefaultsWhenMemberLimitIsNull() {
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.of(limit(null, null)));

		TransferLimitInfo info = service.getLimit(MY_ID, null);

		assertThat(info.dailyLimit()).isEqualTo(AccountTransferService.DEFAULT_DAILY_LIMIT);
		assertThat(info.onceLimit()).isEqualTo(AccountTransferService.DEFAULT_ONCE_LIMIT);
	}

	@Test
	void limitUsesMemberValuesAndTodayUsed() {
		Account mine = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 0L);
		ReflectionTestUtils.setField(mine, "todayTransferAmount", 300_000L);
		ReflectionTestUtils.setField(mine, "lastTransactionAt", java.time.LocalDateTime.now());
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.of(limit(2_000_000L, 500_000L)));
		given(accountRepository.findById(1L)).willReturn(Optional.of(mine));

		TransferLimitInfo info = service.getLimit(MY_ID, 1L);

		assertThat(info.dailyLimit()).isEqualTo(2_000_000L);
		assertThat(info.onceLimit()).isEqualTo(500_000L);
		assertThat(info.todayUsed()).isEqualTo(300_000L);
		assertThat(info.remaining()).isEqualTo(1_700_000L);
	}

	// ---------- 이체 인증 : 기준 금액(10만 원) 이하 비밀번호만 / 초과 비밀번호 + OTP ----------

	// 기준 금액과 같으면 OTP를 묻지 않는다 (OTP가 없는 회원도 이체 가능)
	@Test
	void transferAtOtpLineNeedsPasswordOnly() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 500_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();

		service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", OTP_LINE, null, null);

		assertThat(from.getBalance()).isEqualTo(400_000L);
		verify(otpService, never()).findCurrent(anyLong());
		verify(otpService, never()).verify(anyLong(), any());
	}

	// 기준 금액을 넘으면 비밀번호 → OTP → 잠금 순서로 확인한다
	@Test
	void transferOverOtpLineVerifiesOtpBeforeLocking() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 500_000L);
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		givenRecordable();
		givenOtpActive();

		service.transfer(MY_ID, 1L, "1234", OTP_CODE, "TEST-0002", OTP_LINE + 1, null, null);

		assertThat(to.getBalance()).isEqualTo(OTP_LINE + 1);
		InOrder order = inOrder(accountPasswordVerifier, otpService, accountRepository);
		order.verify(accountPasswordVerifier).verify(1L, MY_ID, "1234");
		order.verify(otpService).verify(MY_ID, OTP_CODE);
		order.verify(accountRepository).findByIdForUpdate(1L);
	}

	// OTP가 없는 회원의 기준 금액 초과 이체 : 비밀번호도 확인하지 않고 막는다 (오류 횟수가 늘지 않게)
	@Test
	void transferOverOtpLineFailsWhenOtpNotRegistered() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(otpService.findCurrent(MY_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", OTP_CODE, "TEST-0002", OTP_LINE + 1, null, null))
				.isInstanceOf(OtpRequiredException.class)
				.hasMessageContaining("100,000원을 넘는 이체는 OTP 등록이 필요해요");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
		verify(otpService, never()).verify(anyLong(), any());
		verify(accountRepository, never()).findByIdForUpdate(anyLong());
	}

	// 발급만 받고 사용 등록을 하지 않은 OTP도 미등록으로 본다
	@Test
	void transferOverOtpLineFailsWhenOtpOnlyIssued() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(otpService.findCurrent(MY_ID)).willReturn(Optional.of(otpDevice(OtpStatus.ISSUED)));

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", OTP_CODE, "TEST-0002", OTP_LINE + 1, null, null))
				.isInstanceOf(OtpRequiredException.class);
	}

	// 화면을 거치지 않고 OTP 번호 없이 보내도 서버에서 막는다
	@Test
	void transferOverOtpLineFailsWhenOtpCodeMissing() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		givenOtpActive();

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", OTP_LINE + 1, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("OTP 번호 6자리");
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), anyString());
	}

	// OTP가 틀리면 팀1 OtpService 의 예외가 그대로 나가고 계좌는 잠그지 않는다
	@Test
	void transferOverOtpLineFailsWhenOtpWrong() {
		Account to = account(2L, OTHER_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber("TEST-0002")).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(accountPasswordVerifier.verify(1L, MY_ID, "1234")).willReturn(true);
		givenOtpActive();
		willThrow(new BusinessException("OTP 번호가 올바르지 않습니다. (오류 1/10회)"))
				.given(otpService).verify(MY_ID, "999999");

		assertThatThrownBy(() -> service.transfer(MY_ID, 1L, "1234", "999999", "TEST-0002", OTP_LINE + 1, null, null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("OTP 번호가 올바르지 않습니다");
		verify(accountRepository, never()).findByIdForUpdate(anyLong());
		verify(transactionHistoryRepository, never()).save(any());
	}

	// 본인 계좌끼리는 기준 금액을 넘어도 OTP를 묻지 않는다
	@Test
	void transferToOwnAccountSkipsOtp() {
		Account from = account(1L, MY_ID, "TEST-0001", AccountStatus.ACTIVE, 900_000L);
		Account to = account(2L, MY_ID, "TEST-0002", AccountStatus.ACTIVE, 0L);
		givenTransferable(from, to);
		given(memberRepository.findById(MY_ID)).willReturn(Optional.of(member("홍길동")));
		given(transactionSqnoGenerator.generate(any())).willReturn(SQNO);

		service.transfer(MY_ID, 1L, "1234", null, "TEST-0002", 500_000L, null, null);

		assertThat(to.getBalance()).isEqualTo(500_000L);
		verify(otpService, never()).findCurrent(anyLong());
		verify(otpService, never()).verify(anyLong(), any());
	}

	// ---------- 예약·정기이체 실행 (인증 없이) ----------

	// 등록할 때 인증을 마쳤으므로 비밀번호·OTP를 묻지 않고, 기록에 예약 유형을 남긴다
	@Test
	void preAuthorizedTransferSkipsAuthAndRecordsType() {
		Account from = account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE, 1_000_000L);
		Account to = account(2L, OTHER_ID, "1002-222-222222", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber(to.getAccountNumber())).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(from));
		given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(to));
		givenRecordable();

		service.transferPreAuthorized(MY_ID, 1L, to.getAccountNumber(), 300_000L, null, null, TransferType.SCHEDULED);

		assertThat(from.getBalance()).isEqualTo(700_000L);
		assertThat(to.getBalance()).isEqualTo(300_000L);
		ArgumentCaptor<AccountTransfer> transfer = ArgumentCaptor.forClass(AccountTransfer.class);
		verify(accountTransferRepository).save(transfer.capture());
		assertThat(transfer.getValue().getTransferType()).isEqualTo(TransferType.SCHEDULED);
		verify(accountPasswordVerifier, never()).verify(anyLong(), anyLong(), any());	// 10만 원을 넘어도 인증 없음
		verify(otpService, never()).verify(anyLong(), any());
	}

	// 실행 시점에 잔액이 모자라면 예외 (스케줄러가 실패로 기록) : 잔액은 바뀌지 않는다
	@Test
	void preAuthorizedTransferFailsWhenBalanceIsShort() {
		Account from = account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE, 10_000L);
		Account to = account(2L, OTHER_ID, "1002-222-222222", AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByAccountNumber(to.getAccountNumber())).willReturn(Optional.of(to));
		given(accountRepository.findTransferLimit(MY_ID)).willReturn(Optional.empty());
		given(accountRepository.findByIdForUpdate(1L)).willReturn(Optional.of(from));
		given(accountRepository.findByIdForUpdate(2L)).willReturn(Optional.of(to));

		assertThatThrownBy(() -> service.transferPreAuthorized(MY_ID, 1L, to.getAccountNumber(), 50_000L,
				null, null, TransferType.RECURRING))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("잔액");
		assertThat(from.getBalance()).isEqualTo(10_000L);
		verify(accountTransferRepository, never()).save(any());
	}
}
