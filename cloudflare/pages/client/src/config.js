const isDevelopment = process.env.NODE_ENV === 'development';

const normalizeBaseURL = value => String(value || '').trim().replace(/\/+$/, '');

export const config = {
    apiBaseURL: normalizeBaseURL(process.env.VUE_APP_API_BASE_URL),
    wsBaseURL: normalizeBaseURL(process.env.VUE_APP_WS_BASE_URL || process.env.VUE_APP_API_BASE_URL),
    isCloudflarePages: /pages\.dev$/i.test(window.location.hostname),
    isDevelopment,
    name: 'Cloud Clipboard',
    version: '1.0.0',
};
