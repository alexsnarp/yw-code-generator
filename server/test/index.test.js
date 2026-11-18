import test from "node:test";
import assert from "node:assert/strict";
import { randomCode, constantTimeEqual, boundedInteger, createCodes } from "../src/index.js";

test("codes are eight Crockford-style characters in XXXX-XXXX format", () => {
  const code = randomCode();
  assert.match(code, /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}-[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{4}$/);
  assert.equal(code.replace("-", "").length, 8);
});

test("administrator secret comparison requires exact match", () => {
  assert.equal(constantTimeEqual("secret-value", "secret-value"), true);
  assert.equal(constantTimeEqual("secret-value", "secret-valuF"), false);
  assert.equal(constantTimeEqual("short", "longer"), false);
});

test("redemption limits are bounded to 1..100", () => {
  assert.equal(boundedInteger(1, 1, 100, 1), 1);
  assert.equal(boundedInteger(100, 1, 100, 1), 100);
  assert.equal(boundedInteger(500, 1, 100, 1), 100);
  assert.equal(boundedInteger(0, 1, 100, 1), 1);
  assert.equal(boundedInteger("NaN", 1, 100, 1), 1);
});

test("createCodes stores only hashes and applies the requested redemption cap", async () => {
  const inserts = [];
  const db = {
    prepare(sql) {
      return { bind(...values) { return { async run() { inserts.push({ sql, values }); return { meta: { changes: 1 } }; } }; } };
    }
  };
  const created = await createCodes(db, { count: 2, maxUses: 7, ttlHours: 24 }, 1000);
  assert.equal(created.codes.length, 2);
  assert.equal(created.maxUses, 7);
  assert.equal(created.expiresInHours, 24);
  assert.equal(inserts.length, 2);
  for (const item of inserts) {
    assert.equal(item.values[1], 1000 + 24 * 3600);
    assert.equal(item.values[2], 1000);
    assert.equal(item.values[3], 7);
    assert.equal(item.values[0].length, 64);
  }
  assert.notEqual(inserts[0].values[0], created.codes[0]);
});
