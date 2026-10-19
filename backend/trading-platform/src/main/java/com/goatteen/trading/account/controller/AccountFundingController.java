package com.goatteen.trading.account.controller;

import com.goatteen.trading.account.AccountFundingService;
import com.goatteen.trading.account.dto.AddFundsRequest;
import com.goatteen.trading.account.dto.AddFundsResponse;
import com.goatteen.trading.auth.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/me/funds")
public class AccountFundingController {
    private final AccountFundingService funding;
    private final CurrentUserService currentUser;

    public AccountFundingController(AccountFundingService funding, CurrentUserService currentUser) {
        this.funding = funding;
        this.currentUser = currentUser;
    }

    @PostMapping
    public AddFundsResponse addFunds(@Valid @RequestBody AddFundsRequest request) {
        return funding.addFunds(currentUser.getCurrentUserId(), request);
    }
}
