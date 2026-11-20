package com.loleague.meme.author;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Service-to-service only (X-Internal-Key). Keeps author display data in sync with auth-service. */
@RestController
@RequestMapping("/internal/authors")
public class InternalAuthorController {

    public record AuthorSync(@NotBlank String username, @NotBlank String collegeId,
                             @NotBlank String displayName, String avatarUrl) {
    }

    private final AuthorRepository authors;

    public InternalAuthorController(AuthorRepository authors) {
        this.authors = authors;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> upsert(@PathVariable UUID id, @Valid @RequestBody AuthorSync body) {
        authors.upsert(id, body.username(), body.collegeId(), body.displayName(), body.avatarUrl());
        return ResponseEntity.noContent().build();
    }
}
