-- Meme schema. college_id / post_author_id are denormalised onto likes & comments so the
-- weekly leaderboard is a cheap index range scan per college.

CREATE TABLE authors (
    id           UUID PRIMARY KEY,
    username     VARCHAR(20)  NOT NULL UNIQUE,
    college_id   VARCHAR(80)  NOT NULL,
    display_name VARCHAR(40)  NOT NULL,
    avatar_url   VARCHAR(512),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE posts (
    id            UUID PRIMARY KEY,
    author_id     UUID         NOT NULL REFERENCES authors (id),
    college_id    VARCHAR(80)  NOT NULL,
    caption       VARCHAR(500),
    image_key     VARCHAR(300) NOT NULL,
    image_url     VARCHAR(700) NOT NULL,
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_posts_feed ON posts (college_id, created_at DESC, id DESC);
CREATE INDEX ix_posts_author ON posts (author_id, created_at DESC);

CREATE TABLE post_likes (
    post_id        UUID        NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL,
    college_id     VARCHAR(80) NOT NULL,
    post_author_id UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX ix_likes_board ON post_likes (college_id, created_at);
CREATE INDEX ix_likes_receiver ON post_likes (post_author_id);

CREATE TABLE comments (
    id             UUID PRIMARY KEY,
    post_id        UUID         NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
    author_id      UUID         NOT NULL REFERENCES authors (id),
    college_id     VARCHAR(80)  NOT NULL,
    post_author_id UUID         NOT NULL,
    body           VARCHAR(500) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_comments_post ON comments (post_id, created_at DESC, id DESC);
CREATE INDEX ix_comments_board ON comments (college_id, created_at);
CREATE INDEX ix_comments_receiver ON comments (post_author_id);

-- ===== Coins =====
CREATE TABLE wallets (
    user_id         UUID PRIMARY KEY REFERENCES authors (id),
    balance         BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    lifetime_earned BIGINT      NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE coin_transactions (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES authors (id),
    amount     INT          NOT NULL,
    kind       VARCHAR(32)  NOT NULL, -- LEADERBOARD_REWARD | GOODIE_CLAIM | GOODIE_REFUND | ADMIN_ADJUST
    note       VARCHAR(200),
    ref_id     UUID,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_coin_tx_user ON coin_transactions (user_id, created_at DESC);

CREATE TABLE leaderboard_payouts (
    id         UUID PRIMARY KEY,
    college_id VARCHAR(80) NOT NULL,
    week_start DATE        NOT NULL,
    rank       INT         NOT NULL,
    user_id    UUID        NOT NULL REFERENCES authors (id),
    likes      INT         NOT NULL,
    comments   INT         NOT NULL,
    score      INT         NOT NULL,
    coins      INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (college_id, week_start, rank),
    UNIQUE (college_id, week_start, user_id)
);

-- ===== Goodies =====
CREATE TABLE goodies (
    id                    UUID PRIMARY KEY,
    name                  VARCHAR(80)  NOT NULL,
    description           VARCHAR(400),
    image_url             VARCHAR(700),
    coin_cost             INT          NOT NULL DEFAULT 0 CHECK (coin_cost >= 0),
    min_comments_received INT          NOT NULL DEFAULT 0,
    stock                 INT,         -- NULL = unlimited
    once_per_user         BOOLEAN      NOT NULL DEFAULT FALSE,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order            INT          NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE goodie_claims (
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES authors (id),
    goodie_id     UUID         NOT NULL REFERENCES goodies (id),
    coins_spent   INT          NOT NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING | SHIPPED | DELIVERED | CANCELLED
    full_name     VARCHAR(80)  NOT NULL,
    phone         VARCHAR(15)  NOT NULL,
    address_line1 VARCHAR(160) NOT NULL,
    address_line2 VARCHAR(160),
    city          VARCHAR(60)  NOT NULL,
    state         VARCHAR(60)  NOT NULL,
    pincode       VARCHAR(6)   NOT NULL,
    admin_note    VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_claims_user ON goodie_claims (user_id, created_at DESC);
CREATE INDEX ix_claims_status ON goodie_claims (status, created_at);

-- ===== Ads (shown in-feed, Instagram style) =====
CREATE TABLE ads (
    id          UUID PRIMARY KEY,
    title       VARCHAR(80)  NOT NULL,
    body        VARCHAR(200),
    image_url   VARCHAR(700),
    cta_text    VARCHAR(30)  NOT NULL DEFAULT 'Learn more',
    target_url  VARCHAR(700) NOT NULL,
    college_id  VARCHAR(80), -- NULL = every college
    weight      INT          NOT NULL DEFAULT 1 CHECK (weight > 0),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    impressions BIGINT       NOT NULL DEFAULT 0,
    clicks      BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_ads_active ON ads (active, college_id);
