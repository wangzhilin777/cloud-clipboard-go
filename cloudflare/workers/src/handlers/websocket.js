import { corsHeaders } from '../cors';
import { ensureRoomAccess, normalizeRoomName } from '../auth';
import { debugLog } from '../logger';

export class WebSocketHandler {
  static async connect(request, env) {
    try {
      const url = new URL(request.url);
      const room = normalizeRoomName(url.searchParams.get('room'));
      
      debugLog(env, `WebSocket 连接请求: room=${room}, url=${url.toString()}`);
      const authResult = ensureRoomAccess(request, env, room);
      if (!authResult.ok) {
        debugLog(env, 'WebSocket 认证失败');
        return authResult.response;
      }
      debugLog(env, 'WebSocket 认证成功');

      // 检查是否为 WebSocket 升级请求
      const upgradeHeader = request.headers.get('Upgrade');
      if (!upgradeHeader || upgradeHeader.toLowerCase() !== 'websocket') {
        debugLog(env, '不是 WebSocket 升级请求');
        return new Response('Expected WebSocket', { 
          status: 400,
          headers: corsHeaders
        });
      }

      // 检查必要的 WebSocket 头部
      const connectionHeader = request.headers.get('Connection');
      const wsKeyHeader = request.headers.get('Sec-WebSocket-Key');
      const wsVersionHeader = request.headers.get('Sec-WebSocket-Version');
      
      if (!connectionHeader || !wsKeyHeader || !wsVersionHeader) {
        debugLog(env, '缺少必要的 WebSocket 头部');
        return new Response('Invalid WebSocket headers', {
          status: 400,
          headers: corsHeaders
        });
      }

      debugLog(env, '准备创建 Durable Object 连接');

      // 确保 WEBSOCKET_ROOM binding 存在
      if (!env.WEBSOCKET_ROOM) {
        console.error('WEBSOCKET_ROOM binding 不存在');
        return new Response('WebSocket service unavailable', {
          status: 503,
          headers: corsHeaders
        });
      }

      // 使用 Durable Objects 处理 WebSocket 连接
      const durableObjectId = env.WEBSOCKET_ROOM.idFromName(room);
      const durableObject = env.WEBSOCKET_ROOM.get(durableObjectId);
      
      debugLog(env, `转发到 Durable Object, room: ${room}`);
      
      // 转发请求到 Durable Object，保持原始头部和查询参数
      return await durableObject.fetch(request);

    } catch (error) {
      console.error('WebSocket handler error:', error);
      console.error('Error stack:', error.stack);
      
      return new Response(JSON.stringify({
        error: 'Internal Server Error',
        message: 'WebSocket 服务暂时不可用'
      }), { 
        status: 500,
        headers: {
          'Content-Type': 'application/json',
          ...corsHeaders
        }
      });
    }
  }
}
