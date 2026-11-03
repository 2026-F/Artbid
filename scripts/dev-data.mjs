import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync, renameSync, openSync, closeSync, unlinkSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

// Deliberately local-only. This tool must never accept a remote API/database target.
const root = fileURLToPath(new URL('../', import.meta.url));
const base = process.env.ARTBID_LOCAL_API || 'http://localhost:8080';
const origin = new URL(base);
if (!['http://localhost:8080', 'http://127.0.0.1:8080'].includes(origin.origin) || origin.pathname !== '/' || origin.search || origin.hash || origin.username || origin.password) throw new Error('Only localhost:8080 is supported.');
if (process.env.DOCKER_HOST || process.env.DOCKER_CONTEXT) throw new Error('Unset DOCKER_HOST/DOCKER_CONTEXT; only the local Docker engine is supported.');
const context = JSON.parse(execFileSync('docker', ['context', 'inspect'], { encoding: 'utf8' }))[0];
if (!/^(npipe:\/\/|unix:\/\/)/.test(context.Endpoints.docker.Host)) throw new Error('Remote Docker contexts are not supported.');
const directory = path.join(root, '.local-dev');
mkdirSync(directory, { recursive: true });
const stateFile = path.join(directory, 'data.json');
const lockFile = path.join(directory, 'data.lock');
let lock;
try { lock = openSync(lockFile, 'wx'); } catch { throw new Error('Another dev-data command is running (or a stale .local-dev/data.lock requires inspection).'); }
const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8')) : {
  version: 1, run: randomUUID(), origin: origin.origin, accounts: {}, artist: null, artworks: {}, auctions: {}, pending: null,
};
const save = () => { writeFileSync(`${stateFile}.tmp`, JSON.stringify(state, null, 2)); renameSync(`${stateFile}.tmp`, stateFile); };
const id = value => { if (!Number.isSafeInteger(value) || value <= 0) throw new Error('Invalid tracked ID'); return value; };
const sqlString = value => `'${String(value).replaceAll("'", "''")}'`;
function sql(query) {
  return execFileSync('docker', ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'artbid', '-d', 'artbid', '-v', 'ON_ERROR_STOP=1', '-At'], { cwd: root, input: query, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
}
async function request(route, method = 'GET', body, token) {
  const response = await fetch(`${origin.origin}${route}`, { method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body) });
  if (!response.ok) throw new Error(`${method} ${route}: HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}
// Persist intent before a create request. An ambiguous failure must not silently create duplicates on retry.
async function create(key, route, body, token, accept) {
  state.pending = { key, route }; save();
  const result = await request(route, 'POST', body, token);
  id(result.id); accept(result); state.pending = null; save(); return result;
}
async function accounts(password) {
  for (const [key, role] of [['admin', 'ADMIN'], ['seller', 'CONSIGNOR'], ['bidderA', 'BIDDER'], ['bidderB', 'BIDDER']]) {
    if (!state.accounts[key]) {
      const email = `dev-${state.run}-${key.toLowerCase()}@example.test`;
      await create(`account:${key}`, '/api/auth/signup', { email, password, name: `[DEV] ${key}` }, null,
        member => { state.accounts[key] = { id: member.id, email, role }; });
    }
    const account = state.accounts[key];
    const member = await request(`/api/members/${id(account.id)}`);
    if (member.email !== account.email) throw new Error('API and recorded account do not match; stop without modifying data.');
    // Role bootstrap is limited to the generated local account and checked in this Compose database.
    const changed = sql(`UPDATE member SET role=${sqlString(account.role)} WHERE id=${id(account.id)} AND email=${sqlString(account.email)} RETURNING id;`);
    if (!changed.split(/\r?\n/).includes(String(account.id))) throw new Error('Local API and Compose DB do not match.');
  }
  const tokens = {};
  for (const key of ['admin', 'seller']) tokens[key] = (await request('/api/auth/login', 'POST', { email: state.accounts[key].email, password })).accessToken;
  return tokens;
}
async function prepareArtwork(key, tokens, image = true, category = 'PAINTING') {
  if (!state.artworks[key]) {
    const title = `[DEV ${state.run.slice(0, 8)}] ${key}`;
    await create(`artwork:${key}`, '/api/artworks', { consignorId: state.accounts.seller.id, artistId: state.artist.id,
      title, description: '로컬 개발·검증용 작품. 실제 판매 작품이 아닙니다.', category, medium: '개발용 이미지',
      widthCm: 60, heightCm: 80, startPrice: 100000, estimatedPrice: 200000,
      imageUrl: image ? 'http://localhost:3000/dev-artwork.svg' : null }, tokens.seller,
      artwork => { state.artworks[key] = { id: artwork.id, title }; });
  }
  const tracked = state.artworks[key];
  const artwork = await request(`/api/artworks/${id(tracked.id)}`, 'GET', undefined, tokens.seller);
  if (artwork.title !== tracked.title || artwork.consignorId !== state.accounts.seller.id) throw new Error('Tracked artwork identity changed.');
  if (artwork.status === 'PENDING_REVIEW') await request(`/api/artworks/${tracked.id}/approve`, 'POST', undefined, tokens.admin);
  return tracked.id;
}
function localTime(date) {
  // The existing backend uses timezone-less LocalDateTime. Match the local JVM wall clock.
  const parts = [date.getFullYear(), date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds()].map((v, i) => i ? String(v).padStart(2, '0') : String(v));
  return `${parts[0]}-${parts[1]}-${parts[2]}T${parts[3]}:${parts[4]}:${parts[5]}`;
}
async function prepareAuction(key, artworkId, tokens, ongoing, durationMs) {
  if (!state.auctions[key]) {
    const now = Date.now();
    await create(`auction:${key}`, '/api/auctions', { artworkId, startPrice: 100000, minBidUnit: 10000,
      previewStart: localTime(new Date(now - 3600000)), previewEnd: localTime(new Date(now + (ongoing ? -60000 : 3600000))),
      auctionEndAt: localTime(new Date(now + durationMs)) }, tokens.admin,
      auction => { state.auctions[key] = { id: auction.id, artworkId, ongoing }; });
  }
  const tracked = state.auctions[key];
  const auction = await request(`/api/auctions/${id(tracked.id)}/current-price`);
  if (auction.artworkId !== artworkId) throw new Error('Tracked auction identity changed.');
  if (ongoing && ['PREVIEW', 'SCHEDULED'].includes(auction.status)) await request(`/api/auctions/${auction.id}/status`, 'PATCH', { status: 'ONGOING' }, tokens.admin);
  return auction.id;
}
function cleanup() {
  if (state.pending) throw new Error('An ambiguous create is recorded. Inspect .local-dev/data.json and reconcile it before cleanup.');
  const artworks = Object.values(state.artworks), auctions = Object.values(state.auctions), members = Object.values(state.accounts);
  const artIds = artworks.map(v => id(v.id)).join(',') || 'NULL';
  const auctionIds = auctions.map(v => id(v.id)).join(',') || 'NULL';
  const memberIds = members.map(v => id(v.id)).join(',') || 'NULL';
  const assertions = [
    ...artworks.map(v => `IF EXISTS (SELECT 1 FROM artwork WHERE id=${id(v.id)} AND (title IS DISTINCT FROM ${sqlString(v.title)} OR consignor_id IS DISTINCT FROM ${id(state.accounts.seller.id)})) THEN RAISE EXCEPTION 'Artwork identity mismatch'; END IF;`),
    ...auctions.map(v => `IF EXISTS (SELECT 1 FROM auction WHERE id=${id(v.id)} AND artwork_id IS DISTINCT FROM ${id(v.artworkId)}) THEN RAISE EXCEPTION 'Auction identity mismatch'; END IF;`),
    ...members.map(v => `IF EXISTS (SELECT 1 FROM member WHERE id=${id(v.id)} AND email IS DISTINCT FROM ${sqlString(v.email)}) THEN RAISE EXCEPTION 'Account identity mismatch'; END IF;`),
  ].join('\n');
  // Do not remove trade/stream/payment evidence or objects created outside this manifest.
  sql(`BEGIN;
    LOCK TABLE artwork, auction, member, artist, artwork_media, bid, settlement, livestream, seller_profile, notification, artwork_certificate IN SHARE ROW EXCLUSIVE MODE;
    DO $$ BEGIN ${assertions}
      IF EXISTS (SELECT 1 FROM auction WHERE artwork_id IN (${artIds}) AND id NOT IN (${auctionIds}))
        OR EXISTS (SELECT 1 FROM bid WHERE auction_id IN (${auctionIds}) OR bidder_id IN (${memberIds}))
        OR EXISTS (SELECT 1 FROM settlement WHERE auction_id IN (${auctionIds}) OR winner_id IN (${memberIds}))
        OR EXISTS (SELECT 1 FROM livestream WHERE auction_id IN (${auctionIds}))
        OR EXISTS (SELECT 1 FROM artwork_media WHERE artwork_id IN (${artIds}))
        OR EXISTS (SELECT 1 FROM artwork_certificate WHERE artwork_id IN (${artIds}) OR consignor_id IN (${memberIds}))
        OR EXISTS (SELECT 1 FROM seller_profile WHERE member_id IN (${memberIds}))
        OR EXISTS (SELECT 1 FROM notification WHERE member_id IN (${memberIds}))
        OR EXISTS (SELECT 1 FROM artwork WHERE (consignor_id IN (${memberIds}) OR artist_id=${state.artist ? id(state.artist.id) : 'NULL'}) AND id NOT IN (${artIds}))
      THEN RAISE EXCEPTION 'Related data exists; cleanup stopped. Keep the manifest and review relations.'; END IF;
      ${state.artist ? `IF EXISTS (SELECT 1 FROM artist WHERE id=${id(state.artist.id)} AND name IS DISTINCT FROM ${sqlString(state.artist.name)}) THEN RAISE EXCEPTION 'Artist identity mismatch'; END IF;` : ''}
    END $$;
    DELETE FROM auction WHERE id IN (${auctionIds});
    DELETE FROM artwork WHERE id IN (${artIds});
    ${state.artist ? `DELETE FROM artist WHERE id=${id(state.artist.id)};` : ''}
    DELETE FROM member WHERE id IN (${memberIds}); COMMIT;`);
  renameSync(stateFile, path.join(directory, `cleaned-${state.run}.json`));
  console.log('Recorded data cleaned. Unrelated data preserved.');
}
try {
  if (state.origin !== origin.origin || state.version !== 1) throw new Error('Manifest target/version mismatch.');
  const command = process.argv[2] || 'seed';
  if (!['seed', 'auction:new', 'auction:closing', 'cleanup'].includes(command)) throw new Error('Use seed | auction:new | auction:closing | cleanup');
  save();
  if (command === 'cleanup') cleanup();
  else {
    if (state.pending) throw new Error('A previous create has an unknown result. Reconcile the pending entry before retrying.');
    const password = process.env.ARTBID_TEST_PASSWORD;
    if (!password || password.length < 8 || password.length > 64) throw new Error('Set ARTBID_TEST_PASSWORD (8–64 characters). It is never saved or printed.');
    const tokens = await accounts(password);
    if (!state.artist) await create('artist', '/api/artists', { name: `[DEV ${state.run.slice(0, 8)}] 테스트 작가` }, tokens.seller, artist => { state.artist = { id: artist.id, name: artist.name }; });
    if (command === 'seed') {
      await prepareArtwork('경매 없는 작품', tokens);
      await prepareArtwork('이미지 없는 작품', tokens, false, 'SCULPTURE');
      const preview = await prepareArtwork('시작 전 작품', tokens);
      await prepareAuction('preview', preview, tokens, false, 86400000);
      let live = await prepareArtwork('진행 작품', tokens);
      let auctionId = await prepareAuction('ongoing', live, tokens, true, 86400000);
      // Offset existing databases may accidentally have equal IDs. Ensure a differing pair without modifying IDs.
      if (auctionId === live && !state.auctions.mismatched) {
        await prepareArtwork('ID 간격 확인 작품', tokens);
        live = await prepareArtwork('ID 불일치 검증 작품', tokens);
        auctionId = await prepareAuction('mismatched', live, tokens, true, 86400000);
        if (auctionId === live) throw new Error('ID mismatch fixture could not be prepared.');
      }
    } else {
      const key = `${command}-${randomUUID().slice(0, 8)}`;
      const artworkId = await prepareArtwork(key, tokens);
      await prepareAuction(key, artworkId, tokens, true, command === 'auction:closing' ? 120000 : 86400000);
    }
    console.log(JSON.stringify({ accounts: state.accounts, artworks: state.artworks, auctions: state.auctions }, null, 2));
  }
} catch (error) {
  // Never emit request tokens, passwords, SQL output or full HTTP responses.
  const detail = String(error?.stderr ?? '');
  console.error(detail.includes('Related data exists') ? 'Related data exists; cleanup stopped without deleting anything. Keep the manifest and review relations.' : error instanceof Error ? error.message.split('\n')[0] : 'Local data command failed.');
  process.exitCode = 1;
} finally { closeSync(lock); unlinkSync(lockFile); }
