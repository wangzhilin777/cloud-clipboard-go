import { corsHeaders } from '../cors';
import { canAccessRoom, ensureRoomAccess, hasRoomAuthEntry, normalizeRoomName, resolveRoomAuth } from '../auth';

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders },
  });
}

function withCors(response) {
  const headers = new Headers(response.headers);
  for (const [key, value] of Object.entries(corsHeaders)) {
    headers.set(key, value);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}

async function readJsonBody(request) {
  try {
    return await request.clone().json();
  } catch {
    return {};
  }
}

function getWebSocketProtocol(url) {
  return url.protocol === 'https:' ? 'wss:' : 'ws:';
}

function getSyncRoomObject(env, room) {
  if (!env.WEBSOCKET_ROOM) {
    return null;
  }
  const normalizedRoom = normalizeRoomName(room);
  return env.WEBSOCKET_ROOM.get(env.WEBSOCKET_ROOM.idFromName(normalizedRoom));
}

async function forwardToRoomObject(request, env, room, internalPath) {
  const durableObject = getSyncRoomObject(env, room);
  if (!durableObject) {
    return jsonResponse({ error: 'Service Unavailable', message: '同步服务不可用' }, 503);
  }
  const sourceURL = new URL(request.url);
  const internalURL = new URL(`https://sync.internal${internalPath}`);
  internalURL.search = sourceURL.search;
  const response = await durableObject.fetch(new Request(internalURL.toString(), request));
  return withCors(response);
}

async function validateRequestRoomAccess(request, env, room) {
  const authResult = ensureRoomAccess(request, env, room);
  if (!authResult.ok) {
    return { ok: false, response: authResult.response };
  }
  return { ok: true, room: authResult.room };
}

export class SyncHandler {
  static async server(request, env) {
    const url = new URL(request.url);
    const room = normalizeRoomName(url.searchParams.get('room'));
    const requirement = resolveRoomAuth(env, room);
    const token = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '') || url.searchParams.get('auth') || '';
    return jsonResponse({
      server: `${getWebSocketProtocol(url)}//${url.host}/sync/ws`,
      auth: requirement.required,
      authorized: !requirement.required || canAccessRoom(env, room, token),
      room,
      roomProtected: hasRoomAuthEntry(env, room),
    });
  }

  static async devices(request, env) {
    const url = new URL(request.url);
    const room = normalizeRoomName(url.searchParams.get('room'));
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/devices');
  }

  static async status(request, env) {
    const url = new URL(request.url);
    const room = normalizeRoomName(url.searchParams.get('room'));
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/status');
  }

  static async bootstrap(request, env) {
    const url = new URL(request.url);
    const room = normalizeRoomName(url.searchParams.get('room'));
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/bootstrap');
  }

  static async pairRequest(request, env) {
    const body = await readJsonBody(request);
    const room = normalizeRoomName(body.room);
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/pair/request');
  }

  static async pairApprove(request, env) {
    const body = await readJsonBody(request);
    const room = normalizeRoomName(body.room);
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/pair/approve');
  }

  static async deviceTrust(request, env) {
    const body = await readJsonBody(request);
    const room = normalizeRoomName(body.room);
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, `/sync/device/${encodeURIComponent(request.params.deviceId || '')}/trust`);
  }

  static async payloadNotice(request, env) {
    const body = await readJsonBody(request);
    const room = normalizeRoomName(body.room);
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;
    return forwardToRoomObject(request, env, access.room, '/sync/payload-notice');
  }

  static async connect(request, env) {
    const url = new URL(request.url);
    const room = normalizeRoomName(url.searchParams.get('room'));
    const access = await validateRequestRoomAccess(request, env, room);
    if (!access.ok) return access.response;

    const upgradeHeader = request.headers.get('Upgrade');
    if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
      return new Response('Expected WebSocket', { status: 400, headers: corsHeaders });
    }

    const durableObject = getSyncRoomObject(env, access.room);
    if (!durableObject) {
      return new Response('Sync WebSocket service unavailable', { status: 503, headers: corsHeaders });
    }
    const internalURL = new URL('https://sync.internal/sync/ws');
    internalURL.search = url.search;
    return durableObject.fetch(new Request(internalURL.toString(), request));
  }
}
