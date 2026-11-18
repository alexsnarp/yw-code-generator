import test from "node:test";
import assert from "node:assert/strict";
import { randomCode, constantTimeEqual, boundedInteger } from "../src/index.js";

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
