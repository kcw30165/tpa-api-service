-- Lifecycle CAS: no creation, no relative TTL, no extension through execution delay.
local current = redis.call('GET', KEYS[1])
if not current then return -1 end
local decodedOk, decoded = pcall(cjson.decode, current)
if not decodedOk or type(decoded) ~= 'table' then return -3 end
local currentRevision = decoded['sessionRevision']
if type(currentRevision) ~= 'string' or #currentRevision ~= 32
    or not string.match(currentRevision, '^[0-9a-f]+$') then return -2 end
if currentRevision ~= ARGV[1] then return 0 end
if redis.call('PTTL', KEYS[1]) <= 0 then return -3 end
if not string.match(ARGV[3], '^[1-9]%d*$') then return -4 end
local deadline = tonumber(ARGV[3])
if not deadline or deadline > 9007199254740991 then return -4 end
local time = redis.call('TIME')
local now = time[1] * 1000 + math.floor(time[2] / 1000)
if deadline <= now then return -4 end
redis.call('SET', KEYS[1], ARGV[2])
redis.call('PEXPIREAT', KEYS[1], ARGV[3])
return 1
