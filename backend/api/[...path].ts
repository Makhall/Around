import type { VercelRequest, VercelResponse } from "@vercel/node";

import { handleRequest } from "../src/router";

export default async function handler(
  request: VercelRequest,
  response: VercelResponse,
): Promise<void> {
  await handleRequest(request, response);
}
