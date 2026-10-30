local current = redis.call('GET', KEYS[1])
if not current then return -1 end
local decodedOk, decoded = pcall(cjson.decode, current)
if not decodedOk or type(decoded) ~= 'table' then return -3 end
local currentRevision = decoded['sessionRevision']
if type(currentRevision) ~= 'string' or #currentRevision ~= 32
    or not string.match(currentRevision, '^[0-9a-f]+$') then return -2 end
if currentRevision ~= ARGV[1] then return 0 end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then return -3 end
local rawDeadline = ARGV[3]
if type(rawDeadline) ~= 'string' or not string.match(rawDeadline, '^[1-9][0-9]*$')
    or #rawDeadline > 16 then return -4 end
local deadline = tonumber(rawDeadline)
if not deadline or deadline > 9007199254740991 then return -4 end
local now = redis.call('TIME')
local nowMs = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000)
if nowMs >= deadline then return -5 end
redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
return 1
