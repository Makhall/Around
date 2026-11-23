import { setGlobalOptions } from "firebase-functions/v2";
import { onRequest } from "firebase-functions/v2/https";

// Cost guardrail from the project plan: cap every function's instances.
setGlobalOptions({
  region: "asia-southeast1",
  maxInstances: 3,
});

/**
 * Phase 0 health check. Confirms the functions emulator and deploy pipeline work.
 * Real callables (createCircle, joinCircle, ...) arrive in Phase 1.
 */
export const health = onRequest((_request, response) => {
  response.status(200).json({
    ok: true,
    service: "around-functions",
    phase: 0,
  });
});
