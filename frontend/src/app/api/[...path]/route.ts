import type { NextRequest } from "next/server";

/**
 * Backend-for-frontend proxy. The browser only ever talks to this origin, so the auth cookies set by
 * auth-service are first-party, HttpOnly and SameSite=Lax. Each prefix maps to one Spring Boot service.
 */
export const dynamic = "force-dynamic";

function withScheme(url: string | undefined, fallback: string) {
  const u = (url && url.trim()) || fallback;
  return /^https?:\/\//.test(u) ? u.replace(/\/$/, "") : `http://${u}`;
}

const AUTH = withScheme(process.env.AUTH_SERVICE_URL, "http://localhost:8081");
const MEME = withScheme(process.env.MEME_SERVICE_URL, "http://localhost:8082");
const CHAT = withScheme(process.env.CHAT_SERVICE_URL, "http://localhost:8083");

const ROUTES: Array<[prefix: string, target: string]> = [
  ["auth", AUTH],
  ["users", AUTH],
  ["posts", MEME],
  ["comments", MEME],
  ["media", MEME],
  ["leaderboard", MEME],
  ["wallet", MEME],
  ["goodies", MEME],
  ["ads", MEME],
  ["admin", MEME],
  ["chat", CHAT],
];

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "transfer-encoding",
  "te",
  "trailer",
  "upgrade",
  "proxy-authorization",
  "proxy-authenticate",
  "host",
  "content-length",
  "content-encoding",
]);

async function forward(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const target = ROUTES.find(([prefix]) => prefix === path[0])?.[1];
  if (!target) {
    return Response.json({ status: 404, title: "Not Found", detail: "Unknown API route" }, { status: 404 });
  }

  const url = `${target}/api/${path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`;
  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (!HOP_BY_HOP.has(key.toLowerCase())) headers.set(key, value);
  });
  const clientIp = req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ?? req.headers.get("x-real-ip") ?? "";
  if (clientIp) headers.set("x-forwarded-for", clientIp);
  headers.set("x-forwarded-proto", req.nextUrl.protocol.replace(":", ""));
  headers.set("x-forwarded-host", req.headers.get("host") ?? "");

  const hasBody = !["GET", "HEAD"].includes(req.method);
  let upstream: Response;
  try {
    upstream = await fetch(url, {
      method: req.method,
      headers,
      body: hasBody ? await req.arrayBuffer() : undefined,
      redirect: "manual",
      cache: "no-store",
      signal: AbortSignal.timeout(30_000),
    });
  } catch {
    return Response.json(
      { status: 503, title: "Service Unavailable", detail: "LOLeague is waking up. Try again in a few seconds." },
      { status: 503 }
    );
  }

  const out = new Headers();
  upstream.headers.forEach((value, key) => {
    const k = key.toLowerCase();
    if (!HOP_BY_HOP.has(k) && k !== "set-cookie") out.set(key, value);
  });
  // Preserve every Set-Cookie separately (access, refresh and session-hint cookies).
  for (const cookie of upstream.headers.getSetCookie()) out.append("set-cookie", cookie);

  return new Response(upstream.status === 204 || upstream.status === 304 ? null : upstream.body, {
    status: upstream.status,
    headers: out,
  });
}

export { forward as GET, forward as POST, forward as PUT, forward as PATCH, forward as DELETE };
