import { createHash, randomBytes } from "node:crypto";

import {
  INVITE_ALPHABET,
  INVITE_CODE_LENGTH,
  MAX_CIRCLE_NAME_LENGTH,
  MAX_DISPLAY_NAME_LENGTH,
} from "./constants";
import { fail } from "./errors";

export interface Avatar {
  type: "emoji" | "color";
  value: string;
}

export function requireObject(value: unknown): Record<string, unknown> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) {
    fail(400, "invalid_request", "Request body must be a JSON object");
  }
  return value as Record<string, unknown>;
}

export function requireString(
  data: Record<string, unknown>,
  key: string,
): string {
  const value = data[key];
  if (typeof value !== "string" || value.length === 0) {
    fail(400, "invalid_request", `Missing ${key}`);
  }
  return value;
}

export function normalizeCircleName(raw: string): string {
  const name = raw.trim();
  if (name.length < 1 || name.length > MAX_CIRCLE_NAME_LENGTH) {
    fail(
      400,
      "invalid_name",
      `Circle name must be 1-${MAX_CIRCLE_NAME_LENGTH} characters`,
    );
  }
  return name;
}

export function sanitizeDisplayName(raw: unknown): string {
  if (typeof raw !== "string") {
    return "Around user";
  }
  const name = raw.trim().slice(0, MAX_DISPLAY_NAME_LENGTH);
  return name.length > 0 ? name : "Around user";
}

export function normalizeAvatar(raw: unknown): Avatar {
  if (typeof raw === "object" && raw !== null) {
    const avatar = raw as Record<string, unknown>;
    if (
      avatar.type === "emoji" &&
      typeof avatar.value === "string" &&
      avatar.value.trim().length > 0 &&
      avatar.value.length <= 8
    ) {
      return { type: "emoji", value: avatar.value };
    }
    if (
      avatar.type === "color" &&
      typeof avatar.value === "string" &&
      /^#[0-9a-fA-F]{6}$/.test(avatar.value)
    ) {
      return { type: "color", value: avatar.value.toUpperCase() };
    }
  }
  return { type: "color", value: "#006A6A" };
}

export function normalizeInviteCode(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9]/g, "").toUpperCase();
}

export function generateInviteCode(): string {
  const bytes = randomBytes(INVITE_CODE_LENGTH);
  let code = "";
  for (let i = 0; i < INVITE_CODE_LENGTH; i += 1) {
    code += INVITE_ALPHABET[bytes[i] % INVITE_ALPHABET.length];
  }
  return code;
}

export function hashInviteCode(code: string): string {
  return createHash("sha256").update(code).digest("hex");
}
