import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import validator from 'gltf-validator';

const input = process.argv[2];
if (!input) {
  console.error('Usage: npm run validate -- <path-to-file.gltf>');
  process.exit(2);
}

const gltfPath = resolve(input);
const root = dirname(gltfPath);
const bytes = new Uint8Array(await readFile(gltfPath));
const report = await validator.validateBytes(bytes, {
  uri: pathToFileURL(gltfPath).href,
  externalResourceFunction: async (uri) => {
    const decoded = decodeURIComponent(uri);
    if (/^[a-z][a-z0-9+.-]*:/i.test(decoded)) {
      const url = new URL(decoded);
      if (url.protocol !== 'file:') {
        throw new Error(`Unsupported external URI scheme: ${url.protocol}`);
      }
      return new Uint8Array(await readFile(fileURLToPath(url)));
    }
    return new Uint8Array(await readFile(resolve(root, decoded)));
  }
});

console.log(JSON.stringify(report, null, 2));
if (report.issues.numErrors > 0) {
  process.exit(1);
}
