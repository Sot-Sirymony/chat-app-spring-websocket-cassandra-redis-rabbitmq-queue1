"use client";

import { useEffect, useState } from "react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { useAuth } from "@/contexts/AuthContext";
import {
  apiFetch,
  getApiUrl,
  checkAuthResponse,
  type FileTransferRequest,
} from "@/lib/api";

export default function ApprovalsPage() {
  const { token, isAdmin } = useAuth();
  const [pending, setPending] = useState<FileTransferRequest[]>([]);
  const [myRequests, setMyRequests] = useState<FileTransferRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  function load() {
    if (!token) return;
    setLoading(true);
    setError("");
    const base = { token };
    Promise.all([
      isAdmin
        ? apiFetch<FileTransferRequest[]>("/api/file-requests/pending", base)
        : Promise.resolve([]),
      apiFetch<FileTransferRequest[]>("/api/file-requests/my", base),
    ])
      .then(([p, m]) => {
        setPending(p);
        setMyRequests(m);
      })
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load")
      )
      .finally(() => setLoading(false));
  }

  useEffect(() => {
    load();
  }, [token, isAdmin]);

  async function approve(id: string) {
    if (!token) return;
    setError("");
    setActionLoading(id);
    try {
      const url = getApiUrl(`/api/file-requests/${id}/approve`);
      const res = await fetch(url, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      checkAuthResponse(res);
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Approve failed (${res.status})`);
      }
      setError("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Approve failed");
    } finally {
      setActionLoading(null);
    }
  }

  async function reject(id: string) {
    if (!token) return;
    setError("");
    setActionLoading(id);
    try {
      const url = getApiUrl(`/api/file-requests/${id}/reject`);
      const res = await fetch(url, {
        method: "POST",
        headers: { Authorization: `Bearer ${token}` },
      });
      checkAuthResponse(res);
      if (!res.ok) {
        const text = await res.text();
        throw new Error(text || `Reject failed (${res.status})`);
      }
      setError("");
      load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Reject failed");
    } finally {
      setActionLoading(null);
    }
  }

  function formatDate(s: string | undefined) {
    if (!s) return "-";
    try {
      return new Date(s).toLocaleString();
    } catch {
      return s;
    }
  }

  return (
    <ProtectedLayout>
      <div className="max-w-4xl mx-auto space-y-6">
        <h1 className="text-xl font-bold">Approvals</h1>
        {error && (
          <div className="text-red-600 bg-red-50 p-2 rounded text-sm">
            {error}
          </div>
        )}

        {loading ? (
          <p className="text-gray-500">Loading...</p>
        ) : (
          <>
            {isAdmin && (
              <section>
                <h2 className="text-lg font-semibold mb-2">
                  Pending requests (admin)
                </h2>
                <div className="border border-gray-200 rounded overflow-hidden">
                  {pending.length === 0 ? (
                    <p className="px-3 py-4 text-gray-500 text-sm">
                      No pending requests.
                    </p>
                  ) : (
                    <table className="w-full text-left text-sm">
                      <thead className="bg-gray-100">
                        <tr>
                          <th className="px-3 py-2">Requester</th>
                          <th className="px-3 py-2">Recipient</th>
                          <th className="px-3 py-2">Room</th>
                          <th className="px-3 py-2">Content (preview)</th>
                          <th className="px-3 py-2">Requested</th>
                          <th className="px-3 py-2">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pending.map((r) => (
                          <tr key={r.id} className="border-t border-gray-100">
                            <td className="px-3 py-2">{r.requester}</td>
                            <td className="px-3 py-2">{r.recipient ?? "-"}</td>
                            <td className="px-3 py-2">{r.roomId ?? "-"}</td>
                            <td className="px-3 py-2 max-w-[200px] truncate" title={r.contentText ?? ""}>
                              {r.contentText ? (r.contentText.length > 50 ? r.contentText.slice(0, 50) + "…" : r.contentText) : "-"}
                            </td>
                            <td className="px-3 py-2">
                              {formatDate(r.requestedAt)}
                            </td>
                            <td className="px-3 py-2 flex gap-2">
                              <button
                                type="button"
                                onClick={() => approve(r.id)}
                                disabled={actionLoading === r.id}
                                className="text-green-600 hover:underline disabled:opacity-50"
                              >
                                Approve
                              </button>
                              <button
                                type="button"
                                onClick={() => reject(r.id)}
                                disabled={actionLoading === r.id}
                                className="text-red-600 hover:underline disabled:opacity-50"
                              >
                                Reject
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              </section>
            )}

            <section>
              <h2 className="text-lg font-semibold mb-2">My requests</h2>
              <div className="border border-gray-200 rounded overflow-hidden">
                {myRequests.length === 0 ? (
                  <p className="px-3 py-4 text-gray-500 text-sm">
                    You have no file transfer requests.
                  </p>
                ) : (
                  <table className="w-full text-left text-sm">
                    <thead className="bg-gray-100">
                      <tr>
                        <th className="px-3 py-2">Recipient</th>
                        <th className="px-3 py-2">Room</th>
                        <th className="px-3 py-2">Status</th>
                        <th className="px-3 py-2">Requested</th>
                        <th className="px-3 py-2">Decided</th>
                      </tr>
                    </thead>
                    <tbody>
                      {myRequests.map((r) => (
                        <tr key={r.id} className="border-t border-gray-100">
                          <td className="px-3 py-2">{r.recipient}</td>
                          <td className="px-3 py-2">{r.roomId ?? "-"}</td>
                          <td className="px-3 py-2">
                            <span
                              className={
                                r.status === "APPROVED"
                                  ? "text-green-600"
                                  : r.status === "REJECTED"
                                    ? "text-red-600"
                                    : "text-gray-600"
                              }
                            >
                              {r.status}
                            </span>
                          </td>
                          <td className="px-3 py-2">
                            {formatDate(r.requestedAt)}
                          </td>
                          <td className="px-3 py-2">
                            {formatDate(r.decidedAt)}
                            {r.approver && ` by ${r.approver}`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </section>
          </>
        )}
      </div>
    </ProtectedLayout>
  );
}
