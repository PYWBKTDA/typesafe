// src/api.ts

const API_BASE = ""; // ⚠️ 如果前端代理了 /user /course，不需要 "/api"

function getToken(): string | null {
  return localStorage.getItem("token");
}

function getHeaders(customHeaders: HeadersInit = {}): HeadersInit {
  const token = getToken();
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...customHeaders,
  };
}

async function handleResponse<T>(res: Response, url: string): Promise<T> {
  const text = await res.text();
  if (!res.ok) {
    let message = `请求失败: ${res.status} ${text}`;
    try {
      const json = JSON.parse(text);
      message = json.message || message;
    } catch {}
    throw new Error(message);
  }
  return text ? JSON.parse(text) : ({} as T);
}

export async function apiGet<T>(url: string): Promise<T> {
  const res = await fetch(`${API_BASE}${url}`, {
    method: "GET",
    headers: getHeaders(),
  });
  return handleResponse<T>(res, url);
}

export async function apiPost<T>(url: string, body: any): Promise<T> {
  const res = await fetch(`${API_BASE}${url}`, {
    method: "POST",
    headers: getHeaders(),
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res, url);
}

// ✅ 可选扩展：
export async function apiPut<T>(url: string, body: any): Promise<T> {
  const res = await fetch(`${API_BASE}${url}`, {
    method: "PUT",
    headers: getHeaders(),
    body: JSON.stringify(body),
  });
  return handleResponse<T>(res, url);
}

export async function apiDelete<T>(url: string): Promise<T> {
  const res = await fetch(`${API_BASE}${url}`, {
    method: "DELETE",
    headers: getHeaders(),
  });
  return handleResponse<T>(res, url);
}
