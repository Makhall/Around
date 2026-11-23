import { HttpsError } from "firebase-functions/v2/https";

type HttpsErrorCode = ConstructorParameters<typeof HttpsError>[0];

export type Reason =
  | "unauthenticated"
  | "invalid_name"
  | "profile_missing"
  | "too_many_circles"
  | "circle_not_found"
  | "circle_full"
  | "already_member"
  | "code_not_found"
  | "code_expired"
  | "code_revoked"
  | "code_exhausted"
  | "rate_limited"
  | "owner_cannot_leave"
  | "not_owner"
  | "member_not_found";

export function fail(
  code: HttpsErrorCode,
  message: string,
  reason: Reason,
): never {
  throw new HttpsError(code, message, { reason });
}
