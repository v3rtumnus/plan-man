import {http, ROOT_URL} from "./http/http";

export async function loginUser(username, password) {
    let response = await http.post(`${ROOT_URL}/auth`, JSON.stringify({username, password}));
    let data = await response.data;

    if (data.user) {
        localStorage.setItem('currentUser', JSON.stringify(data));
        return data
    }
}