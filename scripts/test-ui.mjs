import { createRequire } from 'node:module';
import { unlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import esbuild from '../frontend/node_modules/esbuild/lib/main.js';

const output = join(tmpdir(), `chess-ui-tests-${process.pid}.cjs`);
try {
  await esbuild.build({
    entryPoints: ['frontend/tests/components.test.tsx'],
    bundle: true,
    platform: 'node',
    format: 'cjs',
    jsx: 'automatic',
    outfile: output,
  });
  createRequire(import.meta.url)(output);
} finally {
  await unlink(output).catch(() => {});
}
