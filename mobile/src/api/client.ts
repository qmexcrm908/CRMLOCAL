import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';
import Constants from 'expo-constants';
import { API_BASE_URL, STORAGE_KEYS, normalizeApiUrl } from '../config/constants';

let currentBaseUrl = API_BASE_URL;
let activeAuthToken: string | null = null;
let logoutCallback: (() => void) | null = null;

export const setApiAuthToken = (token: string | null) => {
  activeAuthToken = token;
};

export const setApiBaseUrl = (url: string) => {
  const normalized = normalizeApiUrl(url);
  currentBaseUrl = normalized;
  apiClient.defaults.baseURL = normalized;
};

export const setLogoutHandler = (callback: () => void) => {
  logoutCallback = callback;
};

export const getApiBaseUrl = () => currentBaseUrl;

export const apiClient: AxiosInstance = axios.create({
  baseURL: currentBaseUrl,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Load stored custom URL on startup
AsyncStorage.getItem(STORAGE_KEYS.CUSTOM_API_URL).then((storedUrl) => {
  if (storedUrl && storedUrl.trim() !== '') {
    let cleanUrl = normalizeApiUrl(storedUrl.trim());

    // On native mobile devices, if stored URL uses localhost, dynamically convert it to host IP
    if (Platform.OS !== 'web' && (cleanUrl.includes('localhost') || cleanUrl.includes('127.0.0.1'))) {
      const hostUri = Constants.expoConfig?.hostUri || (Constants as any)?.manifest?.debuggerHost;
      if (hostUri) {
        const hostIp = hostUri.split(':')[0];
        if (hostIp && hostIp !== 'localhost' && hostIp !== '127.0.0.1') {
          cleanUrl = cleanUrl.replace(/localhost|127\.0\.0\.1/g, hostIp);
        }
      }
    }

    // On web when accessing localhost, don't let a stale remote IP override localhost
    if (
      typeof window !== 'undefined' &&
      (window.location?.hostname === 'localhost' || window.location?.hostname === '127.0.0.1') &&
      !cleanUrl.includes('localhost') &&
      !cleanUrl.includes('127.0.0.1')
    ) {
      return;
    }
    setApiBaseUrl(cleanUrl);

    // Self-heal storage if it had corrupted path suffixes
    if (cleanUrl !== storedUrl) {
      AsyncStorage.setItem(STORAGE_KEYS.CUSTOM_API_URL, cleanUrl).catch(() => {});
    }
  }
});

// Request interceptor: Attach token & safe debugging
apiClient.interceptors.request.use(
  async (config: InternalAxiosRequestConfig) => {
    let token = activeAuthToken;
    if (!token) {
      try {
        token = await AsyncStorage.getItem(STORAGE_KEYS.AUTH_TOKEN);
        if (token) {
          activeAuthToken = token;
        }
      } catch (err) {
        console.warn('[API Client] Error reading stored token from AsyncStorage:', err);
      }
    }

    const tokenExists = !!token;
    let authHeaderAttached = false;

    if (token) {
      const bearerValue = `Bearer ${token}`;
      if (config.headers && typeof (config.headers as any).set === 'function') {
        (config.headers as any).set('Authorization', bearerValue);
        authHeaderAttached = true;
      } else {
        config.headers = config.headers || ({} as any);
        config.headers['Authorization'] = bearerValue;
        config.headers.Authorization = bearerValue;
        authHeaderAttached = true;
      }
    }

    // Safe debugging log (NEVER prints complete token, only existence and masked preview)
    const tokenPreview = token
      ? `${token.substring(0, 8)}...${token.substring(token.length - 6)}`
      : 'NONE';
    const fullUrl = `${config.baseURL || ''}${config.url || ''}`;
    console.log(
      `[API Request] ${config.method?.toUpperCase()} ${fullUrl} | Token Exists: ${tokenExists} (${tokenPreview}) | Auth Header Attached: ${authHeaderAttached}`
    );

    return config;
  },
  (error) => {
    console.error('[API Request Error]', error?.message);
    return Promise.reject(error);
  }
);

// Response interceptor: Handle 401 unauthorized, log responses, & extract backend messages
apiClient.interceptors.response.use(
  (response) => {
    const fullUrl = `${response.config.baseURL || ''}${response.config.url || ''}`;
    console.log(
      `[API Response] Status: ${response.status} | URL: ${fullUrl}`
    );
    return response;
  },
  async (error) => {
    const status = error.response?.status;
    const url = `${error.config?.baseURL || ''}${error.config?.url || ''}`;
    const rawMessage =
      error.response?.data?.message ||
      error.response?.data?.error ||
      error.message ||
      'An unexpected network error occurred. Please check your connection.';

    console.warn(
      `[API Error] Status: ${status || 'NETWORK_FAILURE'} | URL: ${url} | Message: ${rawMessage}`
    );

    if (status === 401 && !error.config?.url?.includes('/auth/login')) {
      if (logoutCallback) {
        logoutCallback();
      }
    }

    return Promise.reject(new Error(rawMessage));
  }
);
