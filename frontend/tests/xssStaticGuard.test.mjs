import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";

const sourceRoot = path.resolve("src");
const sourceExtensions = new Set([".js", ".ts", ".vue"]);
const forbiddenHtmlSinks = [
  "v-html",
  ".innerHTML",
  ".outerHTML",
  ".insertAdjacentHTML(",
  "new DOMParser(",
  "dangerouslySetInnerHTML",
];

async function sourceFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = await Promise.all(entries.map(async (entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(fullPath);
    }
    return sourceExtensions.has(path.extname(entry.name)) ? [fullPath] : [];
  }));
  return files.flat();
}

const violations = [];

for (const file of await sourceFiles(sourceRoot)) {
  const content = await readFile(file, "utf8");
  for (const marker of forbiddenHtmlSinks) {
    if (content.includes(marker)) {
      violations.push(`${path.relative(process.cwd(), file)} contains ${marker}`);
    }
  }
}

assert.deepEqual(violations, []);
console.log("XSS static guard passed");
