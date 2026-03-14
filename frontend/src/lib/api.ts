import { getOn401 } from "./authCallbacks";

export const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
export const WS_URL =
  process.env.NEXT_PUBLIC_WS_URL || API_URL.replace(/\/$/, "") + "/ws";

export function getApiUrl(path: string): string {
  return `${API_URL.replace(/\/$/, "")}/${path.replace(/^\//, "")}`;
}

/** Call after fetch(); redirects to login on 401 and throws. */
export function checkAuthResponse(res: Response): void {
  if (res.status === 401) {
    getOn401()?.();
    throw new Error("Unauthorized");
  }
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit & { token?: string | null } = {}
): Promise<T> {
  const { token, ...init } = options;
  const url = getApiUrl(path);
  const headers: HeadersInit = {
    "Content-Type": "application/json",
    ...(init.headers as Record<string, string>),
  };
  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(url, { ...init, headers });
  if (!res.ok) {
    if (res.status === 401) {
      getOn401()?.();
      throw new Error("Unauthorized");
    }
    if (res.status === 403) {
      throw new Error("You don't have access to this resource.");
    }
    if (res.status === 404) {
      throw new Error(
        "Not found (404). Is the backend running? Check NEXT_PUBLIC_API_URL (e.g. http://localhost:8080)."
      );
    }
    if (res.status >= 500) {
      throw new Error("Server error. Please try again later.");
    }
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error((err as { error?: string }).error || `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export type ChatRoom = {
  id: string;
  name: string;
  description: string;
  classification?: string;
  allowedDepartments?: string;
  connectedUsers?: { username: string }[];
  numberOfConnectedUsers?: number;
};

export type FileTransferRequest = {
  id: string;
  requester: string;
  recipient: string;
  roomId: string;
  fileRef?: string;
  contentText?: string;
  status: string;
  requestedAt: string;
  decidedAt?: string;
  approver?: string;
};

export type RiskyUserEntry = { username: string; denyCount: number };
export type RiskyRoomEntry = { roomId: string; denyCount: number };
export type AlertStatus = { approvalBacklog: number; approvalBacklogAlert: boolean };

export type UploadResponse = {
  fileId: string | null;
  filename: string | null;
  sizeBytes: number;
  dlpWarning: boolean;
  dlpRequireApproval: boolean;
};

/** Upload file via POST /api/files/upload (multipart). Uses Bearer token. */
export async function uploadFile(
  file: File,
  token: string | null
): Promise<UploadResponse> {
  const url = getApiUrl("/api/files/upload");
  const form = new FormData();
  form.append("file", file);
  const headers: HeadersInit = {};
  if (token) {
    (headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(url, { method: "POST", headers, body: form });
  if (!res.ok) {
    if (res.status === 401) {
      getOn401()?.();
      throw new Error("Unauthorized");
    }
    if (res.status === 403) {
      const err = await res.json().catch(() => ({}));
      throw new Error(
        (err as { message?: string }).message ||
          "Upload blocked or you don't have access."
      );
    }
    if (res.status >= 500) {
      throw new Error("Server error. Please try again later.");
    }
    const err = await res.json().catch(() => ({ message: res.statusText }));
    throw new Error((err as { message?: string }).message || "Upload failed");
  }
  return res.json() as Promise<UploadResponse>;
}
