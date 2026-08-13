-- One username-recovery idempotency record only. Ownership-checked atomic
-- transition for completion (COMPLETED + replay snapshot) or stable unknown
-- outcome (UNKNOWN_OUTCOME with no replay snapshot).
--
-- The stored IN_PROGRESS record must match the caller's action, idempotency key,
-- semantic fingerprint, and operation reference. The remaining expiry is preserved
-- (PTTL -> PSETEX); transitions never extend retention beyond the approved expiry.
--
-- KEYS[1] idempotency record key
-- ARGV[1] expected action name
-- ARGV[2] expected idempotency key value
-- ARGV[3] expected semantic fingerprint
-- ARGV[4] expected operation reference
-- ARGV[5] new status ('COMPLETED' or 'UNKNOWN_OUTCOME')
-- ARGV[6] updatedAt ISO-8601 instant
-- ARGV[7] replay snapshot value ('' for UNKNOWN_OUTCOME -> snapshot omitted)
--
-- Returns the encoded new record on success; otherwise one of the literal codes
-- MISSING / CORRUPT / CONFLICT / EXPIRED (never any stored content).
local current = redis.call('GET', KEYS[1])
if not current then return 'MISSING' end
local ok, decoded = pcall(cjson.decode, current)
if not ok or type(decoded) ~= 'table' then return 'CORRUPT' end
if decoded.action ~= ARGV[1]
    or decoded.idempotencyKey ~= ARGV[2]
    or decoded.fingerprint ~= ARGV[3]
    or decoded.operationReference ~= ARGV[4] then
  return 'CONFLICT'
end
if decoded.status ~= 'IN_PROGRESS' then return 'CONFLICT' end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return 'EXPIRED' end
local updated = {}
for k, v in pairs(decoded) do updated[k] = v end
updated.status = ARGV[5]
updated.updatedAt = ARGV[6]
if ARGV[7] == '' then
  updated.replaySnapshot = nil
else
  updated.replaySnapshot = ARGV[7]
end
local encoded = cjson.encode(updated)
redis.call('PSETEX', KEYS[1], ttl, encoded)
return encoded
