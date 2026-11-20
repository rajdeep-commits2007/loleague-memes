package com.loleague.chat.message;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Service-to-service only (X-Internal-Key). Keeps the username stored on old messages current after a rename. */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    public record Rename(@NotBlank @Size(max = 20) String username) {
    }

    private final JdbcClient jdbc;

    public InternalUserController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @PutMapping("/{id}/username")
    public ResponseEntity<Void> rename(@PathVariable UUID id, @Valid @RequestBody Rename body) {
        jdbc.sql("UPDATE messages SET username = :u WHERE user_id = :id AND username <> :u")
                .param("u", body.username())
                .param("id", id)
                .update();
        return ResponseEntity.noContent().build();
    }
}
