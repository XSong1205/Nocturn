// Explicit routes are also the only provider modules included in the mobile bundle.
// Request transport controls are intentionally absent from the parameter lists.
export const endpoints = Object.freeze({
  'login/qr/key': { module: 'login_qr_key', params: [] },
  'login/qr/create': { module: 'login_qr_create', params: ['key', 'qrimg'], required: ['key'] },
  'login/qr/check': { module: 'login_qr_check', params: ['key'], required: ['key'] },
  'login/status': { module: 'login_status', params: [] },
  'login/refresh': { module: 'login_refresh', params: [] },
  'register/anonimous': { module: 'register_anonimous', params: [], keys: 'xeapi' },
  'captcha/sent': { module: 'captcha_sent', params: ['phone', 'ctcode'], required: ['phone'] },
  'captcha/verify': { module: 'captcha_verify', params: ['phone', 'captcha', 'ctcode'], required: ['phone', 'captcha'] },
  // The mobile client uses SMS codes; passwords are not accepted by this local contract.
  'login/cellphone': { module: 'login_cellphone', params: ['phone', 'captcha', 'countrycode'], required: ['phone', 'captcha'] },
  logout: { module: 'logout', params: [] },
  search: { module: 'search', params: ['keywords', 'type', 'limit', 'offset'], required: ['keywords'] },
  cloudsearch: { module: 'cloudsearch', params: ['keywords', 'type', 'limit', 'offset'], required: ['keywords'] },
  'search/suggest': { module: 'search_suggest', params: ['keywords', 'type'], required: ['keywords'] },
  'search/default': { module: 'search_default', params: [] },
  'search/hot/detail': { module: 'search_hot_detail', params: [] },
  'song/detail': { module: 'song_detail', params: ['ids'], required: ['ids'] },
  'song/url/v1': { module: 'song_url_v1', params: ['id', 'level'], required: ['id', 'level'], keys: 'xeapi', batchId: true },
  'check/music': { module: 'check_music', params: ['id', 'br'], required: ['id'] },
  'song/music/detail': { module: 'song_music_detail', params: ['id'], required: ['id'] },
  lyric: { module: 'lyric', params: ['id'], required: ['id'] },
  'lyric/new': { module: 'lyric_new', params: ['id'], required: ['id'] },
  personalized: { module: 'personalized', params: ['limit'] },
  'recommend/resource': { module: 'recommend_resource', params: [] },
  'recommend/songs': { module: 'recommend_songs', params: ['afresh'] },
  'homepage/block/page': { module: 'homepage_block_page', params: ['refresh', 'cursor'] },
  banner: { module: 'banner', params: ['type'] },
  toplist: { module: 'toplist', params: [] },
  'toplist/detail': { module: 'toplist_detail', params: [] },
  'playlist/detail': { module: 'playlist_detail', params: ['id', 's'], required: ['id'] },
  'playlist/track/all': { module: 'playlist_track_all', params: ['id', 'limit', 'offset'], required: ['id'] },
  'playlist/subscribe': { module: 'playlist_subscribe', params: ['id', 't'], required: ['id', 't'] },
  'user/detail': { module: 'user_detail', params: ['uid'], required: ['uid'] },
  'user/account': { module: 'user_account', params: [] },
  'user/subcount': { module: 'user_subcount', params: [] },
  'user/playlist': { module: 'user_playlist', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'user/playlist/create': { module: 'user_playlist_create', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'user/playlist/collect': { module: 'user_playlist_collect', params: ['uid', 'limit', 'offset'], required: ['uid'] },
  'record/recent/song': { module: 'record_recent_song', params: ['limit'] },
  likelist: { module: 'likelist', params: ['uid'], required: ['uid'] },
  like: { module: 'like', params: ['id', 'like'], required: ['id'] },
  'song/like': { module: 'song_like', params: ['id', 'uid', 'like'], required: ['id', 'uid', 'like'] },
  album: { module: 'album', params: ['id'], required: ['id'] },
  artists: { module: 'artists', params: ['id'], required: ['id'] },
  'artist/detail': { module: 'artist_detail', params: ['id'], required: ['id'] },
  'artist/album': { module: 'artist_album', params: ['id', 'limit', 'offset'], required: ['id'] },
  'personal/fm': { module: 'personal_fm', params: [] },
});

export const audioQualities = Object.freeze(['standard', 'higher', 'exhigh', 'lossless', 'hires', 'jyeffect', 'sky', 'dolby', 'jymaster']);
const levels = new Set(audioQualities);
const idFields = new Set(['id', 'uid']);
const countFields = new Set(['limit', 'offset', 's']);
const positiveId = /^[1-9]\d{0,19}$/;
const searchTypes = new Set(['1', '10', '100', '1000', '1002', '1004', '1006', '1009', '1014', '1018', '2000']);

function validIds(value, batch = false) {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0;
  if (typeof value !== 'string') return false;
  const ids = value.split(',');
  return ids.length <= (batch ? 200 : 1) && ids.every((id) => positiveId.test(id));
}

export function validateParameters(endpoint, body) {
  const spec = Object.hasOwn(endpoints, endpoint) ? endpoints[endpoint] : null;
  if (!spec) return 'Unknown endpoint.';
  if (!body || Array.isArray(body) || typeof body !== 'object') return 'Expected a JSON object.';
  const allowed = new Set([...spec.params, 'cookie']);
  for (const [key, value] of Object.entries(body)) {
    if (!allowed.has(key)) return `Unsupported parameter: ${key}.`;
    if (!['string', 'number', 'boolean'].includes(typeof value)) return `Invalid parameter: ${key}.`;
    if (typeof value === 'number' && !Number.isFinite(value)) return `Invalid parameter: ${key}.`;
    const maxLength = key === 'cookie' ? 16384 : key === 'ids' || (key === 'id' && spec.batchId) ? 4200 : 4096;
    if (typeof value === 'string' && value.length > maxLength) return `Parameter too long: ${key}.`;
    if (key === 'cookie' && typeof value !== 'string') return 'cookie must be a string.';
    if (key === 'cookie' && /[\r\n\0]/.test(value)) return 'Invalid cookie.';
    if (idFields.has(key) && !validIds(value, key === 'id' && spec.batchId)) return `Invalid parameter: ${key}.`;
    if (key === 'ids' && (typeof value !== 'string' || !validIds(value, true))) return 'ids must be a string containing at most 200 comma-separated positive IDs.';
    if (countFields.has(key) && (!/^\d+$/.test(String(value)) || Number(value) > (key === 'offset' ? 100000 : 200))) return `Invalid parameter: ${key}.`;
    if (key === 'limit' && Number(value) < 1) return 'limit must be positive.';
    if (key === 'keywords' && (typeof value !== 'string' || value.trim().length < 1 || value.length > 200)) return 'keywords must contain 1–200 characters.';
    if (key === 'key' && (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,256}$/.test(value))) return 'Invalid QR key.';
    if (key === 'level' && !levels.has(value)) return 'Unsupported audio quality.';
    if (key === 't' && !['1', '2'].includes(String(value))) return 't must be 1 or 2.';
    if (key === 'like' && ![true, false, 'true', 'false'].includes(value)) return 'like must be boolean.';
    if (['qrimg', 'afresh', 'refresh'].includes(key) && typeof value !== 'boolean') return `${key} must be boolean.`;
    if (key === 'type') {
      const accepted = endpoint === 'search/suggest' ? ['mobile', 'web'].includes(value)
        : endpoint === 'banner' ? ['0', '1', '2', '3'].includes(String(value)) : searchTypes.has(String(value));
      if (!accepted) return 'Unsupported type.';
    }
    if (key === 'phone' && (typeof value !== 'string' || !/^\d{5,15}$/.test(value))) return 'Invalid phone number.';
    if (['ctcode', 'countrycode'].includes(key) && (typeof value !== 'string' || !/^[1-9]\d{0,3}$/.test(value))) return 'Invalid country code.';
    if (key === 'captcha' && (typeof value !== 'string' || !/^\d{4,8}$/.test(value))) return 'Invalid verification code.';
    if (key === 'cursor' && (typeof value !== 'string' || value.length > 4096)) return 'Invalid cursor.';
    if (key === 'br' && (!['string', 'number'].includes(typeof value) || !Number.isSafeInteger(Number(value)) || Number(value) < 1 || Number(value) > 10000000)) return 'Invalid bitrate.';
  }
  for (const required of spec.required || []) {
    if (!Object.hasOwn(body, required) || body[required] === '') return `Missing parameter: ${required}.`;
  }
  return null;
}
