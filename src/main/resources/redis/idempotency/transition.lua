-- Whole-value CAS protects logical identity, fingerprint, owner token, source status and schema.
-- The Java adapter validates the decoded record, then this script atomically proves it is unchanged.
local current = redis.call('GET', KEYS[1])
if not current then return 'MISSING' end
if current ~= ARGV[1] then return 'CONFLICT' end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return 'EXPIRED' end
redis.call('SET', KEYS[1], ARGV[2], 'PX', ttl)
return ARGV[2]
