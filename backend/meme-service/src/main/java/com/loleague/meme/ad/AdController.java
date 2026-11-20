package com.loleague.meme.ad;

import com.loleague.common.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ads")
public class AdController {

    private final AdService ads;

    public AdController(AdService ads) {
        this.ads = ads;
    }

    @GetMapping
    public List<AdService.AdView> pick(CurrentUser me, @RequestParam(defaultValue = "3") int count) {
        return ads.pick(me, count);
    }

    @PostMapping("/{id}/impression")
    public ResponseEntity<Void> impression(@PathVariable UUID id) {
        ads.impression(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/click")
    public ResponseEntity<Void> click(@PathVariable UUID id) {
        ads.click(id);
        return ResponseEntity.noContent().build();
    }
}
