package com.loleague.meme.leaderboard;

import com.loleague.common.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    /** week=current (live, refreshed every minute) or week=previous (final, coins paid). */
    @GetMapping
    public LeaderboardService.Board board(CurrentUser me, @RequestParam(defaultValue = "current") String week) {
        return leaderboard.board(me, week);
    }
}
