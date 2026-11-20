-- One chat room per college. username is permanent so it is safe to store with the message.
CREATE TABLE messages (
    id          UUID PRIMARY KEY,
    college_id  VARCHAR(80)   NOT NULL,
    user_id     UUID          NOT NULL,
    username    VARCHAR(20)   NOT NULL,
    kind        VARCHAR(8)    NOT NULL CHECK (kind IN ('TEXT', 'GIF')),
    content     VARCHAR(1000) NOT NULL,
    gif_width   INT,
    gif_height  INT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_messages_room ON messages (college_id, created_at DESC, id DESC);
