import { defineConfig } from "vite";

// base "./" para que ande bajo cualquier subcarpeta (GitHub Pages publica en /nombre-del-repo/).
// server.fs.allow: los ejemplos están en ../docs/ejemplos, fuera de web/.
export default defineConfig({
  base: "./",
  server: { fs: { allow: [".."] } },
});
