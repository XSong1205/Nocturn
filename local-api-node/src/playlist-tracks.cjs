// Keep music playlist edits to one submission and preserve the normal provider envelope.
// The upstream helper retries code 512 and nests successful responses inside another body.
const createOption = require('@neteasecloudmusicapienhanced/api/util/option.js');

module.exports = async function playlistTracks(query, request) {
  const data = {
    op: query.op,
    pid: query.pid,
    trackIds: JSON.stringify(query.tracks.split(',')),
    imme: 'true',
  };
  try {
    return await request('/api/playlist/manipulate/tracks', data, createOption(query));
  } catch (error) {
    // Duplicate or unavailable tracks are business results, not permission to retry a mutation.
    if (error?.body?.code === 512) return { status: 200, body: error.body };
    throw error;
  }
};
