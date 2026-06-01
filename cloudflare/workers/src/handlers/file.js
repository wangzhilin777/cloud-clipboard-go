import { corsHeaders } from '../cors';
import { buildSenderDevice, saveToD1, broadcastMessage, generateUUID } from '../utils';
import { ensureRoomAccess, normalizeRoomName } from '../auth';
import { debugLog } from '../logger';

function decodeUploadFilename(value = '') {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function toSafeAsciiFilename(filename = '') {
  const normalized = String(filename || '')
    .replace(/[\r\n]/g, ' ')
    .replace(/["\\]/g, '_')
    .replace(/[^\x20-\x7E]/g, '_')
    .trim();
  return normalized || 'file';
}

function buildContentDisposition(filename = '', disposition = 'inline') {
  const safeFallback = toSafeAsciiFilename(filename);
  const encodedFilename = encodeURIComponent(String(filename || safeFallback));
  return `${disposition}; filename="${safeFallback}"; filename*=UTF-8''${encodedFilename}`;
}

const MULTIPART_MIN_PART_SIZE = 5 * 1024 * 1024;
const DEFAULT_MULTIPART_PART_SIZE = 8 * 1024 * 1024;

function getFileLimit(env) {
  return env.FILE_LIMIT ? parseInt(env.FILE_LIMIT, 10) : 104857600;
}

function getMultipartPartSize(env) {
  const fileLimit = getFileLimit(env);
  if (!Number.isFinite(fileLimit) || fileLimit <= MULTIPART_MIN_PART_SIZE) {
    return fileLimit + 1;
  }
  return Math.min(fileLimit, DEFAULT_MULTIPART_PART_SIZE);
}

function createFileKey(uuid) {
  return `files/${uuid}`;
}

function buildContentURL(origin, messageId, room) {
  const roomQuery = room !== 'default' ? `?room=${encodeURIComponent(room)}` : '';
  return `${origin}/api/content/${messageId}${roomQuery}`;
}

function extractUuidFromKey(key = '') {
  return String(key || '').startsWith('files/') ? String(key).slice('files/'.length) : String(key || '');
}

function buildMultipartOptions({ room, fileName, fileType, expireTime }) {
  return {
    httpMetadata: {
      contentType: fileType || 'application/octet-stream',
      contentDisposition: buildContentDisposition(fileName, 'inline')
    },
    customMetadata: {
      originalName: fileName,
      uploadTime: Date.now().toString(),
      expireTime: expireTime.toString(),
      room,
    }
  };
}

async function finalizeUploadedFile({ request, env, url, room, uuid, fileName, fileSize, expireTime }) {
  const fileUrl = `${url.origin}/api/file/${uuid}/${encodeURIComponent(fileName)}`;
  const messageData = {
    type: 'file',
    name: fileName,
    size: fileSize,
    room,
    timestamp: Math.floor(Date.now() / 1000),
    senderIP: request.headers.get('CF-Connecting-IP') || 'unknown',
    userAgent: request.headers.get('User-Agent') || 'unknown',
    uuid,
    expireTime,
    url: fileUrl
  };
  const senderDevice = buildSenderDevice(messageData.userAgent);

  const saveResult = await saveToD1(env.DB, messageData, env);
  const messageId = saveResult.messageId;
  const filesToCleanup = saveResult.filesToCleanup;

  debugLog(env, '[upload] saved message', {
    room,
    uuid,
    messageId,
    cleanupCount: filesToCleanup.length,
  });

  if (filesToCleanup.length > 0 && env.R2_BUCKET) {
    debugLog(env, `清理 ${filesToCleanup.length} 个旧文件`);
    for (const fileUuid of filesToCleanup) {
      try {
        await env.R2_BUCKET.delete(createFileKey(fileUuid));
        debugLog(env, `已删除旧文件: ${fileUuid}`);
      } catch (deleteError) {
        console.error(`删除文件失败: ${fileUuid}`, deleteError);
      }
    }
  }

  await broadcastMessage(env, room, {
    event: 'receive',
    data: {
      ...messageData,
      id: messageId,
      expire: expireTime,
      cache: uuid,
      senderDevice,
    }
  });

  const contentURL = buildContentURL(url.origin, messageId, room);

  debugLog(env, '[upload] success', {
    room,
    uuid,
    messageId,
    contentURL,
  });

  const kind = FileHandler.determineFileType(fileName) === 'image' ? 'image' : 'file';
  const downloadURL = `${url.origin}/api/file/${uuid}/${encodeURIComponent(fileName)}`;
  const result = {
    id: messageId.toString(),
    type: FileHandler.determineFileType(fileName),
    kind,
    name: fileName,
    size: fileSize,
    uuid,
    url: contentURL,
    actionUrl: contentURL,
    downloadUrl: downloadURL,
  };

  return new Response(JSON.stringify({
    ...result,
    result,
  }), {
    headers: { 'Content-Type': 'application/json', ...corsHeaders }
  });
}

export class FileHandler {
  static async upload(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      if (!env.R2_BUCKET) {
        return new Response(JSON.stringify({
          error: 'Storage not available',
          message: '文件存储服务不可用'
        }), {
          status: 503,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const rawFileName = request.headers.get('X-File-Name');
      const isStreamUpload = Boolean(rawFileName);
      let file = null;
      let fileName = '';
      let fileSize = 0;
      let fileType = request.headers.get('Content-Type') || 'application/octet-stream';
      let fileBody = null;

      debugLog(env, '[upload] start', {
        room,
        hasAuthHeader: Boolean(request.headers.get('Authorization')),
        contentType: fileType,
        hasRawFilenameHeader: isStreamUpload,
      });

      if (isStreamUpload) {
        fileName = decodeUploadFilename(rawFileName) || 'file';
        fileSize = Number(request.headers.get('X-File-Size') || 0);
        fileBody = request.body;
      } else {
        const formData = await request.formData();
        file = formData.get('file');
        if (file) {
          fileName = file.name;
          fileSize = file.size;
          fileType = file.type || fileType;
          fileBody = file.stream();
        }
      }

      debugLog(env, '[upload] parsed', {
        room,
        isStreamUpload,
        fileName,
        fileSize,
        fileType,
        hasBody: Boolean(fileBody),
      });

      if (!fileBody || !fileName) {
        return new Response(JSON.stringify({
          error: 'No file provided',
          message: '未提供文件'
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      // 检查文件大小限制
      const fileLimit = getFileLimit(env);
      if (fileSize > fileLimit) {
        return new Response(JSON.stringify({
          error: 'File too large',
          message: `文件大小超出限制 (最大 ${Math.floor(fileLimit / 1024 / 1024)}MB)`
        }), {
          status: 413,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const uuid = generateUUID();
      const currentTime = Math.floor(Date.now() / 1000);
      const expireSeconds = env.FILE_EXPIRE ? parseInt(env.FILE_EXPIRE) : 3600;
      const expireTime = currentTime + expireSeconds;

      // 上传文件到 R2
      await env.R2_BUCKET.put(createFileKey(uuid), fileBody, buildMultipartOptions({ room, fileName, fileType, expireTime }));

      debugLog(env, '[upload] stored in r2', {
        room,
        uuid,
        fileName,
        fileSize,
      });

      return await finalizeUploadedFile({
        request,
        env,
        url,
        room,
        uuid,
        fileName,
        fileSize,
        expireTime,
      });

    } catch (error) {
      console.error('File upload error:', error);
      debugLog(env, 'File upload stack:', error?.stack || '(no stack)');
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '上传文件时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static async createMultipart(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      if (!env.R2_BUCKET) {
        return new Response(JSON.stringify({
          error: 'Storage not available',
          message: '文件存储服务不可用'
        }), {
          status: 503,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const body = await request.json();
      const fileName = String(body?.name || '').trim();
      const fileSize = Number(body?.size || 0);
      const fileType = String(body?.type || 'application/octet-stream');

      if (!fileName || !fileSize) {
        return new Response(JSON.stringify({
          error: 'Invalid file metadata',
          message: '文件元数据无效'
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const fileLimit = getFileLimit(env);
      if (fileSize > fileLimit) {
        return new Response(JSON.stringify({
          error: 'File too large',
          message: `文件大小超出限制 (最大 ${Math.floor(fileLimit / 1024 / 1024)}MB)`
        }), {
          status: 413,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const uuid = generateUUID();
      const key = createFileKey(uuid);
      const expireSeconds = env.FILE_EXPIRE ? parseInt(env.FILE_EXPIRE) : 3600;
      const expireTime = Math.floor(Date.now() / 1000) + expireSeconds;
      const upload = await env.R2_BUCKET.createMultipartUpload(key, buildMultipartOptions({ room, fileName, fileType, expireTime }));
      const partSize = getMultipartPartSize(env);

      debugLog(env, '[multipart] created', {
        room,
        uuid,
        key,
        uploadId: upload.uploadId,
        fileName,
        fileSize,
        partSize,
      });

      return new Response(JSON.stringify({
        result: {
          uuid,
          key,
          uploadId: upload.uploadId,
          partSize,
          minPartSize: MULTIPART_MIN_PART_SIZE,
        }
      }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    } catch (error) {
      console.error('Create multipart upload error:', error);
      debugLog(env, 'Create multipart upload stack:', error?.stack || '(no stack)');
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '初始化分片上传时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static async uploadMultipartPart(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      const uploadId = String(url.searchParams.get('uploadId') || '').trim();
      const key = String(url.searchParams.get('key') || '').trim();
      const partNumber = Number(request.params.partNumber);

      if (!uploadId || !key || !partNumber || !request.body) {
        return new Response(JSON.stringify({
          error: 'Invalid multipart request',
          message: '分片上传参数无效'
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const upload = env.R2_BUCKET.resumeMultipartUpload(key, uploadId);
      const part = await upload.uploadPart(partNumber, request.body);

      debugLog(env, '[multipart] uploaded part', {
        room,
        key,
        uploadId,
        partNumber,
      });

      return new Response(JSON.stringify({ result: part }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    } catch (error) {
      console.error('Multipart upload part error:', error);
      debugLog(env, 'Multipart upload part stack:', error?.stack || '(no stack)');
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '上传文件分片时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static async completeMultipart(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      const body = await request.json();
      const uploadId = String(body?.uploadId || '').trim();
      const key = String(body?.key || '').trim();
      const parts = Array.isArray(body?.parts) ? [...body.parts] : [];

      if (!uploadId || !key || !parts.length) {
        return new Response(JSON.stringify({
          error: 'Invalid multipart request',
          message: '分片完成参数无效'
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const upload = env.R2_BUCKET.resumeMultipartUpload(key, uploadId);
      const completedObject = await upload.complete(parts.sort((left, right) => left.partNumber - right.partNumber));
      const uuid = extractUuidFromKey(key);
      const fileName = completedObject.customMetadata?.originalName || body?.name || uuid || 'file';
      const expireTime = Number(completedObject.customMetadata?.expireTime || 0);
      const fileSize = Number(completedObject.size || body?.size || 0);

      debugLog(env, '[multipart] completed', {
        room,
        uuid,
        key,
        uploadId,
        fileName,
        fileSize,
      });

      return await finalizeUploadedFile({
        request,
        env,
        url,
        room,
        uuid,
        fileName,
        fileSize,
        expireTime,
      });
    } catch (error) {
      console.error('Complete multipart upload error:', error);
      debugLog(env, 'Complete multipart upload stack:', error?.stack || '(no stack)');
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '完成分片上传时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static async abortMultipart(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      const uploadId = String(url.searchParams.get('uploadId') || '').trim();
      const key = String(url.searchParams.get('key') || '').trim();
      if (!uploadId || !key) {
        return new Response(JSON.stringify({
          error: 'Invalid multipart request',
          message: '缺少 uploadId 或 key'
        }), {
          status: 400,
          headers: { 'Content-Type': 'application/json', ...corsHeaders }
        });
      }

      const upload = env.R2_BUCKET.resumeMultipartUpload(key, uploadId);
      await upload.abort();

      debugLog(env, '[multipart] aborted', {
        room,
        key,
        uploadId,
      });

      return new Response(JSON.stringify({
        status: '已取消分片上传'
      }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    } catch (error) {
      console.error('Abort multipart upload error:', error);
      debugLog(env, 'Abort multipart upload stack:', error?.stack || '(no stack)');
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '取消分片上传时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static async download(request, env) {
    try {
      const { uuid, filename } = request.params;
      debugLog(env, `文件下载请求: UUID ${uuid}, filename: ${filename}`);

      const object = env.R2_BUCKET ? await env.R2_BUCKET.get(`files/${uuid}`) : null;
      const room = normalizeRoomName(object?.customMetadata?.room || 'default');
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }
      
      if (!env.R2_BUCKET) {
        return new Response('Storage not available', { 
          status: 503,
          headers: corsHeaders 
        });
      }
      
      if (!object) {
        debugLog(env, `文件未找到: ${uuid}`);
        return new Response('File not found', { 
          status: 404,
          headers: corsHeaders 
        });
      }

      // 检查文件是否过期
      const expireTime = parseInt(object.customMetadata?.expireTime || '0');
      const currentTime = Math.floor(Date.now() / 1000);
      if (expireTime > 0 && currentTime > expireTime) {
        debugLog(env, `文件已过期: ${uuid}, expireTime: ${expireTime}, currentTime: ${currentTime}`);
        // 删除过期文件
        await env.R2_BUCKET.delete(`files/${uuid}`);
        return new Response('File expired', { 
          status: 404,
          headers: corsHeaders 
        });
      }

      debugLog(env, `文件下载成功: ${uuid}, size: ${object.size}, type: ${object.httpMetadata?.contentType}`);

      const headers = {
        'Content-Type': object.httpMetadata?.contentType || 'application/octet-stream',
        'Content-Length': object.size.toString(),
        'Cache-Control': 'public, max-age=3600',
        'Last-Modified': new Date(parseInt(object.customMetadata?.uploadTime || Date.now())).toUTCString(),
        ...corsHeaders
      };

      // 如果请求包含 download=true，设置为附件下载
      const url = new URL(request.url);
      const originalName = object.customMetadata?.originalName || filename || 'file';
      
      if (url.searchParams.get('download') === 'true') {
        headers['Content-Disposition'] = buildContentDisposition(originalName, 'attachment');
      } else {
        headers['Content-Disposition'] = buildContentDisposition(originalName, 'inline');
      }

      return new Response(object.body, { headers });

    } catch (error) {
      console.error('File download error:', error);
      debugLog(env, 'Error stack:', error.stack);
      return new Response('Internal Server Error', { 
        status: 500,
        headers: corsHeaders
      });
    }
  }

  static async delete(request, env) {
    try {
      let room = 'default';
      if (env.R2_BUCKET) {
        const object = await env.R2_BUCKET.head(`files/${request.params.uuid}`);
        if (object?.customMetadata?.room) {
          room = normalizeRoomName(object.customMetadata.room);
        }
      }

      if (room === 'default' && env.DB) {
        const fileRecord = await env.DB.prepare('SELECT room FROM messages WHERE uuid = ? ORDER BY id DESC LIMIT 1')
          .bind(request.params.uuid)
          .first();
        if (fileRecord?.room) {
          room = normalizeRoomName(fileRecord.room);
        }
      }

      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        return authResult.response;
      }

      const { uuid } = request.params;
      debugLog(env, `删除文件请求: UUID ${uuid}`);
      
      if (env.R2_BUCKET) {
        // 从 R2 删除文件
        await env.R2_BUCKET.delete(`files/${uuid}`);
        debugLog(env, `文件已从 R2 删除: ${uuid}`);
      }
      
      if (env.DB) {
        // 从 D1 删除相关记录
        await env.DB.prepare('DELETE FROM messages WHERE uuid = ?').bind(uuid).run();
        debugLog(env, `文件记录已从 D1 删除: ${uuid}`);
      }

      return new Response(JSON.stringify({
        status: '文件删除成功'
      }), {
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });

    } catch (error) {
      console.error('File delete error:', error);
      debugLog(env, 'Error stack:', error.stack);
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: '删除文件时发生错误'
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }
  }

  static determineFileType(filename) {
    if (!filename) return 'file';
    
    const ext = filename.split('.').pop()?.toLowerCase();
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'];
    const videoExts = ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'm4v'];
    const audioExts = ['mp3', 'wav', 'ogg', 'm4a', 'aac', 'flac'];
    
    if (imageExts.includes(ext)) return 'image';
    if (videoExts.includes(ext)) return 'video';
    if (audioExts.includes(ext)) return 'audio';
    return 'file';
  }

  // 添加获取文件类型图标的方法
  static getFileTypeIcon(filename) {
    const type = FileHandler.determineFileType(filename);
    const iconMap = {
      'image': '🖼️',
      'video': '🎬',
      'audio': '🎵',
      'file': '📄'
    };
    return iconMap[type] || '📄';
  }

  // 添加获取文件大小格式化的方法
  static formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }
}
