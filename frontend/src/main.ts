import { createApp } from "vue";
import App from "./App.vue";
import { router } from "./router";
import { localFonts } from "./generated/localFonts";
import "./styles.css";

const fontFormats: Record<string, string> = {
  otf: "opentype",
  ttf: "truetype",
  woff: "woff",
  woff2: "woff2",
};

const fontStyles = document.createElement("style");
fontStyles.dataset.viziLocalFonts = "true";
fontStyles.textContent = localFonts.map((font) =>
  `@font-face{font-family:${JSON.stringify(font.family)};src:url(${JSON.stringify(font.url)}) format("${fontFormats[font.format] ?? font.format}");font-display:swap;}`,
).join("\n");
document.head.append(fontStyles);

createApp(App).use(router).mount("#app");
