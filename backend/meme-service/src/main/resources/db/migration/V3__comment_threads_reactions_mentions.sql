-- Threaded comments (one level of replies, Instagram/YouTube style), edits, reactions and @mentions.

ALTER TABLE comments
    ADD COLUMN parent_id   UUID REFERENCES comments (id) ON DELETE CASCADE,
    ADD COLUMN reply_count INT         NOT NULL DEFAULT 0,
    ADD COLUMN edited_at   TIMESTAMPTZ;

-- Top-level comments of a post (newest first) and replies of a comment (oldest first).
CREATE INDEX ix_comments_post_top ON comments (post_id, created_at DESC, id DESC) WHERE parent_id IS NULL;
CREATE INDEX ix_comments_replies ON comments (parent_id, created_at, id) WHERE parent_id IS NOT NULL;

CREATE TABLE comment_reactions (
    comment_id UUID        NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES authors (id),
    emoji      VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id, emoji)
);

CREATE TABLE comment_mentions (
    comment_id UUID NOT NULL REFERENCES comments (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES authors (id),
    PRIMARY KEY (comment_id, user_id)
);
CREATE INDEX ix_comment_mentions_user ON comment_mentions (user_id);

-- Prefix search for the @mention picker, scoped to a college.
CREATE INDEX ix_authors_college_username ON authors (college_id, lower(username) varchar_pattern_ops);
