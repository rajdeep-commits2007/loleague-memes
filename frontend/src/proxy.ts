import { clerkMiddleware } from "@clerk/nextjs/server";
import { NextResponse, type NextRequest } from "next/server";

/**
 * Route guard. The real auth check happens in the services (HttpOnly JWT cookie); here we only read
 * the non-secret "ll_session" hint cookie to avoid flashing protected pages to logged-out visitors.
 */
const PROTECTED = ["/feed", "/post", "/leaderboard", "/chat", "/rewards", "/profile", "/u"];
const GUEST_ONLY = ["/login", "/register", "/welcome"];

function guard(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const hasSession = request.cookies.get("ll_session")?.value === "1";

  if (!hasSession && PROTECTED.some((p) => pathname === p || pathname.startsWith(`${p}/`))) {
    const url = new URL("/login", request.url);
    url.searchParams.set("next", pathname + search);
    return NextResponse.redirect(url);
  }
  if (hasSession && (GUEST_ONLY.includes(pathname) || pathname === "/")) {
    return NextResponse.redirect(new URL("/feed", request.url));
  }
  return NextResponse.next();
}

/** Clerk's middleware runs first (it keeps the Clerk session fresh); our cookie hint decides the redirects. */
export const proxy = clerkMiddleware((_auth, request) => guard(request));

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|icon.svg|robots.txt).*)"],
};
