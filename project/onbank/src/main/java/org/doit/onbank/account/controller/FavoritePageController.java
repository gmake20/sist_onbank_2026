package org.doit.onbank.account.controller;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.doit.onbank.account.dto.FavoriteEditForm;
import org.doit.onbank.account.dto.FavoriteForm;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountFavorite;
import org.doit.onbank.account.service.AccountFavoriteService;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 즐겨찾기 관리 화면 : 목록, 등록, 수정, 순서 이동, 삭제
// 전계좌조회의 ☆/★, 이체 완료 화면의 [즐겨찾기 추가]도 여기로 등록·삭제한다
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/favorites")
public class FavoritePageController {

	// 등록·삭제 후 돌아갈 화면 (returnTo 값). 정해진 값만 받아 다른 주소로 보내지 않는다
	private static final String RETURN_ACCOUNTS = "accounts";

	private final AccountFavoriteService accountFavoriteService;
	private final AccountService accountService;

	// ---------- 목록·등록 ----------

	@GetMapping
	public String list(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("form", new FavoriteForm());
		return showList(me, model);
	}

	@PostMapping
	public String add(@AuthenticationPrincipal LoginMember me,
			@Valid @ModelAttribute("form") FavoriteForm form, BindingResult bindingResult,
			@RequestParam(name = "returnTo", required = false) String returnTo,
			Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			return showList(me, model);
		}

		try {
			accountFavoriteService.add(me.getMemberId(), form.getAccountId(), form.getRecipientAccountNumber(),
					form.getFavoriteAlias(), form.getMemo());
			redirectAttributes.addFlashAttribute("message", "즐겨찾기에 등록했습니다.");
		} catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
			if (!RETURN_ACCOUNTS.equals(returnTo)) {
				model.addAttribute("errorMessage", e.getMessage());
				return showList(me, model);
			}
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return redirectTo(returnTo);	// PRG : 새로고침해도 다시 등록되지 않음
	}

	// ---------- 수정 ----------

	@GetMapping("/{favoriteId}/edit")
	public String editForm(@AuthenticationPrincipal LoginMember me, @PathVariable("favoriteId") Long favoriteId,
			Model model) {
		AccountFavorite favorite = accountFavoriteService.getMyFavorite(me.getMemberId(), favoriteId);
		model.addAttribute("favorite", favorite);
		model.addAttribute("form", FavoriteEditForm.from(favorite));
		return "account/favorite-edit";
	}

	@PostMapping("/{favoriteId}/edit")
	public String edit(@AuthenticationPrincipal LoginMember me, @PathVariable("favoriteId") Long favoriteId,
			@Valid @ModelAttribute("form") FavoriteEditForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("favorite", accountFavoriteService.getMyFavorite(me.getMemberId(), favoriteId));
			return "account/favorite-edit";
		}
		accountFavoriteService.update(me.getMemberId(), favoriteId, form.getFavoriteAlias(), form.getMemo());
		redirectAttributes.addFlashAttribute("message", "즐겨찾기를 수정했습니다.");
		return "redirect:/mybank/favorites";
	}

	// ---------- 순서 이동·삭제 ----------

	@PostMapping("/{favoriteId}/move")
	public String move(@AuthenticationPrincipal LoginMember me, @PathVariable("favoriteId") Long favoriteId,
			@RequestParam("direction") String direction) {
		accountFavoriteService.move(me.getMemberId(), favoriteId, "up".equals(direction));
		return "redirect:/mybank/favorites";
	}

	@PostMapping("/{favoriteId}/delete")
	public String delete(@AuthenticationPrincipal LoginMember me, @PathVariable("favoriteId") Long favoriteId,
			@RequestParam(name = "returnTo", required = false) String returnTo,
			RedirectAttributes redirectAttributes) {
		accountFavoriteService.delete(me.getMemberId(), favoriteId);
		redirectAttributes.addFlashAttribute("message", "즐겨찾기를 삭제했습니다.");
		return redirectTo(returnTo);
	}

	// 없는 즐겨찾기, 남의 즐겨찾기 → 목록으로
	@ExceptionHandler(NoSuchElementException.class)
	public String handleNotFound(NoSuchElementException e, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		return "redirect:/mybank/favorites";
	}

	private String redirectTo(String returnTo) {
		return RETURN_ACCOUNTS.equals(returnTo) ? "redirect:/mybank/accounts" : "redirect:/mybank/favorites";
	}

	private String showList(LoginMember me, Model model) {
		List<Account> myAccounts = accountService.getMyAccounts(me.getMemberId());
		// 기본 출금계좌로 고를 수 있는 계좌 : 출금 가능한 내 계좌
		model.addAttribute("accounts", myAccounts.stream().filter(Account::canWithdraw).toList());
		// 목록의 "기본 출금계좌" 열 : 계좌 ID → 계좌번호
		model.addAttribute("accountNumbers", myAccounts.stream()
				.collect(Collectors.toMap(Account::getAccountId, Account::getAccountNumber)));
		model.addAttribute("favorites", accountFavoriteService.getMyFavorites(me.getMemberId()));
		model.addAttribute("maxFavorites", AccountFavoriteService.MAX_FAVORITES);
		return "account/favorites";
	}
}
