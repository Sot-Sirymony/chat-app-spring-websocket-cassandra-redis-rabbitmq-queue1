 "use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import SockJS from "sockjs-client";
import { Client, IMessage } from "@stomp/stompjs";

import ProtectedLayout from "@/components/ProtectedLayout";
import { useAuth } from "@/contexts/AuthContext";
import {
  apiFetch,
  getApiUrl,
  uploadFile,
  checkAuthResponse,
  type ChatRoom,
  WS_URL,
} from "@/lib/api";

type ChatUser = { username: string };

type InstantMessage = {
  fromUser: string;
  toUser?: string | null;
  text?: string | null;
  public?: boolean;
  dlpWarning?: string | null;
};

type AttachedFile = {
  fileId: string;
  filename: string;
  sizeBytes: number;
  dlpWarning?: boolean;
  dlpRequireApproval?: boolean;
};

export default function ChatRoomPage({ params }: { params: { id: string } }) {
  const chatRoomId = params.id;
  const { token, username } = useAuth();
  const router = useRouter();

  const [room, setRoom] = useState<ChatRoom | null>(null);
  const [loadingRoom, setLoadingRoom] = useState(true);
  const [error, setError] = useState("");

  const [connectedUsers, setConnectedUsers] = useState<ChatUser[]>([]);
  const [messages, setMessages] = useState<InstantMessage[]>([]);
  const [sendTo, setSendTo] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [status, setStatus] = useState<string | null>("Connecting…");
  const [attachedFile, setAttachedFile] = useState<AttachedFile | null>(null);
  const [uploading, setUploading] = useState(false);

  const clientRef = useRef<Client | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!token) return;
    setLoadingRoom(true);
    apiFetch<ChatRoom>(`/api/chatrooms/${chatRoomId}`, { token })
      .then((r) => setRoom(r))
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load room")
      )
      .finally(() => setLoadingRoom(false));
  }, [chatRoomId, token]);

  useEffect(() => {
    if (!token) return;

    const headers: Record<string, string> = { chatRoomId };
    if (typeof navigator !== "undefined" && navigator.userAgent) {
      headers["device-type"] = navigator.userAgent;
    }
    headers["Authorization"] = `Bearer ${token}`;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      connectHeaders: headers,
      reconnectDelay: 10000,
      debug: () => {
        // no-op
      },
    });

    client.onConnect = () => {
      setStatus("Connected");

      client.subscribe("/chatroom/connected.users", (message: IMessage) => {
        const users = JSON.parse(message.body) as ChatUser[];
        setConnectedUsers(users);
      });

      client.subscribe("/chatroom/old.messages", (message: IMessage) => {
        const list = JSON.parse(message.body) as InstantMessage[];
        setMessages((prev) => [...prev, ...list]);
      });

      client.subscribe(
        `/topic/${chatRoomId}.public.messages`,
        (message: IMessage) => {
          const m = JSON.parse(message.body) as InstantMessage;
          setMessages((prev) => [...prev, m]);
        }
      );

      client.subscribe(
        `/user/queue/${chatRoomId}.private.messages`,
        (message: IMessage) => {
          const m = JSON.parse(message.body) as InstantMessage;
          setMessages((prev) => [...prev, m]);
        }
      );

      client.subscribe(
        `/topic/${chatRoomId}.connected.users`,
        (message: IMessage) => {
          const users = JSON.parse(message.body) as ChatUser[];
          setConnectedUsers(users);
        }
      );

      client.subscribe("/user/queue/policy-denial", (message: IMessage) => {
        const body = JSON.parse(message.body) as { reason?: string };
        setStatus(body.reason || "Message blocked by policy.");
      });

      client.subscribe("/user/queue/recipient-warning", (message: IMessage) => {
        const body = JSON.parse(message.body) as { reason?: string };
        setStatus(body.reason || "Recipient may not be in this room.");
      });
    };

    client.onStompError = () => {
      setStatus("WebSocket error. Reconnecting…");
    };

    client.onWebSocketClose = () => {
      setStatus("Disconnected. Reconnecting…");
    };

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [chatRoomId, token]);

  function handleSend(e: React.FormEvent) {
    e.preventDefault();
    if (!clientRef.current || !clientRef.current.connected) return;
    if (!text.trim() && !attachedFile) return;

    const isPublic = !sendTo;
    const payload: Record<string, unknown> = { text: text || "" };
    if (!isPublic) payload.toUser = sendTo;
    if (attachedFile) {
      payload.fileRef = attachedFile.fileId;
      // Only link file to approval request when attachment required approval at upload (WARN files stay downloadable)
      payload.attachmentRequiresApproval = attachedFile.dlpRequireApproval === true;
      setAttachedFile(null);
    }

    clientRef.current.publish({
      destination: "/chatroom/send.message",
      body: JSON.stringify(payload),
    });

    setText("");
  }

  function onFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !token) return;
    e.target.value = "";
    setUploading(true);
    setStatus(null);
    uploadFile(file, token)
      .then((r) => {
        if (!r.fileId) {
          setStatus("Upload returned no file id.");
          return;
        }
        setAttachedFile({
          fileId: r.fileId,
          filename: r.filename || file.name,
          sizeBytes: r.sizeBytes,
          dlpWarning: r.dlpWarning,
          dlpRequireApproval: r.dlpRequireApproval,
        });
      })
      .catch((err) =>
        setStatus(err instanceof Error ? err.message : "Upload failed")
      )
      .finally(() => setUploading(false));
  }

  function handleDownload(fileId: string, token: string | null) {
    if (!token) {
      setStatus("Please log in to download. If you're already logged in, try refreshing the page or logging in again.");
      return;
    }
    setStatus("");
    const url = getApiUrl(`/api/files/${fileId}/download`);
    fetch(url, {
      method: "GET",
      headers: { Authorization: `Bearer ${token}` },
      mode: "cors",
      credentials: "include",
    })
      .then(async (r) => {
        const denyReason = r.headers.get("X-File-Deny-Reason");
<<<<<<< HEAD
        if (r.status === 403 && denyReason === "FILE_PENDING_APPROVAL") {
          throw new Error("FILE_PENDING_APPROVAL");
        }
        if (r.status === 403 && denyReason === "FORBIDDEN_ROLE") {
          throw new Error("FORBIDDEN_ROLE");
=======
        if (r.status === 403) {
          if (denyReason === "FILE_PENDING_APPROVAL") throw new Error("FILE_PENDING_APPROVAL");
          if (denyReason === "FORBIDDEN_ROLE") throw new Error("FORBIDDEN_ROLE");
          if (denyReason === "FILE_NOT_AUTHORIZED") throw new Error("FILE_NOT_AUTHORIZED");
          throw new Error("DOWNLOAD_FORBIDDEN");
>>>>>>> integrate-ivw
        }
        if (r.status === 401) {
          checkAuthResponse(r);
          throw new Error("Unauthorized (401). Try logging in again.");
        }
        if (!r.ok) {
          const text = await r.text();
          throw new Error(`Download failed (${r.status}). ${text || r.statusText}`);
        }
        return r.blob();
      })
      .then((blob) => {
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = "attachment";
        a.click();
        URL.revokeObjectURL(a.href);
        setStatus("");
      })
      .catch((err: Error) => {
        const m = err?.message ?? String(err);
        const msg =
          m === "FILE_PENDING_APPROVAL"
            ? "File not yet approved."
              : m === "FORBIDDEN_ROLE"
              ? "Download not allowed for your account. Try logging out and logging in again, then try the download again."
              : m === "FILE_NOT_AUTHORIZED"
                ? "This file was rejected or you're not authorized to download it."
                : m === "DOWNLOAD_FORBIDDEN"
                  ? "Download not allowed. File may be pending, rejected, or you need to log in again."
                  : m.startsWith("Download failed (403)")
                    ? "Download not allowed. File may be pending, rejected, or try logging out and in again."
                    : m.startsWith("Download failed")
                      ? m
                      : m.startsWith("Unauthorized")
                        ? m
                        : m === "Failed to fetch" || m.includes("NetworkError")
                          ? `Cannot reach server. Start backend: ./run-dev.sh or cd ebook-chat && mvn spring-boot:run. Use same host for app and API (e.g. both localhost or both 127.0.0.1). Check: ${getApiUrl("")}`
                          : `Download failed: ${m}`;
        setStatus(msg);
      });
  }

  /** Render message text with download links as clickable buttons. */
  function renderMessageBody(body: string) {
    const linkRegex = /\[Download attachment\]\(\/api\/files\/([^)]+)\/download\)/gi;
    const parts: React.ReactNode[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    while ((match = linkRegex.exec(body)) !== null) {
      if (match.index > lastIndex) {
        parts.push(body.slice(lastIndex, match.index));
      }
      const fileId = match[1];
      parts.push(
        <button
          key={match.index}
          type="button"
          onClick={() => handleDownload(fileId, token)}
          className="text-blue-600 hover:underline ml-1"
        >
          [Download attachment]
        </button>
      );
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < body.length) parts.push(body.slice(lastIndex));
    return parts.length === 1 && typeof parts[0] === "string"
      ? parts[0]
      : parts;
  }

  function renderMessage(m: InstantMessage, index: number) {
    const isPrivate = !!m.toUser;
    const from = m.fromUser || "";
    const to = m.toUser || "";
    const body = m.text || "";

    if (isPrivate) {
      return (
        <div
          key={index}
          className="bg-blue-50 border border-blue-100 rounded px-3 py-1 text-sm"
        >
          <span className="font-semibold">
            [private] {from} → {to}:
          </span>{" "}
          <span>{renderMessageBody(body)}</span>
          {m.dlpWarning && (
            <div className="text-xs text-blue-700 mt-1">
              &#9888; {m.dlpWarning}
            </div>
          )}
        </div>
      );
    }

    return (
      <div key={index} className="text-sm">
        <span className="font-semibold">{from}:</span>{" "}
        <span>{renderMessageBody(body)}</span>
        {m.dlpWarning && (
          <div className="text-xs text-blue-700 mt-1">
            &#9888; {m.dlpWarning}
          </div>
        )}
      </div>
    );
  }

  return (
    <ProtectedLayout>
      <div className="max-w-5xl mx-auto space-y-4">
        <div className="flex items-center justify-between gap-2">
          <div>
            <h1 className="text-xl font-bold">
              Chat room {room ? `“${room.name}”` : ""}
            </h1>
            {room?.classification && (
              <p className="text-xs text-gray-500">
                Classification: {room.classification}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={() => router.push("/chat")}
            className="text-sm text-blue-600 hover:underline"
          >
            Back to rooms
          </button>
        </div>

        {status && (
          <div className="text-xs text-gray-600 bg-gray-50 border border-gray-200 px-2 py-1 rounded">
            {status}
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="md:col-span-1 border border-gray-200 rounded">
            <div className="border-b border-gray-200 px-3 py-2 font-medium text-sm">
              Users
            </div>
            <div className="max-h-80 overflow-y-auto px-3 py-2 space-y-1 text-sm">
              {connectedUsers.map((u) => (
                <button
                  key={u.username}
                  type="button"
                  onClick={() =>
                    setSendTo((prev) =>
                      prev === u.username ? null : u.username
                    )
                  }
                  className={`block w-full text-left px-2 py-1 rounded ${
                    sendTo === u.username
                      ? "bg-blue-600 text-white"
                      : "hover:bg-gray-100"
                  }`}
                  disabled={u.username === username}
                >
                  {u.username}
                </button>
              ))}
            </div>
            <div className="border-t border-gray-200 px-3 py-2">
              <button
                type="button"
                onClick={() => setSendTo(null)}
                className="w-full bg-green-600 text-white text-xs py-1 rounded"
              >
                Public messages
              </button>
            </div>
          </div>

          <div className="md:col-span-3 flex flex-col border border-gray-200 rounded">
            <div className="flex-1 max-h-96 overflow-y-auto px-3 py-2 space-y-2 bg-white">
              {messages.map((m, idx) => renderMessage(m, idx))}
            </div>
            <form
              onSubmit={handleSend}
              className="border-t border-gray-200 px-3 py-2 flex flex-col gap-2"
            >
              <div className="flex items-center gap-2 text-xs text-gray-600">
                <span className="px-2 py-1 bg-gray-100 rounded">
                  {sendTo ? `private to ${sendTo}` : "public"}
                </span>
              </div>
              {attachedFile && (
                <div className="flex items-center gap-2 text-xs text-gray-700 bg-gray-50 px-2 py-1 rounded">
                  <span title={attachedFile.filename}>
                    &#128206; {attachedFile.filename}
                    {attachedFile.sizeBytes > 0 &&
                      ` (${attachedFile.sizeBytes < 1024 ? attachedFile.sizeBytes + " B" : (attachedFile.sizeBytes / 1024).toFixed(1) + " KB"})`}
                  </span>
                  {attachedFile.dlpWarning && (
                    <span className="text-amber-600">&#9888; DLP warning</span>
                  )}
                  {attachedFile.dlpRequireApproval && (
                    <span className="text-amber-600">
                      &#9888; Requires approval
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={() => setAttachedFile(null)}
                    className="text-red-600 hover:underline ml-1"
                  >
                    Remove
                  </button>
                </div>
              )}
              <div className="flex gap-2">
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  onChange={onFileSelect}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={uploading}
                  className="px-2 py-1 border border-gray-300 rounded text-sm hover:bg-gray-50 disabled:opacity-50"
                  title="Attach file"
                >
                  {uploading ? "Uploading…" : "&#128206; Attach"}
                </button>
                <input
                  type="text"
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                  placeholder="Type your message…"
                />
                <button
                  type="submit"
                  disabled={(!text.trim() && !attachedFile) || uploading}
                  className="bg-blue-600 text-white px-3 py-1 rounded text-sm disabled:opacity-50"
                >
                  Send
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </ProtectedLayout>
  );
}

