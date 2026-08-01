-- KEYS[1] session record; KEYS[2] existing mutation-lock record.
-- Whole-value comparison proves unchanged legacy state; no JSON substring matching.
if redis.call('GET', KEYS[2]) ~= ARGV[1] then return -2 end
local current = redis.call('GET', KEYS[1])
if not current then return -1 end
if current ~= ARGV[2] then return 0 end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return -3 end
redis.call('PSETEX', KEYS[1], ttl, ARGV[3])
return 1
