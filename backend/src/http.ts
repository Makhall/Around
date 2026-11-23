import type { IncomingMessage, ServerResponse } from "node:http";

import { ApiError, fail } from "./errors";
import { auth } from "./firebase";

const MAX_BODY_BYTES = 1_000_000;

export function sendJson(
  response: ServerResponse,
  status: number,
  body: unknown,
): void {
  const payload = JSON.stringify(body);
  response.statusCode = status;
  response.setHeader("Content-Type", "application/json; charset=utf-8");
  response.setHeader("Cache-Control", "no-store");
  response.end(payload);
}

async function readBody(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    let body = "";
    request.on("data", (chunk: Buffer) => {
      body += chunk.toString("utf8");
      if (body.length > MAX_BODY_BYTES) {
        request.destroy();
        reject(new ApiError(413, "invalid_request", "Request body is too large"));
      }
    });
    request.on("end", () => resolve(body));
    request.on("error", reject);
  });
}

async function readJsonBody(request: IncomingMessage): Promise<unknown> {
  const preParsed = (request as IncomingMessage & { body?: unknown }).body;
  if (preParsed !== undefined && preParsed !== null) {
    if (typeof preParsed === "string") {
      try {
        return JSON.parse(preParsed);
      } catch {
        fail(400, "invalid_request", "Request body must be valid JSON");
      }
    }
    if (Buffer.isBuffer(preParsed)) {
      try {
        return JSON.parse(preParsed.toString("utf8"));
      } catch {
        fail(400, "invalid_request", "Request body must be valid JSON");
      }
    }
    return preParsed;
  }

  const raw = await readBody(request);
  if (raw.trim().length === 0) {
    return {};
  }
  try {
    return JSON.parse(raw);
  } catch {
    fail(400, "invalid_request", "Request body must be valid JSON");
  }
}

async function authenticate(request: IncomingMessage): Promise<string> {
  const header = request.headers.authorization ?? "";
  if (!header.startsWith("Bearer ")) {
    fail(401, "unauthenticated", "Sign in required");
  }

  const token = header.slice("Bearer ".length).trim();
  if (!token) {
    fail(401, "unauthenticated", "Sign in required");
  }

  try {
    const decoded = await auth.verifyIdToken(token);
    return decoded.uid;
  } catch {
    fail(401, "unauthenticated", "Invalid or expired sign-in");
  }
}

export async function handleJson(
  request: IncomingMessage,
  response: ServerResponse,
  handler: (
    uid: string,
    data: Record<string, unknown>,
  ) => Promise<Record<string, unknown>>,
): Promise<void> {
  if (request.method !== "POST") {
    sendJson(response, 405, {
      error: { code: "invalid_request", message: "Use POST" },
    });
    return;
  }

  try {
    const uid = await authenticate(request);
    const parsed = await readJsonBody(request);
    if (
      typeof parsed !== "object" ||
      parsed === null ||
      Array.isArray(parsed)
    ) {
      fail(400, "invalid_request", "Request body must be a JSON object");
    }

    const result = await handler(uid, parsed as Record<string, unknown>);
    sendJson(response, 200, result);
  } catch (error) {
    if (error instanceof ApiError) {
      sendJson(response, error.status, {
        error: { code: error.code, message: error.message },
      });
      return;
    }

    console.error("Unhandled Around API error", error);
    sendJson(response, 500, {
      error: { code: "internal", message: "Unexpected server error" },
    });
  }
}
