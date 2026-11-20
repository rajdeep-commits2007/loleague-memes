package com.loleague.meme.goodie;

import com.loleague.common.security.CurrentUser;
import com.loleague.meme.goodie.GoodieService.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goodies")
public class GoodieController {

    private final GoodieService goodies;

    public GoodieController(GoodieService goodies) {
        this.goodies = goodies;
    }

    @GetMapping
    public List<GoodieView> list(CurrentUser me) {
        return goodies.catalogue(me);
    }

    @PostMapping("/{id}/claim")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView claim(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody ClaimRequest req) {
        return goodies.claim(me, id, req);
    }

    @GetMapping("/claims")
    public List<ClaimView> myClaims(CurrentUser me) {
        return goodies.claims(me.id());
    }
}
