package com.loleague.meme.leaderboard;

import com.loleague.common.security.CurrentUser;
import com.loleague.meme.config.MemeProperties;
import com.loleague.meme.leaderboard.LeaderboardRepository.ScoreRow;
import com.loleague.meme.wallet.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final long CACHE_SECONDS = 60;

    public record Entry(long rank, UUID userId, String username, String displayName, String avatarUrl,
                        int likes, int comments, int score, int coins) {
    }

    public record Board(LocalDate weekStart, LocalDate weekEnd, boolean finalized, List<Integer> rewards,
                        List<Entry> entries, Entry me) {
    }

    private record Cached(List<Entry> entries, Instant at) {
    }

    private final LeaderboardRepository repo;
    private final WalletRepository wallets;
    private final TransactionTemplate tx;
    private final MemeProperties.Leaderboard config;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public LeaderboardService(LeaderboardRepository repo, WalletRepository wallets, PlatformTransactionManager txManager,
                              MemeProperties props) {
        this.repo = repo;
        this.wallets = wallets;
        this.tx = new TransactionTemplate(txManager);
        this.config = props.leaderboard();
    }

    public Board board(CurrentUser me, String which) {
        Week week = "previous".equalsIgnoreCase(which) ? Week.current().previous() : Week.current();
        List<Integer> rewards = config.rewardList();

        List<Entry> entries;
        boolean finalized = false;
        if (week.start().isBefore(Week.current().start())) {
            var paid = repo.payouts(me.collegeId(), week.start());
            if (!paid.isEmpty()) {
                finalized = true;
                entries = paid.stream().map(p -> new Entry(p.rank(), p.userId(), p.username(), p.displayName(),
                        p.avatarUrl(), p.likes(), p.comments(), p.score(), p.coins())).toList();
            } else {
                entries = live(me.collegeId(), week, rewards);
            }
        } else {
            entries = live(me.collegeId(), week, rewards);
        }

        Entry mine = entries.stream().filter(e -> e.userId().equals(me.id())).findFirst()
                .orElseGet(() -> repo.standing(me.collegeId(), week, me.id(), config.likePoints(), config.commentPoints())
                        .map(r -> toEntry(r, rewards))
                        .orElse(null));
        return new Board(week.start(), week.lastDay(), finalized, rewards, entries, mine);
    }

    private List<Entry> live(String collegeId, Week week, List<Integer> rewards) {
        String key = collegeId + "|" + week.start();
        Cached cached = cache.get(key);
        if (cached != null && cached.at().plusSeconds(CACHE_SECONDS).isAfter(Instant.now())) {
            return cached.entries();
        }
        List<Entry> entries = repo.top(collegeId, week, rewards.size(), config.likePoints(), config.commentPoints())
                .stream().map(r -> toEntry(r, rewards)).toList();
        cache.put(key, new Cached(entries, Instant.now()));
        return entries;
    }

    private static Entry toEntry(ScoreRow r, List<Integer> rewards) {
        int coins = r.rank() <= rewards.size() && r.score() > 0 ? rewards.get((int) r.rank() - 1) : 0;
        return new Entry(r.rank(), r.userId(), r.username(), r.displayName(), r.avatarUrl(),
                r.likes(), r.comments(), r.score(), coins);
    }

    // ---------------------------------------------------------------- payouts

    /** Every Monday 00:05 IST, pay last week's top creators in every college. */
    @Scheduled(cron = "0 5 0 * * MON", zone = "Asia/Kolkata")
    public void weeklyPayout() {
        payout(Week.current().previous());
    }

    /** Render's free instances sleep, so a missed cron is caught up on boot. Payouts are idempotent. */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnBoot() {
        try {
            payout(Week.current().previous());
        } catch (Exception e) {
            log.error("Leaderboard catch-up failed", e);
        }
    }

    public int payout(Week week) {
        List<Integer> rewards = config.rewardList();
        int paid = 0;
        for (String college : repo.activeColleges(week)) {
            Integer count = tx.execute(status -> {
                int n = 0;
                for (ScoreRow row : repo.top(college, week, rewards.size(), config.likePoints(), config.commentPoints())) {
                    int coins = rewards.get((int) row.rank() - 1);
                    if (coins > 0 && repo.insertPayout(college, week.start(), row, coins)) {
                        wallets.credit(row.userId(), coins, "LEADERBOARD_REWARD",
                                "Rank #" + row.rank() + " for week of " + week.start(), null);
                        n++;
                    }
                }
                return n;
            });
            paid += count == null ? 0 : count;
        }
        if (paid > 0) {
            log.info("Paid {} leaderboard rewards for week {}", paid, week.start());
        }
        return paid;
    }
}
