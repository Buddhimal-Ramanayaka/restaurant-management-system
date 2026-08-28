import { useEffect, useRef, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useAuth } from "../context/AuthContext";

const WS_URL = import.meta.env.VITE_WS_URL || "http://localhost:8080/ws";

/**
 * One STOMP connection per mounted screen (Kitchen Display, POS for order-ready
 * pushes, Manager dashboard for stock alerts). Reconnection is automatic via
 * the stompjs client built-in reconnectDelay - a kitchen tablet that drops
 * wifi mid-service resubscribes to every topic it had open without the
 * component needing to do anything.
 *
 * subscriptions: array of { destination, onMessage } pairs subscribed to as
 * soon as the connection (re)establishes.
 */
export function useStompClient(subscriptions) {
  const { token } = useAuth();
  const clientRef = useRef(null);
  const [connected, setConnected] = useState(false);

  const publish = useCallback((destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({ destination, body: JSON.stringify(body) });
    }
  }, []);

  useEffect(() => {
    if (!token) return undefined;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 4000,
      onConnect: () => {
        setConnected(true);
        subscriptions.forEach(({ destination, onMessage }) => {
          client.subscribe(destination, (message) => {
            try {
              onMessage(JSON.parse(message.body));
            } catch (e) {
              onMessage(message.body);
            }
          });
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: (frame) => {
        // eslint-disable-next-line no-console
        console.error("STOMP broker error", frame.headers["message"]);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return { connected, publish };
}
