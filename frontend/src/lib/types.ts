// Shapes returned by the Spring Boot services.

export interface College {
  id: string;
  name: string;
  city: string;
}

export interface User {
  id: string;
  username: string;
  collegeId: string;
  collegeName: string;
  displayName: string;
  bio: string | null;
  avatarUrl: string | null;
  birthday: string | null; // yyyy-MM-dd
  role: "USER" | "ADMIN";
  /** Verified email. Locked: one account per email. Null on accounts created before email sign-up. */
  email: string | null;
  usernameChangedAt: string | null;
  createdAt: string;
}

/** Result of swapping a Clerk session for LOLeague cookies. */
export type SessionResult =
  | { status: "SIGNED_IN"; user: User; email: null; suggestedName: null }
  | { status: "ONBOARDING"; user: null; email: string; suggestedName: string | null }
  | { status: "EMAIL_REQUIRED"; user: null; email: null; suggestedName: null };

export interface PublicProfile {
  id: string;
  username: string;
  collegeId: string;
  collegeName: string;
  displayName: string;
  bio: string | null;
  avatarUrl: string | null;
  birthdayDay: number | null;
  birthdayMonth: number | null;
  createdAt: string;
}

export interface UsernameAvailability {
  username: string;
  available: boolean;
  reason: string | null;
}

export interface Author {
  id: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
}

export interface Post {
  id: string;
  caption: string | null;
  imageUrl: string;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  mine: boolean;
  createdAt: string;
  author: Author;
}

export interface CommentReaction {
  emoji: string;
  count: number;
  mine: boolean;
}

export interface Comment {
  id: string;
  postId: string;
  parentId: string | null;
  body: string;
  mine: boolean;
  createdAt: string;
  editedAt: string | null;
  replyCount: number;
  reactions: CommentReaction[];
  /** Usernames that were resolved as real @mentions (same college). */
  mentions: string[];
  author: Author;
}

export const COMMENT_REACTIONS = ["👍", "😂", "❤️", "🔥", "😮", "😢"] as const;

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Ad {
  id: string;
  title: string;
  body: string | null;
  imageUrl: string | null;
  ctaText: string;
  targetUrl: string;
}

export interface LeaderboardEntry {
  rank: number;
  userId: string;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  likes: number;
  comments: number;
  score: number;
  coins: number;
}

export interface Leaderboard {
  weekStart: string;
  weekEnd: string;
  finalized: boolean;
  rewards: number[];
  entries: LeaderboardEntry[];
  me: LeaderboardEntry | null;
}

export interface CoinTransaction {
  id: string;
  amount: number;
  kind: "LEADERBOARD_REWARD" | "GOODIE_CLAIM" | "GOODIE_REFUND" | "ADMIN_ADJUST";
  note: string | null;
  createdAt: string;
}

export interface Wallet {
  balance: number;
  lifetimeEarned: number;
  commentsReceived: number;
  likesReceived: number;
  transactions: CoinTransaction[];
}

export interface Goodie {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  coinCost: number;
  minCommentsReceived: number;
  stock: number | null;
  oncePerUser: boolean;
  canClaim: boolean;
  blockedReason: string | null;
}

export interface GoodieClaim {
  id: string;
  goodieId: string;
  goodieName: string;
  coinsSpent: number;
  status: "PENDING" | "SHIPPED" | "DELIVERED" | "CANCELLED";
  fullName: string;
  city: string;
  pincode: string;
  adminNote: string | null;
  createdAt: string;
}

export interface ClaimAddress {
  fullName: string;
  phone: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  pincode: string;
}

export interface PresignResponse {
  uploadUrl: string;
  key: string;
  publicUrl: string;
  expiresAt: string;
  headers: Record<string, string>;
}

export interface ChatMessage {
  id: string;
  collegeId: string;
  userId: string;
  username: string;
  kind: "TEXT" | "GIF";
  content: string;
  gifWidth: number | null;
  gifHeight: number | null;
  createdAt: string;
}

export interface Gif {
  id: string;
  title: string;
  url: string;
  previewUrl: string;
  width: number;
  height: number;
}

export interface WsTicket {
  ticket: string;
  wsUrl: string;
  room: string;
}
