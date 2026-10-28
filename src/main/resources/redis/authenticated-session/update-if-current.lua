-- One authenticated-session record only. Revision-token CAS, mirroring
-- auth-flow-session/replace-revisioned.lua. Deliberately no legacy-adoption
-- branch: a missing or malformed sessionRevision fails closed.
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
redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
return 1
