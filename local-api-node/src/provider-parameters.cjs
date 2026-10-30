// Several upstream modules compare `like` with the literal string "false".
// Normalize only that field: stringifying all booleans breaks qrimg/refresh.
module.exports = function providerParameters(params, cookie) {
  const query = { ...params, cookie, timeout: 12000 };
  if (Object.hasOwn(query, 'like')) query.like = String(query.like);
  return query;
};
