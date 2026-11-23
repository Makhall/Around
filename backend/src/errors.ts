export type ApiErrorCode =
  | "invalid_request"
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
  | "member_not_found"
  | "unauthenticated"
  | "not_found"
  | "internal";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ApiErrorCode,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function fail(
  status: number,
  code: ApiErrorCode,
  message: string,
): never {
  throw new ApiError(status, code, message);
}
