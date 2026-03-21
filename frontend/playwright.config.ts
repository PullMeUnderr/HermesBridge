import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  use: {
    baseURL: "http://127.0.0.1:3007",
    trace: "retain-on-failure",
  },
  webServer: {
    command: "PATH=/Users/vladislav/.local/node-v20.18.3-darwin-arm64/bin:$PATH npm run dev -- --hostname 127.0.0.1 --port 3007",
    port: 3007,
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
