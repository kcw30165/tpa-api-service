-- One auth-flow-session record only. Whole-value CAS deletion.
local current = redis.call('GET', KEYS[1])
if not current then return -1 end
if current ~= ARGV[1] then return 0 end
redis.call('DEL', KEYS[1])
return 1
