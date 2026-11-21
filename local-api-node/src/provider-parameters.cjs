// Several upstream modules compare `like` with the literal string "false",
// and `song_url_v1` checks `unblock === 'true'`.
// Normalize only these fields: stringifying all booleans breaks qrimg/refresh.
module.exports = function providerParameters(params, cookie) {
  const query = { ...params, cookie, timeout: 12000 };
  if (Object.hasOwn(query, 'like')) query.like = String(query.like);
  if (Object.hasOwn(query, 'unblock')) query.unblock = String(query.unblock);
  return query;
};
