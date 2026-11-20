package com.loleague.meme.post;

import com.loleague.common.security.CurrentUser;
import com.loleague.common.web.ApiException;
import com.loleague.meme.author.AuthorRepository;
import com.loleague.meme.config.MemeProperties;
import com.loleague.meme.media.MediaService;
import com.loleague.meme.post.PostDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PostService {

    private static final int MAX_PAGE = 30;

    private final PostRepository posts;
    private final AuthorRepository authors;
    private final MediaService media;
    private final MemeProperties props;

    public PostService(PostRepository posts, AuthorRepository authors, MediaService media, MemeProperties props) {
        this.posts = posts;
        this.authors = authors;
        this.media = media;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Page<PostView> feed(CurrentUser me, String sort, String cursor, int limit) {
        int size = clamp(limit);
        if ("top".equalsIgnoreCase(sort)) {
            int offset = cursor == null || cursor.isBlank() ? 0 : parseOffset(cursor);
            List<PostRow> rows = posts.top(me.collegeId(), me.id(), offset, size,
                    props.leaderboard().likePoints(), props.leaderboard().commentPoints());
            String next = rows.size() == size && offset + size < 300 ? String.valueOf(offset + size) : null;
            return new Page<>(rows.stream().map(r -> r.toView(me.id())).toList(), next);
        }
        List<PostRow> rows = posts.latest(me.collegeId(), me.id(), Cursor.decode(cursor), size);
        return page(rows, size, me.id());
    }

    @Transactional(readOnly = true)
    public Page<PostView> byAuthor(CurrentUser me, String username, String cursor, int limit) {
        var author = authors.findByUsername(username, me.collegeId())
                .orElseThrow(() -> ApiException.notFound("User"));
        int size = clamp(limit);
        return page(posts.byAuthor(me.collegeId(), author.id(), me.id(), Cursor.decode(cursor), size), size, me.id());
    }

    @Transactional(readOnly = true)
    public PostView get(CurrentUser me, UUID id) {
        return posts.find(id, me.collegeId(), me.id())
                .map(r -> r.toView(me.id()))
                .orElseThrow(() -> ApiException.notFound("Meme"));
    }

    @Transactional
    public PostView create(CurrentUser me, CreatePostRequest req) {
        authors.ensure(me);
        String imageUrl = media.verifyUploaded(me, MediaService.Purpose.MEME, req.imageKey());
        UUID id = UUID.randomUUID();
        String caption = req.caption() == null || req.caption().isBlank() ? null : req.caption().trim();
        posts.insert(id, me.id(), me.collegeId(), caption, req.imageKey(), imageUrl);
        return get(me, id);
    }

    @Transactional
    public void delete(CurrentUser me, UUID id) {
        PostRow row = posts.find(id, me.collegeId(), me.id()).orElseThrow(() -> ApiException.notFound("Meme"));
        if (!row.authorId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("You can only delete your own memes");
        }
        posts.delete(id);
        media.delete(row.imageKey());
    }

    @Transactional
    public LikeResult like(CurrentUser me, UUID postId) {
        authors.ensure(me);
        posts.find(postId, me.collegeId(), me.id()).orElseThrow(() -> ApiException.notFound("Meme"));
        posts.like(postId, me.id(), me.collegeId());
        return new LikeResult(true, posts.likeCount(postId));
    }

    @Transactional
    public LikeResult unlike(CurrentUser me, UUID postId) {
        posts.find(postId, me.collegeId(), me.id()).orElseThrow(() -> ApiException.notFound("Meme"));
        posts.unlike(postId, me.id());
        return new LikeResult(false, posts.likeCount(postId));
    }

    private Page<PostView> page(List<PostRow> rows, int size, UUID me) {
        String next = null;
        if (rows.size() == size) {
            PostRow last = rows.getLast();
            next = new Cursor(last.createdAt(), last.id()).encode();
        }
        return new Page<>(rows.stream().map(r -> r.toView(me)).toList(), next);
    }

    private static int clamp(int limit) {
        return Math.max(1, Math.min(limit, MAX_PAGE));
    }

    private static int parseOffset(String cursor) {
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Bad cursor");
        }
    }
}
