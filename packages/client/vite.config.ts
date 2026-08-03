import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:3000",
        changeOrigin: true,
      },
    },
  },
  preview: {
    allowedHosts: process.env.PREVIEW_ALLOWED_HOST ? [process.env.PREVIEW_ALLOWED_HOST] : undefined,
    // Unlike `vite dev`, `vite preview` doesn't proxy by default. Only needed
    // when the server isn't reachable on the client's own origin (e.g. the
    // root docker-compose.yml, where client/server are separate containers) —
    // in the Traefik-fronted deploy/ stack both are under one domain already.
    proxy: process.env.PREVIEW_API_PROXY_TARGET
      ? { "/api": { target: process.env.PREVIEW_API_PROXY_TARGET, changeOrigin: true } }
      : undefined,
  },
});
