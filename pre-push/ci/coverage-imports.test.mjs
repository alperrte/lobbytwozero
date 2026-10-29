import { test } from 'node:test';
import assert from 'node:assert/strict';
import { javaTokens, withoutImports } from './coverage.mjs';

const semantic = (before, after) => JSON.stringify(withoutImports(javaTokens(before))) !== JSON.stringify(withoutImports(javaTokens(after)));
const body = 'public class A { int f() { return 1; } }';

test('import-only edits (add, remove, reorder, star to explicit) are not a semantic change', () => {
  assert.equal(semantic(`package p;\nimport java.util.*;\n${body}`, `package p;\nimport java.util.List;\nimport java.util.Map;\n${body}`), false);
  assert.equal(semantic(`package p;\nimport b.B;\nimport a.A;\n${body}`, `package p;\nimport a.A;\nimport b.B;\n${body}`), false);
  assert.equal(semantic(`package p;\nimport static a.A.x;\n${body}`, `package p;\n${body}`), false);
});

test('any other token in the same file keeps it semantic, including next to import edits', () => {
  assert.equal(semantic(`package p;\nimport a.A;\n${body}`, `package p;\nimport a.A;\nimport b.B;\n${body.replace('return 1', 'return 2')}`), true);
  assert.equal(semantic(`package p;\n${body}`, `package q;\n${body}`), true);
  assert.equal(semantic(`package p;\n${body}`, `package p;\n${body}\nclass B {}`), true);
  assert.equal(semantic('package p;\nimport a.A;\nclass C { String s = "import x;"; }', 'package p;\nimport a.A;\nclass C { String s = "import y;"; }'), true);
});
