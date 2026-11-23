import "dotenv/config";

import { createServer, type Server } from "node:http";

import { deleteApp, getApps } from "firebase-admin/app";

import { handleRequest } from "./src/router";

export function startServer(
  port: number = Number(process.env.PORT ?? 3000),
): Promise<Server> {
  return new Promise((resolve, reject) => {
    const server = createServer((request, response) => {
      void handleRequest(request, response);
    });
    server.once("error", reject);
    server.listen(port, () => {
      console.log(`Around API listening on http://127.0.0.1:${port}`);
      resolve(server);
    });
  });
}

export async function stopServer(server: Server): Promise<void> {
  await new Promise<void>((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
      } else {
        resolve();
      }
    });
  });
  await Promise.all(getApps().map((app) => deleteApp(app)));
}

if (require.main === module) {
  startServer().catch((error) => {
    console.error("Failed to start Around API", error);
    process.exit(1);
  });
}
