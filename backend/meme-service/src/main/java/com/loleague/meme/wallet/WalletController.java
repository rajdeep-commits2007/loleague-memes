package com.loleague.meme.wallet;

import com.loleague.common.security.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    public record WalletView(long balance, long lifetimeEarned, long commentsReceived, long likesReceived,
                             List<WalletRepository.TransactionRow> transactions) {
    }

    private final WalletRepository wallets;

    public WalletController(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public WalletView wallet(CurrentUser me) {
        var w = wallets.get(me.id());
        return new WalletView(w.balance(), w.lifetimeEarned(),
                wallets.commentsReceived(me.id()), wallets.likesReceived(me.id()),
                wallets.recent(me.id(), 30));
    }
}
