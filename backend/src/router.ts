import type { IncomingMessage, ServerResponse } from "node:http";

import {
  createCircle,
  joinCircle,
  leaveCircle,
  removeMember,
  rotateInviteCode,
} from "./circles";
import { handleJson, sendJson } from "./http";

export async function handleRequest(
  request: IncomingMessage,
  response: ServerResponse,
): Promise<void> {
  const url = new URL(request.url ?? "/", "http://localhost");
  const path = url.pathname.replace(/\/+$/, "") || "/";

  switch (path) {
    case "/api/health":
      sendJson(response, 200, { ok: true, service: "around-api" });
      return;
    case "/api/createCircle":
      return handleJson(request, response, createCircle);
    case "/api/joinCircle":
      return handleJson(request, response, joinCircle);
    case "/api/rotateInviteCode":
      return handleJson(request, response, rotateInviteCode);
    case "/api/leaveCircle":
      return handleJson(request, response, leaveCircle);
    case "/api/removeMember":
      return handleJson(request, response, removeMember);
    default:
      sendJson(response, 404, {
        error: { code: "not_found", message: "Unknown endpoint" },
      });
  }
}
