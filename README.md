# LOLeague 🟣🩷🟢

**Campus meme league for West Bengal colleges.** Students join with their college, post memes only their batchmates can see, climb a weekly leaderboard, earn coins, claim real goodies, and hang out in a live college chat room.

| Layer | Stack |
|---|---|
| Frontend | Next.js 16 (App Router, Turbopack, `proxy.ts`), React 19, TypeScript 6, Tailwind CSS 4, shadcn/ui (Radix), Redux Toolkit 2 + RTK Query (infinite queries), react-hook-form + Zod 4, STOMP.js, emoji-picker-react |
| Backend | Java 25, Spring Boot 4.1, Spring Security 7 (OAuth2 resource server, HS256 JWT in HttpOnly cookies), Spring WebSocket (STOMP), JDBC/JPA, Flyway |
| Data | PostgreSQL 18 (one database, one schema per service) |
| Media | AWS S3, presigned PUT uploads straight from the browser |
| Infra | Docker, docker-compose, Render Blueprint (`render.yaml`) |

---

## 1. Architecture

```
                 Browser (only talks to ONE origin)
                         │  HttpOnly cookies: ll_access (15 min JWT), ll_refresh (30 d), ll_session hint
                         ▼
        ┌──────────────────────────────────┐
        │  Next.js  (loleague-web)          │  proxy.ts → route guard
        │  /api/[...path]  BFF proxy  ──────┼──────────────┬───────────────────┐
        └──────────────────────────────────┘              │                   │
            /api/auth/*, /api/users/*     /api/posts, comments, media,      /api/chat/*
                     │                     leaderboard, wallet, goodies,        │
                     ▼                     ads, admin                           ▼
        ┌──────────────────────┐          ┌───────────────────────┐   ┌──────────────────────┐
        │ auth-service :8081   │─sync────▶│ meme-service :8082     │   │ chat-service :8083   │◀── WebSocket (STOMP)
        │ users, colleges.json │ profile  │ posts, likes, comments │   │ 1 room per college   │    with 60 s ticket
        │ refresh-token rotation│          │ leaderboard + payouts  │   │ GIF proxy (GIPHY)    │
        └─────────┬────────────┘          │ coins, goodies, ads, S3│   └─────────┬────────────┘
                  │ schema auth           └───────────┬────────────┘             │ schema chat
                  └───────────────────────────────────┴─ schema meme ────────────┘
                                         PostgreSQL 18
```

**Why 3 services?** They scale and fail independently: chat holds long-lived sockets, memes is the read-heavy hot path, auth is small and security-critical. One shared Postgres (separate schemas, separate Flyway histories) keeps cost low at 1-2K users and still comfortably handles 1 lakh.

### Key rules implemented
- **College isolation:** every meme/comment/like/leaderboard/chat query is filtered by the `college` claim inside the verified JWT, never by request input. Another college's meme returns 404.
- **Permanent identity:** username and college have no setters, `updatable=false`, *and* a Postgres trigger rejects changes.
- **Unique usernames, live check:** `GET /api/auth/username-available` (debounced while typing), case-insensitive unique index, reserved words blocked.
- **Cookies, not localStorage:** access JWT is HttpOnly + SameSite=Lax (+ Secure in prod). Refresh tokens are opaque, stored SHA-256 hashed, rotated on every use; replaying an old one revokes the whole login family. Changing your password logs out other devices.
- **Chat auth:** the browser fetches a 60-second `ws` ticket over the cookie-authenticated proxy, then opens the socket to chat-service with it. Subscriptions to other colleges' rooms are rejected.

---

## 2. Features → where they live

| Feature | Backend | Frontend |
|---|---|---|
| Sign-up: college dropdown → unique username (live check) → password + confirm | `auth-service` `AuthController`, `colleges.json` | `/register` (shadcn Combobox) |
| Login / logout / silent refresh | `TokenService`, `AuthCookies` | `/login`, `store/api/baseApi.ts` (single-flight refresh) |
| Profile: display name, bio, avatar, birthday, change password | `UserController` | `/profile` |
| Memes: upload to S3, feed (Fresh / Top this week), like, comment, delete | `meme-service` `PostController`, `CommentController`, `MediaService` | `/feed`, `/post/[id]`, `/u/[username]` |
| In-feed ads every 5th card, impression + click tracking | `AdService` | `components/feed/ad-card.tsx` |
| Weekly top-10 leaderboard per college | `LeaderboardService` | `/leaderboard` |
| Coins: 10, 5, 3, 2, 1, 1, 1, 1, 1, 1 paid every Monday 00:05 IST | `LeaderboardService.weeklyPayout` (+ catch-up on boot) | wallet chip + `/rewards` |
| Goodies paid with coins, delivered to an address; 20K-comments milestone box | `GoodieService` | `/rewards` |
| Discord-style college chat: text, emojis, GIFs, online count | `chat-service` | `/chat` |
| Admin: deliveries, ads, manual payouts | `AdminController` (`/api/admin/**`, ROLE_ADMIN) | API only (curl/Postman) |

### Leaderboard & coin rules (all configurable)
- **Score** = likes received + 2 × comments received during the week (Mon 00:00 → Mon 00:00 IST). Self-likes/comments don't count. Ties: more comments, then more likes.
- **Rewards** for rank 1..10: `LEADERBOARD_REWARDS=10,5,3,2,1,1,1,1,1,1`.
- **Goodies** (seeded in `meme-service/.../V2__seed_goodies_and_ads.sql`): Sticker pack 2,000 coins · Tee 5,000 · Hoodie 10,000 · **20K Comments Legend Box** free once you've received 20,000 comments. Change prices, stock or add items in that SQL (or a new migration).
- Payouts are idempotent (unique per college/week/rank), so restarts or re-runs never double-pay.

> Heads-up: at 10 coins/week for #1, 2,000-10,000 coin goodies take a long time. You'll probably want either bigger weekly rewards (e.g. `1000,500,300,...`) or cheaper goodies. It's all config, no code change needed.

---

## 3. Design system: 1 : 2 : 1

| Share | Colour | Used for |
|---|---|---|
| **2** | Grape `#6B3CF6` | Surfaces, nav, headers, active tabs, highlights |
| **1** | Bubblegum `#FF3D8B` | Icons |
| **1** | Lime `#C6F432` | Buttons & calls to action (shadcn `primary`) |
| base | Ink `#1A1033` on Cream `#FFF9EE` | Text, chunky borders and hard "meme" shadows |

Tokens live in `frontend/src/app/globals.css` (Tailwind v4 `@theme`). Dark mode included. All shadcn components are in `src/components/ui`, and `components.json` is set up, so `npx shadcn@latest add <component>` works.

---

## 4. Run locally

Requirements: Docker Desktop (or Docker Engine + compose).

```bash
cp .env.example .env        # already done for you: .env has random local secrets
# put your AWS + GIPHY keys into .env
docker compose up --build
```

- Web: http://localhost:3000
- Auth / Meme / Chat: http://localhost:8081, 8082, 8083 (`/actuator/health`)

**Without Docker** (hot reload):
```bash
# Postgres 18 running locally with db/user/pass = loleague
cd backend && mvn -pl auth-service -am spring-boot:run     # and meme-service, chat-service in other terminals
cd frontend && npm install && npm run dev                   # defaults to localhost:8081/8082/8083
```
Export the variables from `.env` in each backend terminal (at minimum `JWT_SECRET`, `INTERNAL_API_KEY`, `DB_HOST=localhost`).

---

## 5. AWS S3 setup (memes + avatars)

1. Create a bucket, e.g. `loleague-memes` in `ap-south-1` (Mumbai).
2. **Public read** for objects (or put CloudFront in front and set `AWS_S3_PUBLIC_BASE_URL` to it). Turn off "Block all public access" for this bucket, then add this bucket policy:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Sid": "PublicReadMemes",
       "Effect": "Allow",
       "Principal": "*",
       "Action": "s3:GetObject",
       "Resource": ["arn:aws:s3:::loleague-memes/memes/*", "arn:aws:s3:::loleague-memes/avatars/*"]
     }]
   }
   ```
3. **CORS** (Permissions → CORS) so the browser can upload with the presigned URL:
   ```json
   [{
     "AllowedOrigins": ["http://localhost:3000", "https://loleague-web.onrender.com"],
     "AllowedMethods": ["PUT"],
     "AllowedHeaders": ["Content-Type", "Cache-Control"],
     "MaxAgeSeconds": 3000
   }]
   ```
4. Create an IAM user with only this policy, then put its keys in `.env` (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`):
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
       "Resource": "arn:aws:s3:::loleague-memes/*"
     }]
   }
   ```
5. Optional: a lifecycle rule to delete orphaned uploads (images picked but never posted).

Limits: memes ≤ 8 MB, avatars ≤ 2 MB, JPG/PNG/WEBP/GIF. The size and type are signed into the URL and re-checked with `HEAD` before a post is created.

**GIFs:** create a free key at https://developers.giphy.com and set `GIPHY_API_KEY`. The key stays on the server (chat-service proxies search).

---

## 6. Deploy on Render

1. Push this repo to GitHub.
2. Render → **New → Blueprint** → pick the repo. `render.yaml` creates:
   - `loleague-db` (Postgres 18), `loleague-auth`, `loleague-meme`, `loleague-chat`, `loleague-web` (all Docker, Singapore region, closest to India)
   - a shared env group with auto-generated `JWT_SECRET` and `INTERNAL_API_KEY`
3. Fill in the `sync: false` values when prompted: AWS keys, bucket, public URL, `GIPHY_API_KEY`, `ADMIN_USERNAMES`.
4. If your web service URL isn't `https://loleague-web.onrender.com`, update `FRONTEND_URL` on `loleague-chat` and the S3 CORS origin.
5. Open the web URL and register. 🎉

Notes:
- The blueprint uses `starter` instances because services call each other over Render's private network. On **free** instances, set `AUTH_SERVICE_URL` / `MEME_SERVICE_URL` / `CHAT_SERVICE_URL` (web) and `MEME_SERVICE_URL` (auth) to the public `https://<service>.onrender.com` URLs instead. Free instances also sleep. Missed payouts are caught up automatically on boot.
- The chat-service works out its public `wss://` URL from `RENDER_EXTERNAL_URL`; set `CHAT_PUBLIC_WS_URL` for a custom domain.

---

## 7. Scaling to 1 lakh students

What's already in place: keyset pagination everywhere, denormalised counters, `(college_id, created_at)` indexes for the leaderboard, direct-to-S3 uploads, virtual threads, small Hikari pools, idempotent jobs.

When you outgrow one instance:
1. **Chat:** switch `enableSimpleBroker` to `enableStompBrokerRelay` (RabbitMQ) so several chat instances share rooms. Move presence/rate-limits to Redis.
2. **Auth:** move `LoginThrottle` to Redis.
3. **Images:** put CloudFront in front of S3.
4. **Leaderboard:** cache is 60 s in-memory per instance; move to Redis or a materialised view if needed.
5. **Payout job:** if you run more than one meme-service instance it's still safe (unique constraints), but you can add ShedLock to avoid duplicate work.

---

## 8. API cheat-sheet

All paths are relative to the web origin (`/api/...`), proxied to the right service.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/auth/colleges` | dropdown data |
| GET | `/api/auth/username-available?username=` | live check |
| POST | `/api/auth/register` `{collegeId, username, password, confirmPassword}` | sets cookies |
| POST | `/api/auth/login` · `/refresh` · `/logout` | |
| GET/PATCH | `/api/users/me` | profile (display name, bio, avatarUrl, birthday) |
| POST | `/api/users/me/password` | |
| GET | `/api/users/{username}` | same college only |
| POST | `/api/media/presign` `{purpose: MEME|AVATAR, contentType, contentLength}` | |
| GET/POST | `/api/posts?sort=new|top&author=&cursor=` | feed / create |
| GET/DELETE | `/api/posts/{id}` | |
| PUT/DELETE | `/api/posts/{id}/like` | |
| GET/POST | `/api/posts/{id}/comments` · DELETE `/api/comments/{id}` | |
| GET | `/api/leaderboard?week=current|previous` | |
| GET | `/api/wallet` | balance, stats, history |
| GET | `/api/goodies` · POST `/api/goodies/{id}/claim` · GET `/api/goodies/claims` | |
| GET | `/api/ads?count=` · POST `/api/ads/{id}/impression|click` | |
| GET | `/api/chat/ws-ticket` · `/api/chat/messages?before=` · `/api/chat/gifs?q=` · `/api/chat/online` | |
| WS | `wss://<chat>/ws` → CONNECT `ticket:` → SUB `/topic/college.{id}` → SEND `/app/chat.send` | |
| * | `/api/admin/claims`, `/api/admin/ads`, `/api/admin/leaderboard/payout` | ADMIN only |

Errors are always RFC 9457 `ProblemDetail` JSON: `{ "status", "title", "detail", "errors"? }`.

---

## 9. Project layout

```
loleague/
├── .env / .env.example        # all secrets & config (AWS keys go here)
├── docker-compose.yml         # local full stack
├── render.yaml                # one-click Render deploy
├── backend/                   # Maven multi-module (Java 25, Spring Boot 4.1)
│   ├── common/                # JWT-cookie resolver, CurrentUser, ProblemDetail errors
│   ├── auth-service/          # users, colleges.json, sessions
│   ├── meme-service/          # memes, leaderboard, coins, goodies, ads, S3
│   └── chat-service/          # STOMP chat rooms, GIF proxy, presence
└── frontend/                  # Next.js 16 + shadcn/ui + Redux Toolkit
    └── src/
        ├── app/               # routes: (auth)/login, (auth)/register, (app)/feed, post, leaderboard, chat, rewards, profile, u
        ├── app/api/[...path]  # BFF proxy to the services
        ├── proxy.ts           # route guard (Next 16's renamed middleware)
        ├── components/ui      # shadcn components (themed)
        ├── components/*       # feature components
        ├── store/             # Redux store, slices, RTK Query APIs
        └── lib/               # types, upload, formatting
```

Not included (by request): reporting/moderation system and direct messages.
