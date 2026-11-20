package com.loleague.meme.comment;

import com.loleague.common.security.CurrentUser;
import com.loleague.meme.comment.CommentService.CommentView;
import com.loleague.meme.comment.CommentService.CreateCommentRequest;
import com.loleague.meme.comment.CommentService.EditCommentRequest;
import com.loleague.meme.comment.CommentService.MentionCandidate;
import com.loleague.meme.comment.CommentService.ReactRequest;
import com.loleague.meme.post.PostDtos.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class CommentController {

    private final CommentService comments;

    public CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/api/posts/{postId}/comments")
    public Page<CommentView> list(CurrentUser me, @PathVariable UUID postId,
                                  @RequestParam(required = false) String cursor,
                                  @RequestParam(defaultValue = "20") int limit) {
        return comments.list(me, postId, cursor, limit);
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView create(CurrentUser me, @PathVariable UUID postId, @Valid @RequestBody CreateCommentRequest req) {
        return comments.create(me, postId, req);
    }

    @GetMapping("/api/comments/{id}/replies")
    public Page<CommentView> replies(CurrentUser me, @PathVariable UUID id,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "10") int limit) {
        return comments.replies(me, id, cursor, limit);
    }

    @PatchMapping("/api/comments/{id}")
    public CommentView edit(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody EditCommentRequest req) {
        return comments.edit(me, id, req);
    }

    @PostMapping("/api/comments/{id}/reactions")
    public CommentView react(CurrentUser me, @PathVariable UUID id, @Valid @RequestBody ReactRequest req) {
        return comments.react(me, id, req);
    }

    @GetMapping("/api/comments/mentions")
    public List<MentionCandidate> mentions(CurrentUser me, @RequestParam(defaultValue = "") String q) {
        return comments.mentionCandidates(me, q);
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(CurrentUser me, @PathVariable UUID id) {
        comments.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
