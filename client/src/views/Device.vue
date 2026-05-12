<template>
    <v-container>
        <v-responsive max-width="900" class="mx-auto">
            <div class="headline text--primary my-4">在线浏览器设备</div>
            <template v-if="$root.websocket">
                {{ $t('devicesConnected', { count: $root.device.length, desktop: desktopDeviceCount, mobile: mobileDeviceCount }) }}
                <v-divider class="my-2"></v-divider>
            </template>
            <template v-else>
                {{ $t('notConnectedToServer') }}
            </template>

            <v-list rounded two-line class="mb-6">
                <v-list-item-group color="primary">
                    <v-list-item v-for="item in $root.device" :key="item.id">
                        <v-list-item-avatar tile>
                            <template v-if="item.type === 'desktop'">
                                <v-icon v-if="item.os.split(' ').shift() === 'Windows'">{{mdiMicrosoftWindows}}</v-icon>
                                <v-icon v-else-if="item.os.split(' ').shift() === 'GNU/Linux'">{{mdiLinux}}</v-icon>
                                <v-icon v-else-if="item.os.split(' ').shift() === 'Mac'">{{mdiApple}}</v-icon>
                                <v-icon v-else>{{mdiLaptop}}</v-icon>
                            </template>
                            <template v-else-if="item.type === 'smartphone' || item.type === 'mobile' || item.type === 'tablet'">
                                <v-icon v-if="item.os.split(' ').shift() === 'Android'">{{mdiAndroid}}</v-icon>
                                <v-icon v-else-if="item.os.split(' ').shift() === 'iOS'">{{mdiAppleIos}}</v-icon>
                                <v-icon v-else>{{mdiTabletCellphone}}</v-icon>
                            </template>
                            <v-icon v-else>{{mdiDevices}}</v-icon>
                        </v-list-item-avatar>
                        <v-list-item-content>
                            <v-list-item-title>{{
                                item.type === 'desktop' ? $t('desktopDevice') : (
                                    (item.type === 'smartphone' || item.type === 'mobile' || item.type === 'tablet') ? $t('mobileDevice') : $t('otherDevice')
                                )
                            }}</v-list-item-title>
                            <v-list-item-subtitle>{{item.os}} ({{item.browser}})</v-list-item-subtitle>
                        </v-list-item-content>
                    </v-list-item>
                </v-list-item-group>
            </v-list>

            <div class="headline text--primary my-4">同步设备管理</div>

            <v-alert
                v-if="$root.sync.pendingRemoteText"
                outlined
                dense
                type="info"
                class="mb-4"
            >
                <div class="font-weight-medium mb-1">最近一次远端文本</div>
                <div class="text-body-2 mb-2 sync-preview">{{ $root.sync.pendingRemoteText }}</div>
                <v-btn small color="primary" @click="$root.syncCopyPendingText()">一键复制</v-btn>
            </v-alert>

            <v-card class="mb-4">
                <v-card-title>当前网页同步设备</v-card-title>
                <v-card-text>
                    <v-text-field v-model="$root.sync.deviceName" label="设备名称"></v-text-field>
                    <v-switch v-model="$root.sync.enableSend" label="允许监听本地文本剪贴板"></v-switch>
                    <v-switch v-model="$root.sync.enableReceive" label="允许自动写入本地剪贴板"></v-switch>
                    <div class="caption text--secondary mb-3">
                        当前状态：<strong>{{ statusText }}</strong>
                    </div>
                    <v-btn color="primary" @click="refreshSyncDevices">刷新同步设备列表</v-btn>
                </v-card-text>
            </v-card>

            <v-card class="mb-4" v-if="$root.sync.summary">
                <v-card-title>同步状态摘要</v-card-title>
                <v-card-text>
                    <div class="text-body-2 mb-1">设备：{{ $root.sync.summary.totalDevices }} 台，在线 {{ $root.sync.summary.onlineDevices }} 台，待批准 {{ $root.sync.summary.pendingDevices }} 台</div>
                    <div class="text-body-2 mb-1">最近文本：{{ formatSyncTime($root.sync.summary.lastMessageAt) }}，最近通知：{{ formatSyncTime($root.sync.summary.lastPayloadAt) }}</div>
                    <div class="text-body-2">同步历史：文本 {{ $root.sync.summary.recentMessageCount }} 条，通知 {{ $root.sync.summary.recentPayloadCount }} 条</div>
                </v-card-text>
            </v-card>

            <v-card class="mb-4" v-if="$root.sync.statusInfo">
                <v-card-title>同步诊断</v-card-title>
                <v-card-text>
                    <div class="text-body-2 mb-1">房间：{{ $root.sync.statusInfo.room }} / {{ authModeText }}</div>
                    <div class="text-body-2 mb-1">当前设备：{{ currentDeviceText }}</div>
                    <div class="text-body-2 mb-1">文本限制：{{ $root.sync.statusInfo.limits?.textLimit || 0 }}，历史上限：{{ $root.sync.statusInfo.limits?.historyLimit || 0 }}</div>
                    <div class="text-body-2">状态清理：{{ cleanupText }}</div>
                </v-card-text>
            </v-card>

            <v-list rounded two-line>
                <v-list-item v-for="item in $root.sync.devices" :key="`${item.room}-${item.deviceId}`">
                    <v-list-item-avatar tile>
                        <v-icon>{{ iconFor(item.platform) }}</v-icon>
                    </v-list-item-avatar>
                    <v-list-item-content>
                        <v-list-item-title>{{ item.name }}</v-list-item-title>
                        <v-list-item-subtitle>
                            {{ platformLabel(item) }} / {{ item.clientType }} / {{ item.online ? '在线' : '离线' }} / {{ item.trusted ? '已信任' : '待批准' }}
                        </v-list-item-subtitle>
                    </v-list-item-content>
                    <v-list-item-action>
                        <v-btn
                            v-if="!item.trusted"
                            color="primary"
                            small
                            @click="$root.syncApproveDevice(item.deviceId, item.name)"
                        >批准</v-btn>
                        <v-btn
                            v-else
                            text
                            small
                            @click="$root.syncToggleTrust(item)"
                        >取消信任</v-btn>
                    </v-list-item-action>
                </v-list-item>
            </v-list>

            <v-card class="mt-4">
                <v-card-title>最近同步日志</v-card-title>
                <v-list dense>
                    <v-list-item v-for="entry in $root.sync.logs" :key="entry.id">
                        <v-list-item-content>
                            <v-list-item-title>{{ entry.message }}</v-list-item-title>
                            <v-list-item-subtitle>{{ entry.at }}</v-list-item-subtitle>
                        </v-list-item-content>
                    </v-list-item>
                </v-list>
            </v-card>
        </v-responsive>
    </v-container>
</template>

<script>
import {
    mdiLaptop,
    mdiMicrosoftWindows,
    mdiApple,
    mdiLinux,
    mdiTabletCellphone,
    mdiAndroid,
    mdiAppleIos,
    mdiDevices,
} from '@mdi/js';

export default {
    data() {
        return {
            mdiLaptop,
            mdiMicrosoftWindows,
            mdiApple,
            mdiLinux,
            mdiTabletCellphone,
            mdiAndroid,
            mdiAppleIos,
            mdiDevices,
        };
    },
    computed: {
        desktopDeviceCount() {
            return this.$root.device.filter(e => e.type === 'desktop').length;
        },
        mobileDeviceCount() {
            return this.$root.device.filter(e => (e.type === 'smartphone' || e.type === 'tablet')).length;
        },
        statusText() {
            switch (this.$root.sync.status) {
                case 'trusted':
                    return '已信任';
                case 'pending':
                    return '等待批准';
                case 'connecting':
                    return '连接中';
                case 'forbidden':
                    return '房间认证失败';
                case 'failed':
                    return '连接失败';
                default:
                    return '未连接';
            }
        },
        authModeText() {
            const info = this.$root.sync.statusInfo;
            if (!info) return '未知';
            const usesGlobal = !!info.authMode?.usesGlobalPassword;
            const usesRoom = !!info.authMode?.usesRoomPassword;
            if (!info.authRequired) return '当前房间无需密码';
            if (usesGlobal && usesRoom) return '支持全局密码 + 房间密码';
            if (usesRoom) return '仅房间密码';
            if (usesGlobal) return '仅全局密码';
            return '需要访问密码';
        },
        currentDeviceText() {
            const device = this.$root.sync.statusInfo?.currentDevice;
            if (!device) return '当前网页设备尚未在服务端登记';
            return `${device.name} / ${device.online ? '在线' : '离线'} / ${device.trusted ? '已信任' : '待批准'}`;
        },
        cleanupText() {
            const cleanup = this.$root.sync.statusInfo?.cleanup;
            if (!cleanup) return '未知';
            return `间隔 ${cleanup.stateCleanup || 0}s，文本 ${cleanup.messageExpire || 0}s，通知 ${cleanup.payloadExpire || 0}s，待批准设备 ${cleanup.pendingDeviceExpire || 0}s，已信任设备 ${cleanup.trustedDeviceExpire || 0}s`;
        },
    },
    methods: {
        refreshSyncDevices() {
            this.$root.syncLoadDevices();
            this.$root.syncRefreshBootstrap();
            this.$root.syncLoadStatus();
        },
        iconFor(platform) {
            switch ((platform || '').toLowerCase()) {
                case 'windows':
                    return mdiMicrosoftWindows;
                case 'android':
                    return mdiAndroid;
                case 'linux':
                    return mdiLinux;
                case 'macos':
                case 'ios':
                    return mdiApple;
                case 'web':
                    return mdiDevices;
                default:
                    return mdiLaptop;
            }
        },
        platformLabel(item) {
            return (item.platform || 'unknown').toLowerCase() === 'web' ? '网页' : item.platform;
        },
        formatSyncTime(value) {
            if (!value) return '暂无';
            return new Date(value).toLocaleString();
        },
    },
    mounted() {
        this.refreshSyncDevices();
    },
}
</script>

<style scoped>
.sync-preview {
    white-space: pre-wrap;
    word-break: break-word;
}
</style>
