import { Platform } from 'react-native';
import Constants from 'expo-constants';

/**
 * Resolves the backend API base URL strictly from environment configuration (.env).
 *
 * Supports:
 * 1. Web browser intelligent host detection (if accessed via localhost, uses localhost:8080)
 * 2. process.env.EXPO_PUBLIC_API_BASE_URL (Metro / Expo standard inlining from .env)
 * 3. process.env.API_BASE_URL (standard environment variable from .env)
 * 4. Constants.expoConfig?.extra?.apiBaseUrl (dynamic app.config.js injection)
 * 5. (Constants.manifest as any)?.extra?.apiBaseUrl (Expo manifest fallback)
 */
const DEFAULT_PROD_URL = 'https://nationally-nano-graduate-subjects.trycloudflare.com';

const getRawEnvBaseUrl = (): string => {
  // 1. Explicit environment variable (.env) takes highest priority
  let envUrl =
    process.env.EXPO_PUBLIC_API_BASE_URL ||
    process.env.API_BASE_URL ||
    Constants.expoConfig?.extra?.apiBaseUrl ||
    (Constants as any)?.manifest2?.extra?.expoClient?.extra?.apiBaseUrl ||
    (Constants.manifest as any)?.extra?.apiBaseUrl;

  if (envUrl && typeof envUrl === 'string' && envUrl.trim() !== '') {
    envUrl = envUrl.trim();
  } else if (Platform.OS === 'web' && typeof window !== 'undefined' && window.location) {
    const { hostname } = window.location;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      envUrl = 'http://localhost:8080';
    } else {
      envUrl = DEFAULT_PROD_URL;
    }
  } else {
    envUrl = DEFAULT_PROD_URL;
  }

  // 2. On physical mobile devices (Android/iOS), 'localhost' points to the device itself.
  // Automatically replace 'localhost' or '127.0.0.1' with Metro server host IP address if available.
  if (Platform.OS !== 'web' && (envUrl.includes('localhost') || envUrl.includes('127.0.0.1'))) {
    const hostUri =
      Constants.expoConfig?.hostUri ||
      (Constants as any)?.manifest2?.extra?.expoGo?.developer?.tool ||
      (Constants as any)?.manifest?.debuggerHost ||
      (Constants as any)?.developer?.tool;

    if (hostUri) {
      const hostIp = hostUri.split(':')[0];
      if (hostIp && hostIp !== 'localhost' && hostIp !== '127.0.0.1') {
        envUrl = envUrl.replace(/localhost|127\.0\.0\.1/g, hostIp);
      }
    }
  }

  return envUrl;
};

// Normalize base URL to ensure clean /api/v1 prefix without trailing endpoint artifacts
export const normalizeApiUrl = (url: string): string => {
  if (!url) return '';
  let trimmed = url.trim().replace(/\/+$/, '');
  // Strip common mistaken endpoint suffixes
  trimmed = trimmed.replace(/\/api\/v1\/health\/?$/i, '');
  trimmed = trimmed.replace(/\/health\/?$/i, '');
  trimmed = trimmed.replace(/\/auth\/login\/?$/i, '');
  trimmed = trimmed.replace(/\/login\/?$/i, '');
  trimmed = trimmed.replace(/\/api\/v1\/?$/i, '');
  trimmed = trimmed.replace(/\/+$/, '');
  return `${trimmed}/api/v1`;
};

export const API_BASE_URL = normalizeApiUrl(getRawEnvBaseUrl());

export const STORAGE_KEYS = {
  AUTH_TOKEN: 'crm_mobile_token',
  USER_DATA: 'crm_mobile_user',
  ATTENDANCE_DATA: 'crm_mobile_attendance',
  CUSTOM_API_URL: 'crm_custom_api_url',
};
