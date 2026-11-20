import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { ChatMessage } from "@/lib/types";

export type ConnectionStatus = "idle" | "connecting" | "connected" | "reconnecting" | "error";

interface ChatState {
  messages: ChatMessage[]; // oldest -> newest
  status: ConnectionStatus;
  online: number;
  olderCursor: string | null;
  historyLoaded: boolean;
}

const initialState: ChatState = {
  messages: [],
  status: "idle",
  online: 0,
  olderCursor: null,
  historyLoaded: false,
};

const MAX_IN_MEMORY = 600;

const chatSlice = createSlice({
  name: "chat",
  initialState,
  reducers: {
    statusChanged(state, action: PayloadAction<ConnectionStatus>) {
      state.status = action.payload;
    },
    onlineChanged(state, action: PayloadAction<number>) {
      state.online = action.payload;
    },
    /** First page of history (server returns newest first). */
    historyLoaded(state, action: PayloadAction<{ items: ChatMessage[]; nextBefore: string | null }>) {
      const incoming = [...action.payload.items].reverse();
      const known = new Set(incoming.map((m) => m.id));
      // keep any live messages that arrived while history was loading
      state.messages = [...incoming, ...state.messages.filter((m) => !known.has(m.id))];
      state.olderCursor = action.payload.nextBefore;
      state.historyLoaded = true;
    },
    olderLoaded(state, action: PayloadAction<{ items: ChatMessage[]; nextBefore: string | null }>) {
      const known = new Set(state.messages.map((m) => m.id));
      const older = [...action.payload.items].reverse().filter((m) => !known.has(m.id));
      state.messages = [...older, ...state.messages];
      state.olderCursor = action.payload.nextBefore;
    },
    messageReceived(state, action: PayloadAction<ChatMessage>) {
      if (state.messages.some((m) => m.id === action.payload.id)) return;
      state.messages.push(action.payload);
      if (state.messages.length > MAX_IN_MEMORY) {
        state.messages.splice(0, state.messages.length - MAX_IN_MEMORY);
        state.olderCursor = state.messages[0]?.id ?? null;
      }
    },
    chatReset() {
      return initialState;
    },
  },
});

export const { statusChanged, onlineChanged, historyLoaded, olderLoaded, messageReceived, chatReset } =
  chatSlice.actions;
export default chatSlice.reducer;
