import {http, ROOT_URL} from "./http/http";

export async function retrieveCreditPlan() {
    let response = await http.get(`${ROOT_URL}/credit/plan`);
    return await response.data;
}

export async function retrieveCreditOverview() {
    let response = await http.get(`${ROOT_URL}/credit/overview`);
    return await response.data;
}