const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" }
  });
}

function randomCode() {
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  const value = Array.from(bytes, byte => CODE_ALPHABET[byte & 31]).join("");
  return value.slice(0, 4) + "-" + value.slice(4);
}

async function sha256(value) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
}

function constantTimeEqual(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}

async function isAdmin(request, env) {
  const expected = env.ADMIN_SECRET || "";
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return false;
  const supplied = header.slice(7);
  return expected.length >= 32 && constantTimeEqual(supplied, expected);
}

function boundedInteger(value, min, max, fallback) {
  const number = Number(value);
  if (!Number.isSafeInteger(number)) return fallback;
  return Math.max(min, Math.min(max, number));
}

async function createCodes(db, input, timestamp = Math.floor(Date.now() / 1000)) {
  const count = boundedInteger(input.count, 1, 50, 1);
  const maxUses = boundedInteger(input.maxUses, 1, 100, 1);
  const ttlHours = boundedInteger(input.ttlHours, 1, 720, 168);
  const codes = [];
  for (let i = 0; i < count; i++) {
    let inserted = false;
    for (let attempt = 0; attempt < 5 && !inserted; attempt++) {
      const code = randomCode();
      const hash = await sha256(code);
      const result = await db.prepare(
        "INSERT OR IGNORE INTO authorization_codes (code_hash, expires_at, used_at, created_at, max_uses, uses_count) VALUES (?, ?, NULL, ?, ?, 0)"
      ).bind(hash, timestamp + ttlHours * 3600, timestamp, maxUses).run();
      if (result.meta && result.meta.changes === 1) {
        codes.push(code);
        inserted = true;
      }
    }
    if (!inserted) throw new Error("Could not allocate a unique code. Please retry.");
  }
  return { codes, maxUses, expiresInHours: ttlHours };
}

export { randomCode, sha256, constantTimeEqual, boundedInteger, createCodes };

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "YW Code Generator API" });
    }
    if (url.pathname === "/admin/codes" && request.method === "POST") {
      if (!await isAdmin(request, env)) return json({ error: "Admin authorization required." }, 401);
      let input;
      try { input = await request.json(); }
      catch (_) { return json({ error: "Expected a JSON request body." }, 400); }
      try {
        return json(await createCodes(env.DB, input || {}), 201);
      } catch (error) {
        return json({ error: error.message || "Code generation failed." }, 500);
      }
    }
    if (url.pathname === "/admin/stats" && request.method === "GET") {
      if (!await isAdmin(request, env)) return json({ error: "Admin authorization required." }, 401);
      const result = await env.DB.prepare(
        "SELECT COUNT(*) AS active_codes, COALESCE(SUM(max_uses - uses_count), 0) AS remaining_redemptions FROM authorization_codes WHERE uses_count < max_uses AND expires_at > ?"
      ).bind(Math.floor(Date.now() / 1000)).first();
      return json({ activeCodes: result?.active_codes || 0, remainingRedemptions: result?.remaining_redemptions || 0 });
    }
    return json({ error: "Not found." }, 404);
  }
};
