import { setGlobalOptions } from "firebase-functions/v2";
import { onRequest } from "firebase-functions/v2/https";

import "./admin";
import { REGION } from "./constants";

// Cost guardrail from the project plan: cap every function's instances.
setGlobalOptions({
  region: REGION,
  maxInstances: 3,
});

export { createCircle, joinCircle, leaveCircle, removeMember, rotateInviteCode } from "./circles";

/**
 * Phase 0 health check. Confirms the functions emulator and deploy pipeline work.
 */
export const health = onRequest((_request, response) => {
  response.status(200).json({
    ok: true,
    service: "around-functions",
    phase: 1,
  });
});
