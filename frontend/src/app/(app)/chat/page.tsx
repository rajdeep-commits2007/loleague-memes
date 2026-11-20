import type { Metadata } from "next";
import { ChatRoom } from "@/components/chat/chat-room";

export const metadata: Metadata = { title: "Adda" };

export default function ChatPage() {
  return <ChatRoom />;
}
