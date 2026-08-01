-- One auth-flow-session record only. Whole-value CAS; no JSON substring matching.
local current = redis.call('GET', KEYS[1])
if not current then return -1 end
if current ~= ARGV[1] then return 0 end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return -3 end
redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
return 1
