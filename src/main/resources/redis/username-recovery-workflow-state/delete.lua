-- One username-recovery workflow-state record only. Idempotent deletion: 1 when the
-- key existed and was removed, -1 when the key was already absent.
if redis.call('EXISTS', KEYS[1]) == 1 then
  redis.call('DEL', KEYS[1])
  return 1
end
return -1
