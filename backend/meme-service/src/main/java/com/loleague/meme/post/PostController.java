package com.loleague.meme.post;

import com.loleague.common.security.CurrentUser;
import com.loleague.meme.post.PostDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Everything is scoped to the caller's college (taken from the JWT, never from the request). */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @GetMapping
    public Page<PostView> feed(CurrentUser me,
                               @RequestParam(defaultValue = "new") String sort,
                               @RequestParam(required = false) String author,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "10") int limit) {
        if (author != null && !author.isBlank()) {
            return posts.byAuthor(me, author, cursor, limit);
        }
        return posts.feed(me, sort, cursor, limit);
    }

    @GetMapping("/{id}")
    public PostView get(CurrentUser me, @PathVariable UUID id) {
        return posts.get(me, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostView create(CurrentUser me, @Valid @RequestBody CreatePostRequest req) {
        return posts.create(me, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(CurrentUser me, @PathVariable UUID id) {
        posts.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/like")
    public LikeResult like(CurrentUser me, @PathVariable UUID id) {
        return posts.like(me, id);
    }

    @DeleteMapping("/{id}/like")
    public LikeResult unlike(CurrentUser me, @PathVariable UUID id) {
        return posts.unlike(me, id);
    }
}
