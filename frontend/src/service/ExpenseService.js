import {http, ROOT_URL} from "./http/http";

export async function retrieveExpenseCategories() {
    let response = await http.get(`${ROOT_URL}/expenses/categories`);
    return await response.data;
}

export async function retrieveOldestExpense() {
    let response = await http.get(`${ROOT_URL}/expenses/oldest`);
    return await response.data;
}

export async function retrieveExpenses(month, year) {
    let response = await http.get(`${ROOT_URL}/expenses/monthly?month=${month}&year=${year}`);
    return await response.data;
}

export async function retrieveExpenseSummaries(month, year) {
    let response = await http.get(`${ROOT_URL}/expenses/monthly/overview?month=${month}&year=${year}`);
    return await response.data;
}

export async function deleteExpense(id) {
    await http.delete(`${ROOT_URL}/expenses/${id}`);
}

export async function saveExpense(category, amount, comment, date) {
    await http.post(`${ROOT_URL}/expenses`, JSON.stringify({category, amount, comment, date}));
}