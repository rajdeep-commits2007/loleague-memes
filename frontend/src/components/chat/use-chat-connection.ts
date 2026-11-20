"use client";

import { useCallback, useEffect, useRef } from "react";
import { Client, type IMessage } from "@stomp/stompjs";
import { toast } from "sonner";

import { chatApi } from "@/store/api/chatApi";
import { useAppDispatch } from "@/store/hooks";
import { chatReset, historyLoaded, messageReceived, onlineChanged, statusChanged } from "@/store/slices/chatSlice";
import type { ChatMessage, Gif, WsTicket } from "@/lib/types";

/**
 * Owns the STOMP connection for the college room.
 * Every (re)connect first fetches a fresh 60-second ticket over HTTPS (cookie-authenticated),
 * because the HttpOnly cookie can't be sent to the chat service's WebSocket origin.
 */
export function useChatConnection() {
  const dispatch = useAppDispatch();
  const clientRef = useRef<Client | null>(null);
  const roomRef = useRef<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let connectedOnce = false;

    // history first, so the room isn't empty while the socket connects
    dispatch(chatApi.endpoints.getChatHistory.initiate({}))
      .unwrap()
      .then((page) => !cancelled && dispatch(historyLoaded(page)))
      .catch(() => toast.error("Couldn't load chat history"));

    const fetchTicket = () => dispatch(chatApi.endpoints.getWsTicket.initiate()).unwrap() as Promise<WsTicket>;

    const client = new Client({
      reconnectDelay: 3000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // Called before every connect attempt, including automatic reconnects.
      beforeConnect: async (c) => {
        dispatch(statusChanged(connectedOnce ? "reconnecting" : "connecting"));
        const ticket = await fetchTicket();
        c.brokerURL = ticket.wsUrl;
        c.connectHeaders = { ticket: ticket.ticket };
        roomRef.current = ticket.room;
      },
      onConnect: () => {
        const room = roomRef.current;
        if (!room) return;
        connectedOnce = true;
        dispatch(statusChanged("connected"));
        client.subscribe(room, (frame: IMessage) => {
          dispatch(messageReceived(JSON.parse(frame.body) as ChatMessage));
        });
        client.subscribe(`${room}.presence`, (frame: IMessage) => {
          dispatch(onlineChanged((JSON.parse(frame.body) as { online: number }).online));
        });
        client.subscribe("/user/queue/errors", (frame: IMessage) => {
          toast.error((JSON.parse(frame.body) as { error: string }).error);
        });
        dispatch(chatApi.endpoints.getOnline.initiate())
          .unwrap()
          .then((r) => dispatch(onlineChanged(r.online)))
          .catch(() => undefined);
      },
      onWebSocketClose: () => {
        if (!cancelled) dispatch(statusChanged("reconnecting"));
      },
      onStompError: (frame) => {
        dispatch(statusChanged("error"));
        console.warn("STOMP error", frame.headers["message"]);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      cancelled = true;
      clientRef.current = null;
      void client.deactivate();
      dispatch(chatReset());
    };
  }, [dispatch]);

  const sendText = useCallback((content: string) => {
    const client = clientRef.current;
    if (!client?.connected) {
      toast.error("Still connecting to the adda...");
      return false;
    }
    client.publish({ destination: "/app/chat.send", body: JSON.stringify({ kind: "TEXT", content }) });
    return true;
  }, []);

  const sendGif = useCallback((gif: Gif) => {
    const client = clientRef.current;
    if (!client?.connected) {
      toast.error("Still connecting to the adda...");
      return false;
    }
    client.publish({
      destination: "/app/chat.send",
      body: JSON.stringify({ kind: "GIF", content: gif.url, gifWidth: gif.width, gifHeight: gif.height }),
    });
    return true;
  }, []);

  return { sendText, sendGif };
}
