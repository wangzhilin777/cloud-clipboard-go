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
                lastSentText: '',
                lastAppliedText: '',
                lastAppliedAt: 0,
                lastClipboardReadDeniedAt: 0,
                poller: null,
                enableReceive: localStorage.getItem(SYNC_ENABLE_RECEIVE_KEY) !== 'false',
                enableSend: localStorage.getItem(SYNC_ENABLE_SEND_KEY) !== 'false',
                pendingRemoteText: '',
                pendingRemoteTextAt: 0,
            },
        };
    },
    methods: {
        syncLog(message) {
            this.sync.logs.unshift({
                id: `${Date.now()}-${Math.random()}`,
                message,
                at: new Date().toLocaleTimeString(),
            });
            this.sync.logs = this.sync.logs.slice(0, 20);
        },
        async syncLoadDevices() {
            try {
                const response = await this.$http.get('api/sync/devices', {
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
                const response = await this.$http.get('api/sync/bootstrap', {
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
            } catch (error) {
                this.syncLog(`刷新同步状态失败：${error.response?.data?.message || error.message}`);
            }
        },
        async syncConnect() {
            if (this.sync.connecting || this.sync.websocket) return;

            this.sync.connecting = true;
            this.sync.status = 'connecting';
            try {
                const room = this.room || '';
                const response = await this.$http.get('sync/server', {
                    params: { room },
                });
                if (response.data.auth && response.data.authorized === false) {
                    this.sync.status = 'forbidden';
                    this.syncLog('同步房间认证失败');
                    return;
                }

                const wsUrl = new URL(response.data.server);
                wsUrl.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
                wsUrl.port = location.port;
                wsUrl.searchParams.set('room', room);
                const roomToken = this.getAuthTokenForRoom ? this.getAuthTokenForRoom(room) : '';
                if (roomToken) {
                    wsUrl.searchParams.set('auth', roomToken);
                }

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
                            meta: {
                                userAgent: navigator.userAgent,
                            },
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
                                await this.syncLoadDevices();
                                this.syncStartClipboardPolling();
                                break;
                            case 'clipboardSync':
                                if (!this.sync.enableReceive) return;
                                await this.syncApplyRemoteClipboard(payload.data.text || '');
                                break;
                            case 'clipboardAck':
                                if (payload.data.status === 'ok') {
                                    this.syncLog('文本同步成功');
                                } else if (payload.data.status === 'duplicate') {
                                    this.syncLog('检测到重复文本，已忽略');
                                } else {
                                    this.syncLog(`文本同步被拒绝：${payload.data.reason || payload.data.status}`);
                                }
                                break;
                            case 'deviceState':
                                await this.syncLoadDevices();
                                await this.syncRefreshBootstrap();
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
            if (this.sync.websocket) {
                this.sync.websocket.close();
                this.sync.websocket = null;
            }
        },
        syncStartClipboardPolling() {
            if (this.sync.poller || !navigator.clipboard?.readText) {
                if (!navigator.clipboard?.readText) {
                    this.syncLog('当前浏览器不支持自动读取本地剪贴板');
                }
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
        syncStopClipboardPolling() {
            if (this.sync.poller) {
                clearInterval(this.sync.poller);
                this.sync.poller = null;
            }
        },
        async syncApplyRemoteClipboard(text) {
            this.sync.lastAppliedText = text;
            this.sync.lastAppliedAt = Date.now();
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
            await this.$http.post('api/sync/pair/approve', {
                deviceId,
                room: this.room || '',
                name,
            });
            await this.syncLoadDevices();
            await this.syncRefreshBootstrap();
        },
        async syncToggleTrust(device) {
            await this.$http.post(`api/sync/device/${device.deviceId}/trust`, {
                room: this.room || '',
                trusted: !device.trusted,
                name: device.name,
            });
            await this.syncLoadDevices();
            await this.syncRefreshBootstrap();
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
        this.syncConnect();
    },
    beforeDestroy() {
        this.syncDisconnect();
    },
};
