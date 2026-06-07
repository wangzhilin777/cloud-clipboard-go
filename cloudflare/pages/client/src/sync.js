import { config } from './config';

const SYNC_DEVICE_ID_KEY = 'sync.deviceId';
const SYNC_DEVICE_NAME_KEY = 'sync.deviceName';
const SYNC_ENABLE_RECEIVE_KEY = 'sync.enableReceive';
const SYNC_ENABLE_SEND_KEY = 'sync.enableSend';

const generateDeviceId = () => {
    if (crypto?.randomUUID) return crypto.randomUUID();
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const getDefaultDeviceName = () => {
    const ua = navigator.userAgent || 'Web';
    if (ua.includes('Windows')) return '网页端（Windows）';
    if (ua.includes('Android')) return '网页端（Android）';
    if (ua.includes('Mac')) return '网页端（macOS）';
    return '网页端';
};

function buildSyncServerPath() {
    return config.wsBaseURL ? 'sync/server' : 'sync/server';
}

function buildSyncApiPath(path) {
    const normalized = String(path || '').replace(/^\/+/, '');
    return config.apiBaseURL ? `sync/${normalized}` : `api/sync/${normalized}`;
}

function buildSyncWebSocketURL(serverURL, room, roomToken) {
    let wsUrl;
    if (config.wsBaseURL) {
        const base = config.wsBaseURL.replace(/\/+$/, '');
        wsUrl = new URL(`${base}/sync/ws`);
    } else {
        wsUrl = new URL(serverURL);
        wsUrl.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        wsUrl.port = location.port;
    }

    wsUrl.protocol = wsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    wsUrl.searchParams.set('room', room);
    if (roomToken) {
        wsUrl.searchParams.set('auth', roomToken);
    }
    return wsUrl;
}

export default {
    data() {
        return {
            sync: {
                websocket: null,
                connecting: false,
                deviceId: localStorage.getItem(SYNC_DEVICE_ID_KEY) || generateDeviceId(),
                deviceName: localStorage.getItem(SYNC_DEVICE_NAME_KEY) || getDefaultDeviceName(),
                device: null,
                devices: [],
                status: 'idle',
                logs: [],
                summary: null,
                statusInfo: null,
                lastSentText: '',
                lastAppliedText: '',
                lastAppliedAt: 0,
                lastClipboardReadDeniedAt: 0,
                poller: null,
                refreshTimer: null,
                pushRefreshTimer: null,
                enableReceive: localStorage.getItem(SYNC_ENABLE_RECEIVE_KEY) !== 'false',
                enableSend: localStorage.getItem(SYNC_ENABLE_SEND_KEY) !== 'false',
                pendingRemoteText: '',
                pendingRemoteTextAt: 0,
            },
        };
    },
    methods: {
        syncBuildApiPath(path) {
            return buildSyncApiPath(path);
        },
        syncLog(message) {
            this.sync.logs.unshift({
                id: `${Date.now()}-${Math.random()}`,
                message,
                at: new Date().toLocaleTimeString(),
            });
            this.sync.logs = this.sync.logs.slice(0, 20);
        },
        syncMessageToTimelineItem(message) {
            const text = `${message?.text || ''}`.trim();
            if (!text) return null;

            const createdAt = Number(message?.createdAt || Date.now());
            const messageId = message?.messageId || `${message?.sourceDeviceId || 'unknown'}-${createdAt}`;
            return {
                id: `sync-${messageId}`,
                type: 'text',
                content: text,
                timestamp: Math.floor(createdAt / 1000),
                syncOnly: true,
                syncMessageId: messageId,
                syncSourceDeviceId: message?.sourceDeviceId || '',
            };
        },
        syncMergeRecentMessages(messages = [], options = {}) {
            if (!Array.isArray(this.$root.received)) return;

            const syncItems = messages
                .map(message => this.syncMessageToTimelineItem(message))
                .filter(Boolean);
            const dedupedSyncItems = [];
            const seenIds = new Set();
            syncItems.forEach(item => {
                if (seenIds.has(item.id)) return;
                seenIds.add(item.id);
                dedupedSyncItems.push(item);
            });

            const existingSyncItems = options.replaceExistingSync
                ? []
                : this.$root.received.filter(item => item.syncOnly);
            const keepExisting = this.$root.received.filter(item => !item.syncOnly);
            const mergedSyncItems = [];
            const mergedSyncIds = new Set();
            [...dedupedSyncItems, ...existingSyncItems].forEach(item => {
                if (mergedSyncIds.has(item.id)) return;
                mergedSyncIds.add(item.id);
                mergedSyncItems.push(item);
            });
            const merged = [...mergedSyncItems, ...keepExisting].sort((a, b) => Number(b?.timestamp || 0) - Number(a?.timestamp || 0));
            this.$root.received.splice(0, this.$root.received.length, ...merged);
        },
        async syncLoadDevices() {
            try {
                const response = await this.$http.get(buildSyncApiPath('devices'), {
                    params: { room: this.room || '' },
                });
                this.sync.devices = response.data.devices || [];
                this.sync.summary = response.data.summary || null;
                const current = this.sync.devices.find(device => device.deviceId === this.sync.deviceId);
                if (current) {
                    this.sync.device = current;
                    this.sync.status = current.trusted ? 'trusted' : 'pending';
                }
            } catch (error) {
                this.syncLog(`加载同步设备失败：${error.response?.data?.message || error.message}`);
            }
        },
        async syncRefreshBootstrap() {
            try {
                const response = await this.$http.get(buildSyncApiPath('bootstrap'), {
                    params: {
                        room: this.room || '',
                        deviceId: this.sync.deviceId,
                    },
                });
                if (response.data.device) {
                    this.sync.device = response.data.device;
                    this.sync.status = response.data.device.trusted ? 'trusted' : 'pending';
                }
                this.sync.summary = response.data.summary || this.sync.summary;
                this.syncMergeRecentMessages(response.data.recentMessages || [], { replaceExistingSync: true });
            } catch (error) {
                this.syncLog(`刷新同步状态失败：${error.response?.data?.message || error.message}`);
            }
        },
        async syncLoadStatus() {
            try {
                const response = await this.$http.get(buildSyncApiPath('status'), {
                    params: {
                        room: this.room || '',
                        deviceId: this.sync.deviceId,
                    },
                });
                this.sync.statusInfo = response.data || null;
                this.sync.summary = response.data?.summary || this.sync.summary;
                this.syncMergeRecentMessages(response.data?.recentMessages || [], { replaceExistingSync: true });
                if (response.data?.currentDevice) {
                    this.sync.device = response.data.currentDevice;
                    this.sync.status = response.data.currentDevice.trusted ? 'trusted' : 'pending';
                }
            } catch (error) {
                this.syncLog(`加载同步诊断失败：${error.response?.data?.message || error.message}`);
            }
        },
        async syncRefreshAll(options = {}) {
            const {
                includeDevices = true,
                includeBootstrap = true,
                includeStatus = true,
            } = options;

            const tasks = [];
            if (includeDevices) tasks.push(this.syncLoadDevices());
            if (includeBootstrap) tasks.push(this.syncRefreshBootstrap());
            if (includeStatus) tasks.push(this.syncLoadStatus());
            await Promise.all(tasks);
        },
        syncRemoveTimelineMessage(messageId) {
            if (!Array.isArray(this.$root.received)) return;
            const next = this.$root.received.filter(item => item.syncMessageId !== messageId);
            this.$root.received.splice(0, this.$root.received.length, ...next);
        },
        syncClearTimelineMessages() {
            if (!Array.isArray(this.$root.received)) return;
            const next = this.$root.received.filter(item => !item.syncOnly);
            this.$root.received.splice(0, this.$root.received.length, ...next);
        },
        async syncDeleteMessage(messageId) {
            if (!messageId) return;
            await this.$http.delete(buildSyncApiPath(`message/${encodeURIComponent(messageId)}`), {
                params: { room: this.room || '' },
            });
            this.syncRemoveTimelineMessage(messageId);
            await this.syncRefreshAll({ includeDevices: false });
        },
        async syncClearMessages() {
            await this.$http.delete(buildSyncApiPath('messages'), {
                params: { room: this.room || '' },
            });
            this.syncClearTimelineMessages();
            await this.syncRefreshAll({ includeDevices: false });
        },
        async syncConnect() {
            if (this.sync.connecting || this.sync.websocket) return;

            this.sync.connecting = true;
            this.sync.status = 'connecting';
            try {
                const room = this.room || '';
                const response = await this.$http.get(buildSyncServerPath(), {
                    params: { room },
                });
                if (response.data.auth && response.data.authorized === false) {
                    const resolvedToken = this.resolveAuthTokenForRoom
                        ? await this.resolveAuthTokenForRoom(room, { interactive: true })
                        : null;
                    if (resolvedToken === null) {
                        this.sync.status = 'forbidden';
                        this.syncLog('同步房间认证失败');
                        return;
                    }
                }

                const roomToken = this.getAuthTokenForRoom ? this.getAuthTokenForRoom(room) : '';
                const wsUrl = buildSyncWebSocketURL(response.data.server, room, roomToken);
                const ws = new WebSocket(wsUrl);
                ws.onopen = () => {
                    ws.send(JSON.stringify({
                        event: 'hello',
                        data: {
                            deviceId: this.sync.deviceId,
                            name: this.sync.deviceName,
                            room,
                            platform: 'web',
                            clientType: 'browser',
                            meta: { userAgent: navigator.userAgent },
                        },
                    }));
                };
                ws.onmessage = async event => {
                    try {
                        const payload = JSON.parse(event.data);
                        switch (payload.event) {
                            case 'helloAck':
                                this.sync.device = payload.data.device;
                                this.sync.status = payload.data.device.trusted ? 'trusted' : 'pending';
                                this.syncLog(payload.data.device.trusted ? '同步设备已连接' : '同步设备等待批准');
                                await this.syncRefreshAll({ includeBootstrap: false });
                                this.syncStartClipboardPolling();
                                break;
                            case 'clipboardSync':
                                if (!this.sync.enableReceive) return;
                                if (payload.data?.sourceDeviceId === this.sync.deviceId) return;
                                this.syncMergeRecentMessages([{
                                    messageId: payload.data?.messageId,
                                    sourceDeviceId: payload.data?.sourceDeviceId,
                                    text: payload.data?.text || '',
                                    createdAt: payload.data?.createdAt || Date.now(),
                                }]);
                                await this.syncApplyRemoteClipboard(payload.data.text || '');
                                break;
                            case 'clipboardAck':
                                if (payload.data.status === 'ok') this.syncLog('文本同步成功');
                                else if (payload.data.status === 'duplicate') this.syncLog('检测到重复文本，已忽略');
                                else this.syncLog(`文本同步被拒绝：${payload.data.reason || payload.data.status}`);
                                break;
                            case 'deviceState':
                                await this.syncRefreshAll();
                                break;
                            case 'syncMessageDeleted':
                                this.syncRemoveTimelineMessage(payload.data?.messageId || '');
                                await this.syncRefreshAll({ includeDevices: false });
                                break;
                            case 'syncMessagesCleared':
                                this.syncClearTimelineMessages();
                                await this.syncRefreshAll({ includeDevices: false });
                                break;
                            case 'forbidden':
                                this.sync.status = 'forbidden';
                                this.syncLog(payload.data?.message || '同步认证失败');
                                break;
                        }
                    } catch {}
                };
                ws.onclose = () => {
                    this.syncStopClipboardPolling();
                    this.sync.websocket = null;
                    this.sync.connecting = false;
                    this.sync.status = 'disconnected';
                };
                ws.onerror = () => {
                    this.syncLog('同步连接失败');
                };
                this.sync.websocket = ws;
            } catch (error) {
                this.sync.status = 'failed';
                this.syncLog(`建立同步连接失败：${error.response?.data?.message || error.message}`);
            } finally {
                this.sync.connecting = false;
            }
        },
        syncDisconnect() {
            this.syncStopClipboardPolling();
            this.syncMergeRecentMessages([], { replaceExistingSync: true });
            if (this.sync.websocket) {
                this.sync.websocket.close();
                this.sync.websocket = null;
            }
        },
        syncStartClipboardPolling() {
            if (this.sync.poller || !navigator.clipboard?.readText) {
                if (!navigator.clipboard?.readText) this.syncLog('当前浏览器不支持自动读取本地剪贴板');
                return;
            }
            this.sync.poller = setInterval(async () => {
                if (!this.sync.enableSend || !this.sync.device?.trusted || !this.sync.websocket || document.visibilityState !== 'visible') return;
                try {
                    const text = await navigator.clipboard.readText();
                    if (!text || text === this.sync.lastSentText) return;
                    if (text === this.sync.lastAppliedText && Date.now() - this.sync.lastAppliedAt < 5000) return;
                    this.sync.lastSentText = text;
                    this.sync.websocket.send(JSON.stringify({
                        event: 'clipboardPublish',
                        data: {
                            messageId: crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`,
                            text,
                            createdAt: Date.now(),
                        },
                    }));
                } catch (error) {
                    if (Date.now() - this.sync.lastClipboardReadDeniedAt > 15000) {
                        this.sync.lastClipboardReadDeniedAt = Date.now();
                        this.syncLog(`读取本地剪贴板失败，将保留手动复制退化：${error.message}`);
                    }
                }
            }, 1500);
        },
        syncStartRefreshTimer() {
            if (this.sync.refreshTimer) return;
            this.sync.refreshTimer = setInterval(async () => {
                if (!this.sync.deviceId) return;
                await this.syncRefreshAll();
            }, 4000);
        },
        syncQueuePushRefresh() {
            if (!this.sync.deviceId || this.sync.pushRefreshTimer) return;
            this.sync.pushRefreshTimer = setTimeout(async () => {
                this.sync.pushRefreshTimer = null;
                await this.syncRefreshAll();
            }, 200);
        },
        syncStopClipboardPolling() {
            if (this.sync.poller) {
                clearInterval(this.sync.poller);
                this.sync.poller = null;
            }
        },
        syncStopRefreshTimer() {
            if (this.sync.refreshTimer) {
                clearInterval(this.sync.refreshTimer);
                this.sync.refreshTimer = null;
            }
            if (this.sync.pushRefreshTimer) {
                clearTimeout(this.sync.pushRefreshTimer);
                this.sync.pushRefreshTimer = null;
            }
        },
        async syncApplyRemoteClipboard(text) {
            this.sync.lastAppliedText = text;
            this.sync.lastAppliedAt = Date.now();
            this.sync.lastSentText = text;
            this.sync.pendingRemoteText = text;
            this.sync.pendingRemoteTextAt = Date.now();
            this.syncLog('收到远端文本同步');
            try {
                if (navigator.clipboard?.writeText) {
                    await navigator.clipboard.writeText(text);
                    this.syncLog('已写入本地剪贴板');
                } else {
                    throw new Error('当前浏览器不支持剪贴板写入');
                }
            } catch (error) {
                this.syncLog(`写入本地剪贴板失败，已保留一键复制入口：${error.message}`);
            }
        },
        async syncCopyPendingText() {
            if (!this.sync.pendingRemoteText) return;
            await navigator.clipboard.writeText(this.sync.pendingRemoteText);
            this.syncLog('已手动复制最近一次远端文本');
        },
        async syncApproveDevice(deviceId, name) {
            await this.$http.post(buildSyncApiPath('pair/approve'), {
                deviceId,
                room: this.room || '',
                name,
            });
            await this.syncRefreshAll();
        },
        async syncToggleTrust(device) {
            await this.$http.post(buildSyncApiPath(`device/${device.deviceId}/trust`), {
                room: this.room || '',
                trusted: !device.trusted,
                name: device.name,
            });
            await this.syncRefreshAll();
        },
    },
    watch: {
        room() {
            this.syncDisconnect();
            this.syncConnect();
        },
        'sync.deviceName'(value) {
            localStorage.setItem(SYNC_DEVICE_NAME_KEY, value);
        },
        'sync.enableReceive'(value) {
            localStorage.setItem(SYNC_ENABLE_RECEIVE_KEY, value);
        },
        'sync.enableSend'(value) {
            localStorage.setItem(SYNC_ENABLE_SEND_KEY, value);
        },
    },
    mounted() {
        localStorage.setItem(SYNC_DEVICE_ID_KEY, this.sync.deviceId);
        this.syncStartRefreshTimer();
        this.syncConnect();
    },
    beforeDestroy() {
        this.syncStopRefreshTimer();
        this.syncDisconnect();
    },
};
