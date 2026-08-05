-- One auth-flow-session record only. No cross-record transaction is claimed.
if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
return 1
