import axios from "axios";

export const ROOT_URL = '/api';

export const http = axios.create({
    withCredentials: true,
    timeout: 60000,
    params: {}
});

// Add a request interceptor
http.interceptors.request.use(
    async (config) => {
        const token = JSON.parse(localStorage.getItem("currentUser"))?.auth_token;
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }

        config.headers.post['Content-Type'] = 'application/json';

        return config;
    },
    (error) => {
        Promise.reject(error);
    }
);
