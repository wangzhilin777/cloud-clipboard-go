<template>
    <div class="send-panel" :class="{ 'send-panel--compact': compact }">
        <div v-if="!hideTitle" class="headline text--primary mb-4">{{ $t('sendFile') }}</div>
        <v-card
            outlined
            class="send-file-dropzone pa-3 mb-6 d-flex flex-row align-center"
            @dragenter="$event.preventDefault()"
            @dragover="$event.preventDefault()"
            @dragleave="$event.preventDefault()"
            @drop="$event.preventDefault(); handleSelectFiles(Array.from($event.dataTransfer.files))"
        >
            <template v-if="$root.send.files.length">
                <template v-if="progress">
                    <div class="flex-grow-1">
                        <small class="d-block text-right text--secondary">
                            {{Math.min(uploadedSize, fileSize) | prettyFileSize}} / {{fileSize | prettyFileSize}} ({{uploadProgress | percentage}})
                        </small>
                        <v-progress-linear :value="uploadProgress * 100"></v-progress-linear>
                    </div>
                </template>
                <template v-else>
                    <v-img
                        v-if="isUploadingImage"
                        :src="imagePreview"
                        class="mr-3 flex-grow-0"
                        width="2.5rem"
                        height="2.5rem"
                        style="border-radius: 3px"
                    ></v-img>
                    <div class="flex-grow-1 mr-2" style="min-width: 0">
                        <div
                            class="text-truncate"
                            :title="$root.send.files[0].name + ' ' + ($root.send.files.length > 1 ? `等 ${$root.send.files.length} 个文件` : '')"
                        >{{$root.send.files[0].name}} {{$root.send.files.length > 1 ? `等 ${$root.send.files.length} 个文件` : ''}}
                        </div>
                        <div class="caption">{{fileSize | prettyFileSize}}</div>
                    </div>
                    <div class="align-self-center">
                        <v-btn icon color="grey" @click="$root.send.files.splice(0)">
                            <v-icon>{{mdiClose}}</v-icon>
                        </v-btn>
                    </div>
                </template>
            </template>
            <template v-else>
                <v-btn
                    text
                    color="primary"
                    large
                    class="d-block mx-auto"
                    @click="focus"
                >
                    <div :title="$t('dragDropPasteTip')">
                        {{ $t('selectFileToSend') }}<span class="d-none d-xl-inline">{{ $t('dragDropPasteTip') }}</span>
                        <br>
                        <small class="text--secondary">{{ $t('fileSizeLimit', { limit: prettyFileSize($root.config.file.limit) }) }}</small>
                    </div>
                </v-btn>
                <input
                    ref="selectFile"
                    type="file"
                    class="d-none"
                    multiple
                    @change="handleSelectFiles(Array.from($event.target.files))"
                >
            </template>
        </v-card>
        <div class="text-right">
            <v-checkbox
                v-model="notifyAndroid"
                class="mb-2"
                color="primary"
                dense
                hide-details
                :disabled="!canNotifyAndroid || progress"
                label="上传后通知已信任安卓端确认接收"
            ></v-checkbox>
            <div v-if="notifyHint" class="caption text-left text--secondary mb-2">{{notifyHint}}</div>
            <v-btn
                depressed
                color="primary"
                :block="$vuetify.breakpoint.smAndDown"
                :disabled="isDisabled"
                @click="send"
            >{{ $t('send') }}</v-btn>
        </div>
    </div>
</template>

<script>
import {
    prettyFileSize, // Import the function
} from '@/util.js';
import {
    mdiClose,
} from '@mdi/js';

export default {
    name: 'send-file',
    props: {
        hideTitle: {
            type: Boolean,
            default: false,
        },
        compact: {
            type: Boolean,
            default: false,
        },
    },
    data() {
        return {
            progress: false,
            uploadedSizes: [],
            imagePreview: '',
            uploading: false,
            notifyAndroid: true,
            mdiClose,
        };
    },
    computed: {
        fileSize() {
            return this.$root.send.files.length ? this.$root.send.files.reduce((acc, cur) => acc += cur.size, 0) : 0;
        },
        uploadedSize() {
            return this.uploadedSizes.length ? this.uploadedSizes.reduce((acc, cur) => acc += cur, 0) : 0;
        },
        uploadProgress() {
            return Math.min(this.fileSize !== 0 ? (this.uploadedSize / this.fileSize) : 0, 1);
        },
        isDisabled() {
            return !this.$root.send.files.length || !this.$root.websocket || this.progress;
        },
        isUploadingImage() {
            return this.$root.send.files.length && this.$root.send.files[0].type.startsWith('image/');
        },
        canNotifyAndroid() {
            return !!(this.$root.sync?.device?.trusted && this.$root.sync?.deviceId);
        },
        notifyHint() {
            if (this.canNotifyAndroid) {
                return '仅向同房间、已信任且不是当前网页本机的同步客户端广播通知。';
            }
            if (this.$root.sync?.status === 'pending') {
                return '当前网页同步设备尚未获批，暂时不能发送接收通知。';
            }
            return '需要先连上同步协议并让当前网页设备处于已信任状态。';
        },
    },
    methods: {
        prettyFileSize, // Make it available if you prefer this.$options.methods.prettyFileSize(...)
        focus() {
            this.$refs.selectFile.click();
        },
        /**
         * @param {File[]} files
         */
        handleSelectFiles(files) {
            if (files.some(e => !e.size)) {
                this.$toast(this.$t('cannotSendEmptyFile')); // Translate toast
            } else if (files.some(e => e.size > this.$root.config.file.limit)) {
                this.$toast(this.$t('fileSizeExceeded', { limit: prettyFileSize(this.$root.config.file.limit) })); // Translate toast
            } else {
                this.$root.send.files.splice(0);
                this.$root.send.files.push(...files);
                if (this.isUploadingImage) {
                    URL.revokeObjectURL(this.imagePreview);
                    this.imagePreview = URL.createObjectURL(files[0]);
                }
            }
        },
        buildPayloadNotice(file, uploadResult) {
            const result = uploadResult || {};
            return {
                sourceDeviceId: this.$root.sync.deviceId,
                room: this.$root.room || '',
                kind: result.kind || (file.type.startsWith('image/') ? 'image' : 'file'),
                title: result.name || file.name,
                mime: file.type || 'application/octet-stream',
                size: result.size || file.size,
                actionUrl: result.actionUrl || result.url || null,
                downloadUrl: result.downloadUrl || null,
                createdAt: Date.now(),
            };
        },
        async broadcastPayloadNotice(uploadResults) {
            if (!(this.notifyAndroid && this.canNotifyAndroid)) return false;
            await Promise.all(uploadResults.map(result => this.$http.post('api/sync/payload-notice', result.notice)));
            return true;
        },
        async send() {
            try {
                const chunkSize = this.$root.config.file.chunk;
                this.uploadedSizes.splice(0);
                this.uploadedSizes.push(...Array(this.$root.send.files.length).fill(0));
                const files = [...this.$root.send.files];
                const uploadResults = await Promise.all(files.map(async (file, i) => {
                    if (file.size < chunkSize) {
                        const fd = new FormData;
                        fd.set('file', file);
                        this.progress = true;
                        const response = await this.$http.postForm('upload', fd, {
                            params: new URLSearchParams([['room', this.$root.room]]),
                            onUploadProgress: e => this.$set(this.uploadedSizes, i, e.loaded),
                        });
                        return {
                            file,
                            result: response.data.result,
                            notice: this.buildPayloadNotice(file, response.data.result),
                        };
                    } else {
                        const response = await this.$http.post('upload/chunk', file.name, {
                            headers: {'Content-Type': 'text/plain'},
                            params: new URLSearchParams([['room', this.$root.room]]),
                        });
                        const uuid = response.data.result.uuid;

                        let uploadedSize = 0;
                        this.progress = true;
                        while (uploadedSize < file.size) {
                            const chunk = file.slice(uploadedSize, uploadedSize + chunkSize);
                            await this.$http.post(`upload/chunk/${uuid}`, chunk, {
                                headers: {'Content-Type': 'application/octet-stream'},
                                onUploadProgress: e => this.$set(this.uploadedSizes, i, uploadedSize + e.loaded),
                            });
                            uploadedSize += chunkSize;
                        }
                        const finishResponse = await this.$http.post(`upload/finish/${uuid}`, null, {
                            params: new URLSearchParams([['room', this.$root.room]]),
                        });
                        return {
                            file,
                            result: finishResponse.data.result,
                            notice: this.buildPayloadNotice(file, finishResponse.data.result),
                        };
                    }
                }));
                let noticeSent = false;
                let noticeError = null;
                try {
                    noticeSent = await this.broadcastPayloadNotice(uploadResults);
                } catch (error) {
                    noticeError = error;
                }
                if (noticeSent) {
                    this.$toast('发送成功，已通知安卓端确认接收');
                } else if (noticeError) {
                    this.$toast(`发送成功，但通知安卓端失败：${noticeError.response?.data?.message || noticeError.message}`);
                } else {
                    this.$toast(this.$t('sendSuccess'));
                }
                this.$root.send.files.splice(0);
            } catch (error) {
                const backendMessage = error.response?.data?.msg || error.response?.data?.message;
                if (backendMessage) {
                    this.$toast(this.$t('sendFailedMsg', { msg: backendMessage })); // Translate toast
                } else {
                    this.$toast(this.$t('sendFailed')); // Translate toast
                }
            } finally {
                this.progress = false;
            }
        }
    },
    mounted() {
        document.onpaste = e => {
            if (!(e && e.clipboardData)) return;
            console.log(e.clipboardData);
            const items = Array.from(e.clipboardData.items);
            if (!(items.length && items.every(e => e.kind === 'file'))) return;
            this.handleSelectFiles(items.map(e => e.getAsFile()));
        };
    },
}
</script>

<style scoped>
.send-file-dropzone {
    border-radius: 20px;
    border-style: dashed !important;
    border-width: 1.5px !important;
    border-color: rgba(14, 165, 233, 0.35) !important;
    background: rgba(248, 250, 252, 0.75);
}

.theme--dark .send-file-dropzone {
    border-color: rgba(56, 189, 248, 0.45) !important;
    background: rgba(30, 41, 59, 0.84);
}

.send-panel--compact .send-file-dropzone {
    margin-bottom: 1rem !important;
}
</style>
