import { hasRoomAuthEntry, normalizeRoomName, resolveRoomAuth } from '../auth';
import { buildSenderDevice, parseUserAgent } from '../utils';
import { debugLog } from '../logger';

function isRoomListEnabled(env) {
  return ['1', 'true', 'yes', 'on'].includes(String(env.ROOM_LIST || '').toLowerCase());
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function nowMillis() {
  return Date.now();
}

function trimString(value) {
  return String(value || '').trim();
}

function generateId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function normalizeSyncDevice(payload = {}) {
  const currentTime = nowMillis();
  const trusted = !!payload.trusted;
  const device = {
    deviceId: trimString(payload.deviceId) || generateId(),
    name: trimString(payload.name) || '未命名设备',
    room: normalizeRoomName(payload.room),
    platform: trimString(payload.platform) || 'unknown',
    clientType: trimString(payload.clientType) || 'unknown',
    trusted,
    createdAt: Number(payload.createdAt || currentTime),
    lastSeenAt: Number(payload.lastSeenAt || currentTime),
    status: trimString(payload.status) || (trusted ? 'trusted' : 'pending'),
    meta: payload.meta && typeof payload.meta === 'object' ? payload.meta : {},
  };
  return device;
}

function normalizePayloadKind(kind) {
  const normalized = trimString(kind).toLowerCase();
  return normalized === 'image' || normalized === 'file' ? normalized : '';
}

function sanitizePayloadURL(raw) {
  const value = trimString(raw);
  if (!value) {
    return { ok: true, value: '' };
  }
  try {
    const parsed = new URL(value, 'https://sync.internal');
    if (parsed.origin === 'https://sync.internal') {
      if (value.startsWith('//')) {
        return { ok: false, value: '' };
      }
      return { ok: true, value };
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return { ok: false, value: '' };
    }
    return { ok: true, value: parsed.toString() };
  } catch {
    return { ok: false, value: '' };
  }
}

async function readJsonBody(request) {
  try {
    return await request.clone().json();
  } catch {
    return {};
  }
}

export class WebSocketRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sessions = new Map();
    this.syncSessions = new Map();
    debugLog(this.env, 'WebSocketRoom 实例创建');
  }

  async fetch(request) {
    const url = new URL(request.url);
    
    debugLog(this.env, `WebSocketRoom fetch: ${url.pathname}`);
    
    // 处理广播消息的内部请求
    if (url.pathname === '/broadcast') {
      try {
        const message = await request.json();
        this.broadcast(message);
        return new Response('OK');
      } catch (error) {
        console.error('广播消息处理错误:', error);
        return new Response('Broadcast Error', { status: 500 });
      }
    }

    if (url.pathname === '/stats') {
      return Response.json(this.getRoomStats());
    }

    if (url.pathname.startsWith('/sync/')) {
      return this.handleSyncRequest(request, url);
    }

    // 处理 WebSocket 升级请求
    const upgradeHeader = request.headers.get('Upgrade');
    if (upgradeHeader && upgradeHeader.toLowerCase() === 'websocket') {
      return this.handleWebSocket(request);
    }

    return new Response('Expected WebSocket', { status: 400 });
  }

  async handleWebSocket(request) {
    try {
      debugLog(this.env, '开始处理 WebSocket 升级');
      
      // 创建 WebSocket 对
      const webSocketPair = new WebSocketPair();
      const [client, server] = Object.values(webSocketPair);
      
      const url = new URL(request.url);
      const sessionId = this.generateSessionId();
      const userAgent = request.headers.get('User-Agent') || '';
      const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
      const room = normalizeRoomName(url.searchParams.get('room'));
      
      debugLog(this.env, `创建 WebSocket 会话: ${sessionId}, room: ${room}, ip: ${ip}`);
      
      const session = {
        webSocket: server,
        sessionId,
        userAgent,
        ip,
        room: room,
        connectedAt: Date.now()
      };

      this.sessions.set(sessionId, session);
      await this.persistSessionPresence(session);

      // 接受 WebSocket 连接
      server.accept();
      
      debugLog(this.env, `WebSocket 连接已建立: ${sessionId}`);
      
      // 设置事件监听器
      server.addEventListener('message', (event) => {
        this.handleMessage(sessionId, event);
      });

      server.addEventListener('close', (event) => {
        this.handleClose(sessionId, event);
      });

      server.addEventListener('error', (event) => {
        this.handleError(sessionId, event);
      });

      // 与 Go 后端保持一致：先发送历史，再发送配置，再同步设备。
      await this.sendHistoryMessages(server, room);
      await this.sendConfigMessage(server, room);
      await this.sendExistingDevices(server, room, sessionId);

      // 广播新设备连接
      this.broadcastDeviceConnect(sessionId, userAgent, room);

      debugLog(this.env, `WebSocket 会话 ${sessionId} 初始化完成`);

      // 返回 WebSocket 响应
      return new Response(null, {
        status: 101,
        webSocket: client,
      });

    } catch (error) {
      console.error('WebSocket 升级失败:', error);
      return new Response('WebSocket Upgrade Failed', { 
        status: 500 
      });
    }
  }

  async sendConfigMessage(webSocket, room) {
    try {
      const fileLimit = parseInt(this.env.FILE_LIMIT) || 104857600;
      const multipartPartSize = fileLimit > 5 * 1024 * 1024
        ? Math.min(fileLimit, 8 * 1024 * 1024)
        : fileLimit + 1;
      const configMessage = {
        event: 'config',
        data: {
          version: 'cloudflare-worker-v1.0.0',
          server: {
            history: parseInt(this.env.HISTORY_LIMIT) || 10,
            prefix: '',
            roomList: isRoomListEnabled(this.env)
          },
          text: {
            limit: parseInt(this.env.TEXT_LIMIT) || 4096
          },
          file: {
            expire: parseInt(this.env.FILE_EXPIRE) || 3600,
            chunk: multipartPartSize,
            limit: fileLimit
          },
          auth: resolveRoomAuth(this.env, room).required
        }
      };
      
      if (webSocket.readyState === WebSocket.OPEN) {
        webSocket.send(JSON.stringify(configMessage));
        debugLog(this.env, `配置消息已发送`);
      }
      
    } catch (error) {
      console.error('发送配置消息失败:', error);
    }
  }

  async sendHistoryMessages(webSocket, room) {
    try {
      debugLog(this.env, `获取房间 ${room} 的历史消息`);
      
      if (!this.env.DB) {
        debugLog(this.env, 'DB binding 不可用，跳过历史消息');
        return;
      }

      if (webSocket.readyState !== WebSocket.OPEN) {
        debugLog(this.env, 'WebSocket 未就绪，跳过历史消息');
        return;
      }

      // 获取历史消息限制，默认为 10
      const historyLimit = parseInt(this.env.HISTORY_LIMIT || '10');
      debugLog(this.env, `历史消息限制: ${historyLimit}`);

      const query = `
        SELECT * FROM (
          SELECT * FROM messages
          WHERE room = ?
          ORDER BY timestamp DESC, id DESC
          LIMIT ?
        ) recent
        ORDER BY timestamp ASC, id ASC
      `;
      const params = [normalizeRoomName(room), historyLimit];
      
      debugLog(this.env, `历史消息查询: ${query}, 参数:`, params, `限制: ${historyLimit}`);
      
      const results = await this.env.DB.prepare(query).bind(...params).all();
      
      if (!results.results || results.results.length === 0) {
        debugLog(this.env, `房间 ${room} 没有历史消息`);
        return;
      }

      debugLog(this.env, `找到 ${results.results.length} 条历史消息 (限制: ${historyLimit})`);

      // 发送历史消息
      for (const row of results.results) {
        if (webSocket.readyState !== WebSocket.OPEN) {
          debugLog(this.env, 'WebSocket 已关闭，停止发送历史消息');
          break;
        }

        const historyMessage = {
          event: 'receive',
          data: {
            id: row.id,
            type: row.type,
            timestamp: row.timestamp,
            room: row.room || 'default',
            senderIP: row.senderIP || 'unknown',
            senderDevice: buildSenderDevice(row.userAgent || 'unknown')
          }
        };

        // 根据消息类型添加相应字段
        if (row.type === 'text') {
          historyMessage.data.content = row.content;
        } else if (row.type === 'file') {
          // 为历史文件消息添加图标
          const FileHandler = await import('../handlers/file.js');
          const fileIcon = FileHandler.FileHandler.getFileTypeIcon(row.name);
          const displayName = `${fileIcon} ${row.name}`;
          
          historyMessage.data.name = displayName;
          historyMessage.data.size = row.size;
          historyMessage.data.uuid = row.uuid;
          historyMessage.data.url = row.url;
          
          // 处理过期时间
          let expireTime = row.expireTime;
          if (expireTime && expireTime.toString().length === 10) {
            expireTime = expireTime * 1000;
          }
          historyMessage.data.expire = expireTime;
          historyMessage.data.cache = row.uuid;
        }
        
        debugLog(this.env, `发送历史消息: ID ${row.id}, 类型 ${row.type}`);
        
        webSocket.send(JSON.stringify(historyMessage));
        
      }
      
      debugLog(this.env, `历史消息发送完成，共发送 ${results.results.length} 条 (限制: ${historyLimit})`);
      
    } catch (error) {
      console.error('发送历史消息失败:', error);
      debugLog(this.env, 'Error details:', error.stack);
    }
  }

  async sendExistingDevices(webSocket, room, excludeSessionId) {
    try {
      // 发送房间内现有设备信息
      const existingDevices = [];
      for (const [sessionId, session] of this.sessions) {
        if (sessionId !== excludeSessionId && session.room === room) {
          const deviceInfo = parseUserAgent(session.userAgent);
          existingDevices.push({
            id: sessionId,
            type: deviceInfo.type,
            device: deviceInfo.device,
            os: deviceInfo.os,
            browser: deviceInfo.browser
          });
        }
      }

      for (const deviceMeta of existingDevices) {
        if (webSocket.readyState === WebSocket.OPEN) {
          webSocket.send(JSON.stringify({
            event: 'connect',
            data: deviceMeta
          }));
        }
      }

      debugLog(this.env, `发送了 ${existingDevices.length} 个现有设备信息`);

    } catch (error) {
      console.error('发送现有设备信息失败:', error);
    }
  }

  handleMessage(sessionId, event) {
    try {
      if (event.data && event.data.trim()) {
        debugLog(this.env, `WebSocket 消息 from ${sessionId}:`, event.data);
      }
    } catch (error) {
      console.error(`处理消息错误 (${sessionId}):`, error);
    }
  }

  handleClose(sessionId, event) {
    debugLog(this.env, `WebSocket 会话关闭: ${sessionId}`);
    
    const session = this.sessions.get(sessionId);
    if (session) {
      this.sessions.delete(sessionId);
      void this.removeSessionPresence(sessionId);
      
      // 广播设备断开连接
      this.broadcast({
        event: 'disconnect',
        data: { id: sessionId }
      }, session.room);
    }
  }

  handleError(sessionId, event) {
    console.error(`WebSocket 错误 (${sessionId}):`, event);
    this.sessions.delete(sessionId);
    void this.removeSessionPresence(sessionId);
  }

  broadcastDeviceConnect(sessionId, userAgent, room) {
    try {
      const deviceInfo = parseUserAgent(userAgent);
      
      const connectMessage = {
        event: 'connect',
        data: {
          id: sessionId,
          type: deviceInfo.type,
          device: deviceInfo.device,
          os: deviceInfo.os,
          browser: deviceInfo.browser
        }
      };
      
      this.broadcast(connectMessage, room, sessionId);
      debugLog(this.env, `设备连接广播: ${sessionId}`);
      
    } catch (error) {
      console.error('广播设备连接失败:', error);
    }
  }

  broadcast(message, room = null, excludeSessionId = null) {
    if (!message || typeof message !== 'object') {
      console.error('无效的广播消息:', message);
      return;
    }

    const messageString = JSON.stringify(message);
    const disconnectedSessions = [];
    const targetRoom = room ? normalizeRoomName(room) : null;
    
    debugLog(this.env, `广播消息给 ${this.sessions.size} 个会话: ${message.event}`);
    
    for (const [sessionId, session] of this.sessions) {
      try {
        if (excludeSessionId && sessionId === excludeSessionId) {
          continue;
        }
        if (targetRoom && normalizeRoomName(session.room) !== targetRoom) {
          continue;
        }
        if (session.webSocket.readyState === WebSocket.OPEN) {
          session.webSocket.send(messageString);
        } else {
          debugLog(this.env, `会话 ${sessionId} 已断开，标记清理`);
          disconnectedSessions.push(sessionId);
        }
      } catch (error) {
        console.error(`广播到会话 ${sessionId} 失败:`, error);
        disconnectedSessions.push(sessionId);
      }
    }
    
    // 清理断开的连接
    for (const sessionId of disconnectedSessions) {
      this.sessions.delete(sessionId);
      void this.removeSessionPresence(sessionId);
    }
    
    debugLog(this.env, `广播完成，清理了 ${disconnectedSessions.length} 个断开的会话`);
  }

  generateSessionId() {
    return Math.random().toString(36).substr(2, 9);
  }

  async persistSessionPresence(session) {
    if (!this.env.DB) {
      return;
    }

    try {
      const connectedAt = Math.floor(session.connectedAt / 1000);
      await this.env.DB.prepare(`
        INSERT OR REPLACE INTO room_presence (sessionId, room, connectedAt, userAgent, updatedAt)
        VALUES (?, ?, ?, ?, ?)
      `).bind(
        session.sessionId,
        normalizeRoomName(session.room),
        connectedAt,
        session.userAgent || '',
        connectedAt,
      ).run();
    } catch (error) {
      console.error(`持久化房间在线状态失败 (${session.sessionId}):`, error);
    }
  }

  async removeSessionPresence(sessionId) {
    if (!this.env.DB) {
      return;
    }

    try {
      await this.env.DB.prepare('DELETE FROM room_presence WHERE sessionId = ?').bind(sessionId).run();
    } catch (error) {
      console.error(`删除房间在线状态失败 (${sessionId}):`, error);
    }
  }

  async handleSyncRequest(request, url) {
    await this.cleanupSyncState();

    if (url.pathname === '/sync/ws') {
      const upgradeHeader = request.headers.get('Upgrade');
      if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
        return new Response('Expected WebSocket', { status: 400 });
      }
      return this.handleSyncWebSocket(request);
    }

    switch (url.pathname) {
      case '/sync/devices':
        return this.handleSyncDevices(url);
      case '/sync/status':
        return this.handleSyncStatus(url);
      case '/sync/bootstrap':
        return this.handleSyncBootstrap(url);
      case '/sync/pair/request':
        return this.handleSyncPairRequest(request);
      case '/sync/pair/approve':
        return this.handleSyncPairApprove(request);
      case '/sync/payload-notice':
        return this.handleSyncPayloadNotice(request);
      default:
        if (url.pathname.startsWith('/sync/device/') && url.pathname.endsWith('/trust')) {
          const parts = url.pathname.split('/').filter(Boolean);
          return this.handleSyncDeviceTrust(request, decodeURIComponent(parts[2] || ''));
        }
        return new Response('Not Found', { status: 404 });
    }
  }

  async getSyncDevices() {
    return (await this.state.storage.get('sync:devices')) || [];
  }

  async putSyncDevices(devices) {
    await this.state.storage.put('sync:devices', devices);
  }

  async getSyncMessages() {
    return (await this.state.storage.get('sync:messages')) || [];
  }

  async putSyncMessages(messages) {
    await this.state.storage.put('sync:messages', messages.slice(-20));
  }

  async getSyncPayloads() {
    return (await this.state.storage.get('sync:payloads')) || [];
  }

  async putSyncPayloads(payloads) {
    await this.state.storage.put('sync:payloads', payloads.slice(-20));
  }

  isSyncDeviceOnline(room, deviceId) {
    const targetRoom = normalizeRoomName(room);
    for (const session of this.syncSessions.values()) {
      if (session.ready && session.room === targetRoom && session.deviceId === deviceId) {
        return true;
      }
    }
    return false;
  }

  sanitizeSyncDevice(device) {
    const normalized = normalizeSyncDevice(device);
    return {
      deviceId: normalized.deviceId,
      name: normalized.name,
      room: normalized.room,
      platform: normalized.platform,
      clientType: normalized.clientType,
      trusted: normalized.trusted,
      createdAt: normalized.createdAt,
      lastSeenAt: normalized.lastSeenAt,
      status: normalized.trusted ? 'trusted' : 'pending',
      meta: normalized.meta || {},
      online: this.isSyncDeviceOnline(normalized.room, normalized.deviceId),
    };
  }

  buildSyncRoomSummary(devices, messages, payloads, room) {
    const targetRoom = normalizeRoomName(room);
    let totalDevices = 0;
    let trustedDevices = 0;
    let pendingDevices = 0;
    let onlineDevices = 0;
    let lastDeviceSeenAt = 0;
    let lastMessageAt = 0;
    let lastPayloadAt = 0;
    let recentMessageCount = 0;
    let recentPayloadCount = 0;

    for (const device of devices) {
      if (normalizeRoomName(device.room) !== targetRoom) continue;
      totalDevices += 1;
      if (device.trusted) trustedDevices += 1;
      else pendingDevices += 1;
      if (this.isSyncDeviceOnline(targetRoom, device.deviceId)) onlineDevices += 1;
      lastDeviceSeenAt = Math.max(lastDeviceSeenAt, Number(device.lastSeenAt || 0));
    }

    for (const message of messages) {
      if (normalizeRoomName(message.room) !== targetRoom) continue;
      recentMessageCount += 1;
      lastMessageAt = Math.max(lastMessageAt, Number(message.createdAt || 0));
    }

    for (const payload of payloads) {
      if (normalizeRoomName(payload.room) !== targetRoom) continue;
      recentPayloadCount += 1;
      lastPayloadAt = Math.max(lastPayloadAt, Number(payload.createdAt || 0));
    }

    return {
      room: targetRoom,
      totalDevices,
      trustedDevices,
      pendingDevices,
      onlineDevices,
      offlineDevices: Math.max(0, totalDevices - onlineDevices),
      recentMessageCount,
      recentPayloadCount,
      lastDeviceSeenAt,
      lastMessageAt,
      lastPayloadAt,
    };
  }

  async getSyncDevice(deviceId, room) {
    const targetRoom = normalizeRoomName(room);
    const targetDeviceId = trimString(deviceId);
    if (!targetDeviceId) return null;
    const devices = await this.getSyncDevices();
    const device = devices.find(item => item.deviceId === targetDeviceId && normalizeRoomName(item.room) === targetRoom);
    return device ? this.sanitizeSyncDevice(device) : null;
  }

  async requestSyncPair(payload, keepExistingTrust = true) {
    const next = normalizeSyncDevice(payload);
    const devices = await this.getSyncDevices();
    const index = devices.findIndex(item => item.deviceId === next.deviceId && normalizeRoomName(item.room) === next.room);
    if (index === -1) {
      next.trusted = false;
      next.status = 'pending';
      devices.push(next);
    } else {
      const existing = normalizeSyncDevice(devices[index]);
      next.createdAt = existing.createdAt;
      next.trusted = keepExistingTrust ? existing.trusted : false;
      next.status = next.trusted ? 'trusted' : 'pending';
      devices[index] = next;
    }
    await this.putSyncDevices(devices);
    return this.sanitizeSyncDevice(index === -1 ? next : devices[index]);
  }

  async updateSyncDeviceTrust(deviceId, room, trusted, name = '') {
    const targetRoom = normalizeRoomName(room);
    const targetDeviceId = trimString(deviceId);
    const devices = await this.getSyncDevices();
    const index = devices.findIndex(item => item.deviceId === targetDeviceId && normalizeRoomName(item.room) === targetRoom);
    if (index === -1) {
      return { ok: false, device: null };
    }
    devices[index] = normalizeSyncDevice(devices[index]);
    devices[index].trusted = !!trusted;
    devices[index].status = devices[index].trusted ? 'trusted' : 'pending';
    devices[index].lastSeenAt = nowMillis();
    if (trimString(name)) {
      devices[index].name = trimString(name);
    }
    await this.putSyncDevices(devices);

    for (const session of this.syncSessions.values()) {
      if (session.room === targetRoom && session.deviceId === targetDeviceId) {
        session.trusted = devices[index].trusted;
      }
    }

    return { ok: true, device: this.sanitizeSyncDevice(devices[index]) };
  }

  async markSyncDeviceOnline(session) {
    const devices = await this.getSyncDevices();
    const index = devices.findIndex(item => item.deviceId === session.deviceId && normalizeRoomName(item.room) === session.room);
    let trusted = false;
    if (index !== -1) {
      devices[index] = normalizeSyncDevice(devices[index]);
      devices[index].lastSeenAt = nowMillis();
      trusted = !!devices[index].trusted;
      await this.putSyncDevices(devices);
    }
    session.trusted = trusted;
    session.ready = true;
    return trusted;
  }

  async markSyncDeviceOffline(sessionId) {
    const session = this.syncSessions.get(sessionId);
    if (!session) return null;
    this.syncSessions.delete(sessionId);

    const devices = await this.getSyncDevices();
    const index = devices.findIndex(item => item.deviceId === session.deviceId && normalizeRoomName(item.room) === session.room);
    if (index !== -1) {
      devices[index] = normalizeSyncDevice(devices[index]);
      devices[index].lastSeenAt = nowMillis();
      await this.putSyncDevices(devices);
    }
    return session;
  }

  getRecentSyncItems(items, room) {
    const targetRoom = normalizeRoomName(room);
    return items.filter(item => normalizeRoomName(item.room) === targetRoom).slice(-20);
  }

  async handleSyncDevices(url) {
    const room = normalizeRoomName(url.searchParams.get('room'));
    const devices = await this.getSyncDevices();
    const messages = await this.getSyncMessages();
    const payloads = await this.getSyncPayloads();
    const visibleDevices = devices
      .filter(device => normalizeRoomName(device.room) === room)
      .map(device => this.sanitizeSyncDevice(device))
      .sort((a, b) => Number(b.lastSeenAt || 0) - Number(a.lastSeenAt || 0));
    return jsonResponse({
      devices: visibleDevices,
      summary: this.buildSyncRoomSummary(devices, messages, payloads, room),
    });
  }

  async handleSyncStatus(url) {
    const room = normalizeRoomName(url.searchParams.get('room'));
    const deviceId = trimString(url.searchParams.get('deviceId'));
    const devices = await this.getSyncDevices();
    const messages = await this.getSyncMessages();
    const payloads = await this.getSyncPayloads();
    const requirement = resolveRoomAuth(this.env, room);
    return jsonResponse({
      room,
      roomProtected: hasRoomAuthEntry(this.env, room),
      authRequired: requirement.required,
      authMode: {
        usesGlobalPassword: !!trimString(this.env.AUTH_PASSWORD),
        usesRoomPassword: !!trimString(requirement.password) && requirement.password !== trimString(this.env.AUTH_PASSWORD),
      },
      currentDevice: deviceId ? await this.getSyncDevice(deviceId, room) : null,
      summary: this.buildSyncRoomSummary(devices, messages, payloads, room),
      recentMessages: this.getRecentSyncItems(messages, room),
      recentPayloads: this.getRecentSyncItems(payloads, room),
      limits: {
        textLimit: parseInt(this.env.TEXT_LIMIT || '40960', 10),
        historyLimit: parseInt(this.env.HISTORY_LIMIT || '50', 10),
      },
      cleanup: {
        messageExpire: parseInt(this.env.SYNC_MESSAGE_EXPIRE || '86400', 10),
        payloadExpire: parseInt(this.env.SYNC_PAYLOAD_EXPIRE || '86400', 10),
        pendingDeviceExpire: parseInt(this.env.SYNC_PENDING_DEVICE_EXPIRE || '604800', 10),
        trustedDeviceExpire: parseInt(this.env.SYNC_TRUSTED_DEVICE_EXPIRE || '2592000', 10),
      },
      serverTime: nowMillis(),
    });
  }

  async handleSyncBootstrap(url) {
    const room = normalizeRoomName(url.searchParams.get('room'));
    const deviceId = trimString(url.searchParams.get('deviceId'));
    const devices = await this.getSyncDevices();
    const messages = await this.getSyncMessages();
    const payloads = await this.getSyncPayloads();
    return jsonResponse({
      device: deviceId ? await this.getSyncDevice(deviceId, room) : null,
      recentMessages: this.getRecentSyncItems(messages, room),
      recentPayloads: this.getRecentSyncItems(payloads, room),
      summary: this.buildSyncRoomSummary(devices, messages, payloads, room),
    });
  }

  async handleSyncPairRequest(request) {
    const body = await readJsonBody(request);
    if (!trimString(body.deviceId)) {
      return jsonResponse({ error: 'Bad Request', message: '缺少 deviceId' }, 400);
    }
    const device = await this.requestSyncPair(body, true);
    this.broadcastSync({
      event: 'deviceState',
      data: { type: device.trusted ? 'trusted' : 'pending', deviceId: device.deviceId, trusted: device.trusted },
    }, device.room, '', false);
    return jsonResponse({ device });
  }

  async handleSyncPairApprove(request) {
    const body = await readJsonBody(request);
    if (!trimString(body.deviceId)) {
      return jsonResponse({ error: 'Bad Request', message: '缺少 deviceId' }, 400);
    }
    const result = await this.updateSyncDeviceTrust(body.deviceId, body.room, true, body.name);
    if (!result.ok) {
      return jsonResponse({ error: 'Not Found', message: '设备不存在' }, 404);
    }
    this.broadcastSync({
      event: 'deviceState',
      data: { type: 'trusted', deviceId: result.device.deviceId, trusted: true },
    }, result.device.room, '', false);
    return jsonResponse({ device: result.device });
  }

  async handleSyncDeviceTrust(request, deviceId) {
    const body = await readJsonBody(request);
    if (typeof body.trusted !== 'boolean') {
      return jsonResponse({ error: 'Bad Request', message: '缺少 trusted' }, 400);
    }
    const result = await this.updateSyncDeviceTrust(deviceId, body.room, body.trusted, body.name);
    if (!result.ok) {
      return jsonResponse({ error: 'Not Found', message: '设备不存在' }, 404);
    }
    this.broadcastSync({
      event: 'deviceState',
      data: { type: 'trusted', deviceId: result.device.deviceId, trusted: result.device.trusted },
    }, result.device.room, '', false);
    return jsonResponse({ device: result.device });
  }

  async handleSyncPayloadNotice(request) {
    const body = await readJsonBody(request);
    const kind = normalizePayloadKind(body.kind);
    if (!kind) {
      return jsonResponse({ error: 'Bad Request', message: 'kind 仅支持 image 或 file' }, 400);
    }
    if (!trimString(body.title)) {
      return jsonResponse({ error: 'Bad Request', message: '缺少 title' }, 400);
    }
    if (!trimString(body.sourceDeviceId)) {
      return jsonResponse({ error: 'Bad Request', message: '缺少 sourceDeviceId' }, 400);
    }
    const actionURL = sanitizePayloadURL(body.actionUrl);
    const downloadURL = sanitizePayloadURL(body.downloadUrl);
    if (!actionURL.ok) {
      return jsonResponse({ error: 'Bad Request', message: 'actionUrl 非法' }, 400);
    }
    if (!downloadURL.ok) {
      return jsonResponse({ error: 'Bad Request', message: 'downloadUrl 非法' }, 400);
    }
    if (!actionURL.value && !downloadURL.value) {
      return jsonResponse({ error: 'Bad Request', message: 'actionUrl 与 downloadUrl 至少提供一个' }, 400);
    }
    const size = Number(body.size ?? 0);
    if (!Number.isFinite(size) || size < 0) {
      return jsonResponse({ error: 'Bad Request', message: 'size 必须是非负数字' }, 400);
    }

    const room = normalizeRoomName(body.room);
    const payloads = await this.getSyncPayloads();
    let payload = payloads.find(item => trimString(item.payloadId) && item.payloadId === body.payloadId);
    if (!payload) {
      payload = {
        payloadId: trimString(body.payloadId) || generateId(),
        sourceDeviceId: trimString(body.sourceDeviceId),
        room,
        kind,
        title: trimString(body.title),
        mime: trimString(body.mime),
        size,
        actionUrl: actionURL.value,
        downloadUrl: downloadURL.value,
        createdAt: Number(body.createdAt || nowMillis()),
      };
      payloads.push(payload);
      await this.putSyncPayloads(payloads);
    }
    this.broadcastSync({ event: 'payloadNotice', data: payload }, room, payload.sourceDeviceId, true);
    return jsonResponse({ payload });
  }

  async handleSyncWebSocket(request) {
    try {
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const sessionId = this.generateSessionId();

      const session = {
        webSocket: server,
        sessionId,
        room,
        deviceId: '',
        trusted: false,
        ready: false,
        connectedAt: nowMillis(),
      };
      this.syncSessions.set(sessionId, session);

      server.accept();
      server.addEventListener('message', event => {
        void this.handleSyncMessage(sessionId, event);
      });
      server.addEventListener('close', () => {
        void this.handleSyncClose(sessionId);
      });
      server.addEventListener('error', () => {
        void this.handleSyncClose(sessionId);
      });

      return new Response(null, { status: 101, webSocket: client });
    } catch (error) {
      console.error('同步 WebSocket 升级失败:', error);
      return new Response('Sync WebSocket Upgrade Failed', { status: 500 });
    }
  }

  async handleSyncMessage(sessionId, event) {
    const session = this.syncSessions.get(sessionId);
    if (!session) return;

    let envelope;
    try {
      envelope = JSON.parse(event.data || '{}');
    } catch {
      this.sendSync(session, { event: 'error', data: { message: '消息格式无效' } });
      return;
    }

    switch (envelope.event) {
      case 'hello':
        await this.handleSyncHello(session, envelope.data || {});
        break;
      case 'clipboardPublish':
        await this.handleSyncClipboardPublish(session, envelope.data || {});
        break;
      default:
        break;
    }
  }

  async handleSyncHello(session, data) {
    await this.cleanupSyncState();

    const helloRoom = normalizeRoomName(data.room);
    if (helloRoom !== session.room) {
      this.sendSync(session, { event: 'forbidden', data: { message: '房间不匹配' } });
      session.webSocket.close(1008, 'room mismatch');
      return;
    }
    if (!trimString(data.deviceId)) {
      this.sendSync(session, { event: 'error', data: { message: '缺少 deviceId' } });
      return;
    }

    const existing = await this.getSyncDevice(data.deviceId, helloRoom);
    const device = await this.requestSyncPair({
      deviceId: data.deviceId,
      room: helloRoom,
      name: data.name,
      platform: data.platform,
      clientType: data.clientType,
      meta: data.meta,
    }, true);

    session.room = helloRoom;
    session.deviceId = device.deviceId;
    await this.markSyncDeviceOnline(session);
    const currentDevice = await this.getSyncDevice(device.deviceId, helloRoom);
    const messages = await this.getSyncMessages();
    const payloads = await this.getSyncPayloads();

    this.sendSync(session, {
      event: 'helloAck',
      data: {
        device: currentDevice || device,
        recentMessages: this.getRecentSyncItems(messages, helloRoom),
        recentPayloads: this.getRecentSyncItems(payloads, helloRoom),
      },
    });

    this.broadcastSync({
      event: 'deviceState',
      data: {
        type: existing ? 'online' : 'pending',
        deviceId: device.deviceId,
        trusted: !!(currentDevice || device).trusted,
      },
    }, helloRoom, '', false);
  }

  async handleSyncClipboardPublish(session, data) {
    const messageId = trimString(data.messageId);
    if (!session.ready || !session.trusted) {
      this.sendSync(session, {
        event: 'clipboardAck',
        data: { messageId, status: 'rejected', reason: 'device_not_trusted' },
      });
      return;
    }

    const text = trimString(data.text);
    if (!text) return;

    const textLimit = parseInt(this.env.TEXT_LIMIT || '40960', 10);
    if (textLimit > 0 && text.length > textLimit) {
      this.sendSync(session, {
        event: 'clipboardAck',
        data: { messageId, status: 'rejected', reason: 'text_too_long' },
      });
      return;
    }

    const messages = await this.getSyncMessages();
    if (messageId && messages.some(item => item.messageId === messageId)) {
      this.sendSync(session, { event: 'clipboardAck', data: { messageId, status: 'duplicate' } });
      return;
    }

    const record = {
      messageId: messageId || generateId(),
      sourceDeviceId: session.deviceId,
      room: session.room,
      mime: 'text/plain',
      text,
      createdAt: Number(data.createdAt || nowMillis()),
    };
    messages.push(record);
    await this.putSyncMessages(messages);

    this.broadcastSync({ event: 'clipboardSync', data: record }, session.room, session.deviceId, true);
    this.sendSync(session, {
      event: 'clipboardAck',
      data: { messageId: record.messageId, status: 'ok', serverAt: nowMillis() },
    });
  }

  async handleSyncClose(sessionId) {
    const session = await this.markSyncDeviceOffline(sessionId);
    if (!session || !session.ready) return;
    this.broadcastSync({
      event: 'deviceState',
      data: { type: 'offline', deviceId: session.deviceId },
    }, session.room, '', false);
  }

  sendSync(session, message) {
    try {
      if (session?.webSocket?.readyState === WebSocket.OPEN) {
        session.webSocket.send(JSON.stringify(message));
      }
    } catch (error) {
      console.error('发送同步消息失败:', error);
    }
  }

  broadcastSync(message, room, sourceDeviceId = '', trustedOnly = false) {
    const targetRoom = normalizeRoomName(room);
    for (const session of this.syncSessions.values()) {
      if (!session.ready || session.room !== targetRoom) continue;
      if (sourceDeviceId && session.deviceId === sourceDeviceId) continue;
      if (trustedOnly && !session.trusted) continue;
      this.sendSync(session, message);
    }
  }

  async cleanupSyncState() {
    const currentTime = nowMillis();
    const messageExpireMillis = parseInt(this.env.SYNC_MESSAGE_EXPIRE || '86400', 10) * 1000;
    const payloadExpireMillis = parseInt(this.env.SYNC_PAYLOAD_EXPIRE || '86400', 10) * 1000;
    const pendingDeviceExpireMillis = parseInt(this.env.SYNC_PENDING_DEVICE_EXPIRE || '604800', 10) * 1000;
    const trustedDeviceExpireMillis = parseInt(this.env.SYNC_TRUSTED_DEVICE_EXPIRE || '2592000', 10) * 1000;

    const devices = await this.getSyncDevices();
    const messages = await this.getSyncMessages();
    const payloads = await this.getSyncPayloads();

    const nextMessages = messageExpireMillis > 0
      ? messages.filter(message => !message.createdAt || currentTime - Number(message.createdAt) <= messageExpireMillis)
      : messages;
    const nextPayloads = payloadExpireMillis > 0
      ? payloads.filter(payload => !payload.createdAt || currentTime - Number(payload.createdAt) <= payloadExpireMillis)
      : payloads;
    const nextDevices = devices.filter(device => {
      const normalized = normalizeSyncDevice(device);
      if (this.isSyncDeviceOnline(normalized.room, normalized.deviceId)) {
        return true;
      }
      const lastActiveAt = Number(normalized.lastSeenAt || normalized.createdAt || 0);
      const expireMillis = normalized.trusted ? trustedDeviceExpireMillis : pendingDeviceExpireMillis;
      return !(expireMillis > 0 && lastActiveAt > 0 && currentTime - lastActiveAt > expireMillis);
    });

    if (nextMessages.length !== messages.length) await this.putSyncMessages(nextMessages);
    if (nextPayloads.length !== payloads.length) await this.putSyncPayloads(nextPayloads);
    if (nextDevices.length !== devices.length) await this.putSyncDevices(nextDevices);
  }

  getRoomStats() {
    let latestConnectedAt = 0;

    for (const session of this.sessions.values()) {
      if (session.connectedAt > latestConnectedAt) {
        latestConnectedAt = session.connectedAt;
      }
    }

    return {
      deviceCount: this.sessions.size,
      isActive: this.sessions.size > 0,
      lastActive: latestConnectedAt ? Math.floor(latestConnectedAt / 1000) : 0,
    };
  }
}
