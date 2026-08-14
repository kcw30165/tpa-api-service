-- One auth-flow-session record only. Revision-token CAS deletion.
local current = redis.call('GET', KEYS[1])
if not current then return -1 end

local decodedOk, decoded = pcall(cjson.decode, current)
if not decodedOk or type(decoded) ~= 'table' then return -3 end

local currentRevision = decoded['sessionRevision']
if type(currentRevision) ~= 'string' or currentRevision == '' then return -2 end
if currentRevision ~= ARGV[1] then return 0 end

redis.call('DEL', KEYS[1])
return 1
