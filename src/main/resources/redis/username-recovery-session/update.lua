-- One username-recovery session record only. Atomic revision compare-and-set
-- that preserves the remaining expiry (PTTL -> PSETEX; never extends the TTL).
local current = redis.call('GET', KEYS[1])
if not current then return -1 end
local expected = tonumber(ARGV[1])
if expected == nil then return -4 end
local decoded = cjson.decode(current)
if type(decoded) ~= 'table' or decoded.revision == nil then
  error('username-recovery session record is corrupt')
end
if tonumber(decoded.revision) ~= expected then return 0 end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return -3 end
redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
return 1
