"use client";

import { useState } from "react";
import { FeedList } from "@/components/feed/feed-list";
import type { FeedSort } from "@/store/api/memeApi";

export default function FeedPage() {
  const [sort, setSort] = useState<FeedSort>("new");
  return <FeedList sort={sort} onSortChange={setSort} />;
}
